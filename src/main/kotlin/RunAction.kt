package action

import actions.core.setFailed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Launches a suspending body of code for the action, and calls [setFailed] if it throws an exception.
 *
 * This is intended to be used as the top level entry point, analogous to `runBlocking` in a JVM
 * context.
 *
 * @param context Coroutine context (a [Job] will be added)
 * @param body Body of action
 */
fun runAction(context: CoroutineContext = EmptyCoroutineContext, body: suspend CoroutineScope.() -> Unit) {
    val job = Job(parent = context[Job])
    val scope = CoroutineScope(context + job)

    job.invokeOnCompletion { ex ->
        Dispatchers.Default.dispatch(context, Runnable {
            if (ex != null) {
                setFailed(ex.unsafeCast<Error>())
            }
        })
    }

    body.startCoroutine(scope, Continuation(scope.coroutineContext) { result ->
        result.fold({
            job.complete()
        }, { ex ->
            job.completeExceptionally(ex)
        })
    })
}
