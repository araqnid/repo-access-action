package github

import actions.http.client.BearerCredentialHandler
import js.core.get
import js.core.jso
import kotlinx.coroutines.await
import kotlin.js.Date

fun createGithubBackend(githubToken: String): GithubBackend = GithubActionsHttpClient(githubToken)

internal typealias Instant = Date

internal fun parseISOInstant(input: String): Instant = Date(input)

private class GithubActionsHttpClient(token: String) : GithubBackend {
    private val client = actions.http.client.HttpClient(
        userAgent = "github-repo-access/0.0",
        handlers = arrayOf(
            BearerCredentialHandler(token)
        ),
        requestOptions = jso {
            keepAlive = true
        }
    )

    override suspend fun requestForText(method: String, path: String, body: String?): GithubBackend.TextResponse {
        require(path.startsWith("/")) { "path should start with '/': $path" }
        val response = client.request(method, "https://api.github.com$path", body).await()
        return GithubBackend.TextResponse(
            status = response.message.statusCode!!.toInt(),
            contentType = response.message.headers["content-type"]?.toString(),
            text = response.readBody().await()
        )
    }

    override suspend fun dispose() {
        client.dispose()
    }
}
