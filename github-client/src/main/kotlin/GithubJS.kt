package github

import js.core.get
import js.core.jso
import js.core.set
import kotlinx.coroutines.suspendCancellableCoroutine
import node.buffer.Buffer
import node.buffer.BufferEncoding
import node.events.Event
import node.http.AgentOptions
import node.http.ClientRequest
import node.http.IncomingMessage
import node.http.RequestOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Date

fun createGithubBackend(githubToken: String): GithubBackend = GithubJS(githubToken)

internal typealias Instant = Date

internal fun parseISOInstant(input: String): Instant = Date(input)

@JsModule("node:https")
private external object Https {
    open class Agent(options: AgentOptions = definedExternally) {
        fun destroy()
    }

    fun request(uri: String, requestOptions: RequestOptions, callback: (IncomingMessage) -> Unit): ClientRequest
}

private class GithubJS(private val token: String) : GithubBackend {
    private val agent = Https.Agent(jso {
        keepAlive = true
    })

    private val authorizationHeader by lazy {
        val base64input = ":$token"
        val base64output = Buffer.from(base64input).toString(BufferEncoding.base64)
        "Basic $base64output"
    }

    override suspend fun requestForText(method: String, path: String, body: String?): GithubBackend.TextResponse {
        require(path.startsWith("/")) { "path should start with '/': $path" }
        val requestOptions = jso<RequestOptions> {
            headers = jso {
                this["User-Agent"] = "github-repo-access/0.0"
                this["Accept"] = "application/vnd.github.v3+json"
                this["Authorization"] = authorizationHeader
            }
            this.method = method
        }
        val res = suspendCancellableCoroutine<IncomingMessage> { cont ->
            val req = Https.request("https://api.github.com$path", requestOptions, cont::resume)
            req.on(Event.ERROR) { ex ->
                cont.resumeWithException(ex.unsafeCast<Throwable>())
            }
            cont.invokeOnCancellation { ex ->
                req.destroy(ex.unsafeCast<Error>())
            }
            if (body != null) {
                req.end(body, BufferEncoding.utf8)
            } else {
                req.end()
            }
        }
        val text = suspendCancellableCoroutine<String> { cont ->
            val chunks = mutableListOf<Buffer>()
            res.on(Event.DATA) { chunk ->
                chunks += chunk.unsafeCast<Buffer>()
            }
            res.on(Event.ERROR) { err ->
                cont.resumeWithException(err.unsafeCast<Throwable>())
            }
            res.on(Event.END) {
                cont.resume(
                    when (chunks.size) {
                        0 -> ""
                        1 -> chunks[0].toString(BufferEncoding.utf8)
                        else -> Buffer.concat(chunks.toTypedArray()).toString(BufferEncoding.utf8)
                    }
                )
            }
            cont.invokeOnCancellation { ex ->
                res.destroy(ex.unsafeCast<Error>())
            }
        }
        return GithubBackend.TextResponse(
            status = res.statusCode!!.toInt(),
            contentType = res.headers["content-type"]?.toString(),
            text = text
        )
    }

    override suspend fun dispose() {
        agent.destroy()
    }
}
