package actions.kotlin

import kotlinx.coroutines.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.promise

private inline fun <T> jsObject(config: T.() -> Unit): T {
    return js("{}").unsafeCast<T>().apply(config)
}

/**
 * Gets the value of an input.
 *
 * Returns an empty string if the value is not defined and [required] is false.
 *
 * @param name name of the input to get
 * @param required throw error if input not specified
 * @param trimWhitespace trim whitespace from start and end of value
 * @see actions.core.getInput
 */
fun getInput(name: String, required: Boolean = false, trimWhitespace: Boolean = true): String {
    return actions.core.getInput(name, jsObject {
        this.required = required
        this.trimWhitespace = trimWhitespace
    })
}

/**
 * Gets the value of an input, or `null` if it is not supplied.
 *
 * @param name name of the input to get
 * @param trimWhitespace trim whitespace from start and end of value
 * @see actions.core.getInput
 */
fun getInputOrNull(name: String, trimWhitespace: Boolean = true): String? {
    return getInput(name, required = false, trimWhitespace = trimWhitespace).takeIf { it != "" }
}

/**
 * Gets the value of an input, supplying a default if it is not specified.
 *
 * @param name name of the input to get
 * @param trimWhitespace trim whitespace from start and end of value
 * @param defaultSupplier supply default value
 * @see actions.core.getInput
 */
fun getInput(name: String, trimWhitespace: Boolean = true, defaultSupplier: () -> String): String {
    return getInput(name, required = false, trimWhitespace = trimWhitespace).takeIf { it != "" } ?: defaultSupplier()
}

/**
 * Gets the values of a multiline input. Empty values are omitted.
 *
 * @param name name of the input to get
 * @param required Throw error if input is not specified
 * @return input values, possibly empty
 * @see actions.core.getMultilineInput
 */
fun getMultilineInput(name: String, required: Boolean = false): List<String> {
    return actions.core.getMultilineInput(name, jsObject {
        this.required = required
    }).asList()
}

/**
 * Gets the values of a multiline input, supplying a default if it is not specified.
 *
 * @param name name of the input to get
 * @return input values, empty only if [defaultSupplier] provided an empty list
 * @see actions.core.getMultilineInput
 */
fun getMultilineInput(name: String, defaultSupplier: () -> List<String>): List<String> {
    return getMultilineInput(name, required = false).takeIf { it.isNotEmpty() } ?: defaultSupplier()
}

/**
 * Wrap some (suspending) code in a group.
 * @see actions.core.group
 */
suspend fun <T> group(name: String, fn: suspend () -> T): T {
    return coroutineScope {
        actions.core.group(name) {
            promise {
                fn()
            }
        }.await()
    }
}

/**
 * Indicates if the action is being run by Github Actions, as opposed to locally.
 *
 * @see GITHUB_ACTIONS
 */
val runningInGithubActions: Boolean
    get() = GITHUB_ACTIONS != null
