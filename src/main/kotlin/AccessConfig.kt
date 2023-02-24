package action

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import node.buffer.BufferEncoding

@Serializable
data class RepositoryGroup(
    val description: String,
    val teams: Map<String, String>,
    val repos: Set<String>
)

private val jsonFormat = Json {
    ignoreUnknownKeys = true
}

typealias AccessConfig = List<RepositoryGroup>

suspend fun readAccessConfigFile(filename: String): AccessConfig {
    return jsonFormat.decodeFromString(node.fs.readFile(filename, BufferEncoding.utf8))
}

enum class AccessType {
    ADMIN, MAINTAIN, TRIAGE, PUSH, PULL;

    val githubName: String
        get() = name.lowercase()
}

data class RepoAccessConfig(val teams: Map<String, AccessType>)

private val accessTypeNames = AccessType.values().associateBy { it.githubName }

fun String.toAccessType() = accessTypeNames[this] ?: throw IllegalArgumentException("Unknown access type: $this")

fun invertAccessConfig(accessConfig: AccessConfig): Map<String, RepoAccessConfig> {
    return buildMap {
        val seenRepos = mutableSetOf<String>()

        for (level in accessConfig) {
            for (repo in level.repos) {
                if (!seenRepos.add(repo)) {
                    error("Repo \"$repo\" listed twice")
                }

                put(repo, RepoAccessConfig(teams = level.teams.mapValues { (team, accessTypeString) ->
                    accessTypeNames[accessTypeString]
                        ?: error("Unrecognised access type \"$accessTypeString\" for \"$team\" in \"${level.description}\"")
                }))
            }
        }
    }
}
