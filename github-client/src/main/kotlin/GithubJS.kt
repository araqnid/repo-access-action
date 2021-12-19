package github

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Date

fun createGithubBackend(githubToken: String): GithubBackend = GithubJS(githubToken)

internal typealias Instant = Date

internal fun parseISOInstant(input: String): Instant = Date(input)

private external class Buffer {
    companion object {
        fun from(input: String): Buffer
        fun concat(inputs: Array<Buffer>): Buffer
    }

    fun toString(encoding: String): String
}

private external interface ClientRequest : OutgoingMessage

private external interface IncomingHttpHeaders : Dict<dynamic>

private external interface Dict<T>

private inline operator fun <T> Dict<T>.get(key: String): T? = asDynamic()[key].unsafeCast<T?>()
private inline operator fun <T> Dict<T>.set(key: String, value: T) {
    asDynamic()[key] = value
}

private external interface IncomingMessage : Readable {
    val statusCode: Number
    val headers: IncomingHttpHeaders
    fun destroy(error: Throwable = definedExternally)
}

private external interface OutgoingMessage : Writable

private external interface Readable {
    fun on(event: String, cb: (Any?) -> Unit)
}

@Suppress("unused")
private external interface Writable {
    fun on(event: String, cb: (Any?) -> Unit)
    fun end(cb: () -> Unit = definedExternally)
    fun end(chunk: Buffer, cb: () -> Unit = definedExternally)
    fun end(chunk: String, encoding: String, cb: () -> Unit = definedExternally)
    fun destroy(error: Throwable = definedExternally)
}

@JsModule("https")
private external object Https {
    interface AgentOptions : Http.AgentOptions

    open class Agent(options: AgentOptions = definedExternally) : Http.Agent

    fun request(uri: String, requestOptions: Http.RequestOptions, callback: (IncomingMessage) -> Unit): ClientRequest
}

@JsModule("http")
private external object Http {
    interface AgentOptions {
        var keepAlive: Boolean?
    }

    interface RequestOptions {
        var headers: dynamic
        var method: String?
    }

    open class Agent(options: AgentOptions = definedExternally) {
        fun destroy()
    }
}

private inline fun <T> jsObject(config: T.() -> Unit): T {
    return js("{}").unsafeCast<T>().apply(config)
}

private class GithubJS(private val token: String) : GithubBackend {
    private val agent = Https.Agent(jsObject {
        keepAlive = true
    })

    private val authorizationHeader by lazy {
        val base64input = ":$token"
        val base64output = Buffer.from(base64input).toString(encoding = "base64")
        "Basic $base64output"
    }

    override suspend fun requestForText(method: String, path: String, body: String?): GithubBackend.TextResponse {
        require(path.startsWith("/")) { "path should start with '/': $path" }
        val requestOptions = jsObject<Http.RequestOptions> {
            headers = jsObject {
                this["User-Agent"] = "github-repo-access/0.0"
                this["Accept"] = "application/vnd.github.v3+json"
                this["Authorization"] = authorizationHeader
            }
            this.method = method
        }
        val res = suspendCancellableCoroutine<IncomingMessage> { cont ->
            val req = Https.request("https://api.github.com$path", requestOptions, cont::resume)
            req.on("error") { ex ->
                cont.resumeWithException(ex.unsafeCast<Throwable>())
            }
            cont.invokeOnCancellation { ex ->
                req.destroy(ex.unsafeCast<Error>())
            }
            if (body != null) {
                req.end(body, encoding = "utf-8")
            } else {
                req.end()
            }
        }
        val text = suspendCancellableCoroutine<String> { cont ->
            val chunks = mutableListOf<Buffer>()
            res.on("data") { chunk ->
                chunks += chunk.unsafeCast<Buffer>()
            }
            res.on("error") { err ->
                cont.resumeWithException(err.unsafeCast<Throwable>())
            }
            res.on("end") {
                cont.resume(
                    when (chunks.size) {
                        0 -> ""
                        1 -> chunks[0].toString(encoding = "utf-8")
                        else -> Buffer.concat(chunks.toTypedArray()).toString(encoding = "utf-8")
                    }
                )
            }
            cont.invokeOnCancellation { ex ->
                res.destroy(ex.unsafeCast<Error>())
            }
        }
        return GithubBackend.TextResponse(
            status = res.statusCode.toInt(),
            contentType = res.headers["content-type"]?.toString(),
            text = text
        )
    }

    override suspend fun dispose() {
        agent.destroy()
    }
}
