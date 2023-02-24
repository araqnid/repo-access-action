package github

import js.core.get
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import node.process.process

interface GithubBackend {
    suspend fun requestForText(method: String, path: String, body: String?): TextResponse
    suspend fun dispose()

    data class TextResponse(val status: Int, val contentType: String?, val text: String)
}

@Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
class GithubException(val path: String, val statusCode: Int, val error: Github.Error) : RuntimeException(
    if (error.documentationUrl != null)
        "$path: [HTTP $statusCode] ${error.message} (${error.documentationUrl})"
    else
        "$path: [HTTP $statusCode] ${error.message}"
)

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Github(private val backend: GithubBackend, private val defaultPageSize: Int = 30) {
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
    }

    private fun verifySuccessful(path: String, response: GithubBackend.TextResponse, accept204: Boolean = false) {
        when {
            response.status == 200 -> {
            }
            response.status == 204 && accept204 -> {
            }
            response.contentType != null && response.contentType.lowercase().startsWith("application/json") -> {
                throw GithubException(path, response.status, jsonFormat.decodeFromString(response.text))
            }
            else -> {
                error("$path: HTTP error ${response.status}")
            }
        }
    }

    private suspend fun <T> fetch(deserializer: DeserializationStrategy<T>, path: String): T {
        val response = backend.requestForText("GET", path, null)
        verifySuccessful(path, response)
        return jsonFormat.decodeFromString(deserializer, response.text)
    }

    private suspend fun delete(path: String) {
        val response = backend.requestForText("DELETE", path, null)
        verifySuccessful(path, response, accept204 = true)
    }

    private suspend fun <T> put(path: String, body: T, serializer: SerializationStrategy<T>) {
        val response = backend.requestForText("PUT", path, jsonFormat.encodeToString(serializer, body))
        verifySuccessful(path, response, accept204 = true)
    }

    private suspend inline fun <reified T> put(path: String, body: T)
        = put(path, body, serializer())

    private fun <T> fetchPages(
        deserializer: DeserializationStrategy<out T>,
        path: String,
        pageSize: Int
    ): Flow<T> = flow {
        var page = 1
        while (true) {
            val result = try {
                fetch(deserializer, "$path${if (path.contains('?')) '&' else '?'}page=$page&per_page=$pageSize")
            } catch (ex: GithubException) {
                if (ex.statusCode == 404 && page == 1) return@flow // translate 404 to empty flow
                throw ex
            }
            emit(result)
            ++page
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> fetchCollection(
        deserializer: DeserializationStrategy<out Collection<T>>,
        path: String,
        pageSize: Int
    ): Flow<T> = fetchPages(deserializer, path, pageSize).transformWhile { page ->
        emitAll(page.asFlow())
        page.size == pageSize
    }

    private fun <T> fetchCollectionViaListWithCount(
        deserializer: DeserializationStrategy<out ListWithCount<T>>,
        path: String,
        pageSize: Int,
    ): Flow<T> = fetchPages(deserializer, path, pageSize).let { upstream ->
        flow {
            var finished = false
            var seenItems = 0
            // This works, but I feel bad about assuming that each item sequentially calls takeWhile and collect
            upstream.takeWhile { !finished }.collect { page ->
                if (page.items.isNotEmpty()) emitAll(page.items.asFlow())
                seenItems += page.items.size
                finished = page.items.isEmpty() || seenItems >= page.totalCount
            }
        }
    }

    suspend fun getSelf(): User =
        fetch(serializer(), "/user")

    suspend fun getOrganization(orgName: String): Organization =
        fetch(serializer(), "/orgs/$orgName")

    suspend fun getRepo(ownerName: String, repoName: String): Repository =
        fetch(serializer(), "/repos/$ownerName/$repoName")

    fun getOrgRepos(orgName: String, pageSize: Int = defaultPageSize): Flow<Repository> =
        fetchCollection(serializer(), "/orgs/$orgName/repos", pageSize)

    fun getOrgRepos(org: Organization, pageSize: Int = defaultPageSize): Flow<Repository> =
        getOrgRepos(org.name, pageSize)

    fun getOrgTeams(orgName: String, pageSize: Int = defaultPageSize): Flow<Team> =
        fetchCollection(serializer(), "/orgs/$orgName/teams", pageSize)

    fun getOrgTeams(org: Organization, pageSize: Int = defaultPageSize): Flow<Team> =
        getOrgTeams(org.name, pageSize)

    fun getOrgTeamRepos(orgName: String, teamSlug: String, pageSize: Int = defaultPageSize): Flow<Repository> =
        fetchCollection(serializer(), "/orgs/$orgName/teams/$teamSlug/repos", pageSize)

    fun getOrgTeamRepos(org: Organization, team: Team, pageSize: Int = defaultPageSize): Flow<Repository> =
        getOrgTeamRepos(org.name, team.slug, pageSize)

    fun getRepoTeams(ownerName: String, repoName: String, pageSize: Int = defaultPageSize): Flow<Team> =
        fetchCollection(serializer(), "/repos/$ownerName/$repoName/teams", pageSize)

    fun getRepoTeams(repo: Repository, pageSize: Int = defaultPageSize): Flow<Team> =
        getRepoTeams(repo.owner.login, repo.name, pageSize)

    suspend fun deleteRepoTeamPermission(orgName: String, teamSlug: String, ownerName: String, repoName: String) {
        delete("/orgs/$orgName/teams/$teamSlug/repos/$ownerName/$repoName")
    }

    suspend fun deleteRepoTeamPermission(orgName: String, teamSlug: String, repo: Repository) =
        deleteRepoTeamPermission(orgName, teamSlug, repo.owner.login, repo.name)

    suspend fun deleteRepoTeamPermission(orgName: String, team: Team, repo: Repository) =
        deleteRepoTeamPermission(orgName, team.slug, repo)

    suspend fun deleteRepoTeamPermission(org: Organization, team: Team, repo: Repository) =
        deleteRepoTeamPermission(org.name, team.slug, repo)

    suspend fun updateRepoTeamPermission(orgName: String, teamSlug: String, ownerName: String, repoName: String, permission: String) {
        put("/orgs/$orgName/teams/$teamSlug/repos/$ownerName/$repoName", RepoTeamPutPermissionBody(permission))
    }

    suspend fun updateRepoTeamPermission(orgName: String, teamSlug: String, repo: Repository, permission: String) =
        updateRepoTeamPermission(orgName, teamSlug, repo.owner.login, repo.name, permission)

    suspend fun updateRepoTeamPermission(orgName: String, team: Team, repo: Repository, permission: String) =
        updateRepoTeamPermission(orgName, team.slug, repo, permission)

    suspend fun updateRepoTeamPermission(org: Organization, team: Team, repo: Repository, permission: String) =
        updateRepoTeamPermission(org.name, team, repo, permission)

    fun getRepoArtifacts(ownerName: String, repoName: String, pageSize: Int = defaultPageSize): Flow<Artifact> =
        fetchCollectionViaListWithCount(
            serializer<ArtifactList>(),
            "/repos/$ownerName/$repoName/actions/artifacts",
            pageSize
        )

    fun getRepoArtifacts(repo: Repository, pageSize: Int = defaultPageSize): Flow<Artifact> =
        getRepoArtifacts(repo.owner.login, repo.name, pageSize)

    suspend fun deleteRepoArtifact(ownerName: String, repoName: String, artifactId: Int) {
        delete("/repos/$ownerName/$repoName/actions/artifacts/$artifactId")
    }

    suspend fun deleteRepoArtifact(ownerName: String, repoName: String, artifact: Artifact) =
        deleteRepoArtifact(ownerName, repoName, artifact.id)

    suspend fun deleteRepoArtifact(repo: Repository, artifactId: Int) =
        deleteRepoArtifact(repo.owner.login, repo.name, artifactId)

    suspend fun deleteRepoArtifact(repo: Repository, artifact: Artifact) =
        deleteRepoArtifact(repo.owner.login, repo.name, artifact.id)

    suspend fun getRepoEnvironments(ownerName: String, repoName: String): Environments =
        fetch(serializer(), "/repos/$ownerName/$repoName/environments")

    suspend fun getRepoEnvironments(repo: Repository): Environments =
        getRepoEnvironments(repo.owner.login, repo.name)

    fun getRepoBranches(
        ownerName: String,
        repoName: String,
        protected: Boolean = false,
        pageSize: Int = defaultPageSize
    ): Flow<Branch> =
        fetchCollection(serializer(), "/repos/$ownerName/$repoName/branches?protected=$protected", pageSize)

    fun getRepoBranches(repo: Repository, protected: Boolean = false, pageSize: Int = defaultPageSize): Flow<Branch> =
        getRepoBranches(repo.owner.login, repo.name, protected, pageSize)

    suspend fun getRepoBranch(ownerName: String, repoName: String, branchName: String): Branch =
        fetch(serializer(), "/repos/$ownerName/$repoName/branches/$branchName")

    suspend fun getRepoBranch(repo: Repository, branchName: String): Branch =
        getRepoBranch(repo.owner.login, repo.name, branchName)

    suspend fun getRepoBranchProtection(ownerName: String, repoName: String, branchName: String): BranchProtection =
        fetch(serializer(), "/repos/$ownerName/$repoName/branches/$branchName/protection")

    suspend fun getRepoBranchProtection(repo: Repository, branchName: String): BranchProtection =
        getRepoBranchProtection(repo.owner.login, repo.name, branchName)

    @Serializable
    data class User(val id: Int, val login: String, val name: String, val email: String)

    @Serializable
    data class Organization(val id: Int, val name: String)

    @Serializable
    data class Team(val id: Int, val name: String, val slug: String, val permissions: Map<String, Boolean>? = null)

    @Serializable
    data class Artifact(
        val id: Int,
        val name: String,
        @SerialName("size_in_bytes") val sizeInBytes: Long,
        @SerialName("expired") val isExpired: Boolean,
        @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    )

    private interface ListWithCount<out E> {
        val totalCount: Int
        val items: List<E>
    }

    @Serializable
    private data class ArtifactList(
        @SerialName("total_count") override val totalCount: Int,
        @SerialName("artifacts") override val items: List<Artifact>
    ) : ListWithCount<Artifact>

    @Serializable
    data class Repository(
        val id: Int,
        val name: String,
        @SerialName("private")
        val isPrivate: Boolean,
        @SerialName("archived")
        val isArchived: Boolean,
        val visibility: String,
        val topics: Set<String>,
        val url: String,
        val permissions: Permissions,
        val owner: Owner,
        @SerialName("default_branch")
        val defaultBranch: String
    ) {
        @Serializable
        data class Permissions(val admin: Boolean, val push: Boolean, val pull: Boolean)

        @Serializable
        data class Owner(val id: Int, val login: String, val type: String)
    }

    @Serializable
    data class Environment(
        val id: Int,
        val name: String,
        @SerialName("protection_rules") val protectionRules: List<ProtectionRule>,
        @SerialName("deployment_branch_policy") val deploymentBranchPolicy: DeploymentBranchPolicy? = null
    ) {
        @Serializable
        data class ProtectionRule(val id: Int, val type: String)

        @Serializable
        data class DeploymentBranchPolicy(@SerialName("protected_branches") val protectedBranches: Boolean)
    }

    @Serializable
    data class Environments(@SerialName("total_count") val totalCount: Int, val environments: List<Environment>)

    @Serializable
    data class Branch(
        val name: String,
        @SerialName("protected") val isProtected: Boolean
    ) {
        @Serializable
        data class BinaryFeature(@SerialName("enabled") val isEnabled: Boolean)
    }

    @Serializable
    data class BranchProtection(
        @SerialName("required_linear_history")
        val requiredLinearHistory: BinaryFeature? = null,
        @SerialName("allow_force_pushes")
        val allowForcePushes: BinaryFeature? = null,
        @SerialName("allow_deletions")
        val allowDeletions: BinaryFeature? = null
    ) {
        @Serializable
        data class BinaryFeature(@SerialName("enabled") val isEnabled: Boolean)
    }

    @Serializable
    data class Error(val message: String, @SerialName("documentation_url") val documentationUrl: String? = null)

    @Serializable
    private data class RepoTeamPutPermissionBody(val permission: String)
}

class InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(serialName = "instant", kind = PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        return parseISOInstant(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        return encoder.encodeString(value.toISOString())
    }
}

private val defaultGithubToken: String?
    get() = process.env["GITHUB_TOKEN"].unsafeCast<String?>()

/**
 * Use a GitHub client, providing it to the given block and disposing of it after the block is complete.
 *
 * If no [githubToken] is supplied, the environment variable `GITHUB_TOKEN` will be used.
 */
suspend fun <T> useGithub(
    githubToken: String = defaultGithubToken ?: error("No GITHUB_TOKEN in environment"),
    block: suspend (Github) -> T
): T {
    val backend = createGithubBackend(githubToken)
    return try {
        block(Github(backend))
    } finally {
        withContext(NonCancellable) {
            backend.dispose()
        }
    }
}
