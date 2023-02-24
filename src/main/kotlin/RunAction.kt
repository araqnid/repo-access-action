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
 * @param block Body of action
 */
fun runAction(
    context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit
) {
    val job = Job()

    job.invokeOnCompletion { ex ->
        Dispatchers.Default.dispatch(context, Runnable {
            if (ex != null) {
                setFailed(ex.unsafeCast<Error>())
            }
        })
    }

    val completion = object : Continuation<Unit> {
        override val context = context + job

        override fun resumeWith(result: Result<Unit>) {
            result.fold({
                job.complete()
            }, { ex ->
                job.completeExceptionally(ex)
            })
        }
    }

    block.startCoroutine(CoroutineScope(completion.context), completion)
}
