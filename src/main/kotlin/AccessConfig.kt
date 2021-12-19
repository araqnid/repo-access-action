package action

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Serializable
data class RepositoryGroup(
    val description: String,
    val teams: Map<String, String>,
    val repos: Set<String>
)

private val jsonFormat = Json {
    ignoreUnknownKeys = true
}

@JsModule("fs")
private external object Filesystem {
    fun readFile(filename: String, encoding: String, callback: (Throwable?, String?) -> Unit)
}

typealias AccessConfig = List<RepositoryGroup>

suspend fun readAccessConfigFile(filename: String): AccessConfig {
    val fileText = suspendCoroutine<String> { cont ->
        Filesystem.readFile(filename, "utf-8") { ex, data ->
            if (ex != null)
                cont.resumeWithException(ex)
            else
                cont.resume(data.unsafeCast<String>())
        }
    }
    return jsonFormat.decodeFromString(fileText)
}

enum class AccessType {
    ADMIN, PUSH, PULL
}

data class RepoAccessConfig(val teams: Map<String, AccessType>)

private val accessTypeNames = mapOf(
    "admin" to AccessType.ADMIN,
    "push" to AccessType.PUSH,
    "pull" to AccessType.PULL
)

fun String.toAccessType() = accessTypeNames[this] ?: throw IllegalArgumentException("Unknown access type: $this")

fun invertAccessConfig(accessConfig: AccessConfig): Map<String, RepoAccessConfig> {
    return buildMap {
        val seenRepos = mutableSetOf<String>()

        for (level in accessConfig) {
            for (repo in level.repos) {
                if (!seenRepos.add(repo)) {
                    kotlin.error("Repo \"$repo\" listed twice")
                }

                put(repo, RepoAccessConfig(teams = level.teams.mapValues { (team, accessTypeString) ->
                    accessTypeNames[accessTypeString]
                        ?: kotlin.error("Unrecognised access type \"$accessTypeString\" for \"$team\" in \"${level.description}\"")
                }))
            }
        }
    }
}
