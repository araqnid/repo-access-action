@file:JsModule("@actions/core")

package actions.core

import kotlin.js.Promise

// see https://github.com/actions/toolkit/tree/main/packages/core

/**
 * Interface for getInput options
 */
external interface InputOptions {
    /** Optional. Whether the input is required. If required and not present, will throw. Defaults to false */
    var required: Boolean?

    /** Optional. Whether leading/trailing whitespace will be trimmed for the input. Defaults to true */
    var trimWhitespace: Boolean?
}

// Inputs/Outputs

/**
 * Gets the value of an input.
 * Unless trimWhitespace is set to false in InputOptions, the value is also trimmed.
 * Returns an empty string if the value is not defined.
 *
 * @param     name     name of the input to get
 * @param     options  optional. See [InputOptions].
 * @returns   string
 */
external fun getInput(name: String, options: InputOptions = definedExternally): String

/**
 * Gets the input value of the boolean type in the YAML 1.2 "core schema" specification.
 * Support boolean input list: `true | True | TRUE | false | False | FALSE` .
 * The return value is also in boolean type.
 * ref: https://yaml.org/spec/1.2/spec.html#id2804923
 *
 * @param     name     name of the input to get
 * @param     options  optional. See InputOptions.
 * @returns   boolean
 */
external fun getBooleanInput(name: String, options: InputOptions = definedExternally): Boolean

/**
 * Gets the values of an multiline input.  Each value is also trimmed.
 *
 * @param     name     name of the input to get
 * @param     options  optional. See InputOptions.
 * @returns   string[]
 *
 */
external fun getMultilineInput(name: String, options: InputOptions = definedExternally): Array<String>

/**
 * Sets the value of an output.
 *
 * @param     name     name of the output to set
 * @param     value    value to store. Non-string values will be converted to a string via JSON.stringify
 */
external fun setOutput(name: String, value: Any?)

// Exporting variables

/**
 * Sets env variable for this action and future actions in the job
 * @param name the name of the variable to set
 * @param val the value of the variable. Non-string values will be converted to a string via JSON.stringify
 */
external fun exportVariable(name: String, `val`: Any?)

// Setting a secret

/**
 * Registers a secret which will get masked from logs
 * @param secret value of the secret
 */
external fun setSecret(secret: String)

// PATH Manipulation

/**
 * Prepends inputPath to the PATH (for this action and future actions)
 * @param inputPath
 */
external fun addPath(inputPath: String)

// Exit codes

/**
 * Sets the action status to failed.
 * When the action exits it will be with an exit code of 1
 * @param message add error issue message
 */
external fun setFailed(message: String)

/**
 * Sets the action status to failed.
 * When the action exits it will be with an exit code of 1
 * @param message add error issue message
 */
external fun setFailed(message: Throwable)

// Logging

/**
 * Gets whether Actions Step Debug is on or not
 */
external fun isDebug(): Boolean

/**
 * Writes debug message to user log
 * @param message debug message
 */
external fun debug(message: String)

/**
 * Writes info to log with console.log.
 * @param message info message
 */
external fun info(message: String)

/**
 * Adds a notice issue
 * @param message notice issue message. Errors will be converted to string via toString()
 * @param properties optional properties to add to the annotation.
 */
external fun notice(message: String, properties: AnnotationProperties = definedExternally)

/**
 * Adds a warning issue
 * @param message warning issue message. Errors will be converted to string via toString()
 * @param properties optional properties to add to the annotation.
 */
external fun warning(message: String, properties: AnnotationProperties = definedExternally)

/**
 * Adds an error issue
 * @param message error issue message. Errors will be converted to string via toString()
 * @param properties optional properties to add to the annotation.
 */
external fun error(message: String, properties: AnnotationProperties = definedExternally)

/**
 * Begin an output group.
 *
 * Output until the next `groupEnd` will be foldable in this group
 *
 * @param name The name of the output group
 */
external fun startGroup(name: String)

/**
 * End an output group.
 */
external fun endGroup()

/**
 * Wrap an asynchronous function call in a group.
 *
 * Returns the same type as the function itself.
 *
 * @param name The name of the group
 * @param fn The function to wrap in the group
 */
external fun <T> group(name: String, fn: () -> Promise<T>): Promise<T>

// Annotations

external interface AnnotationProperties {
    /**
     * A title for the annotation.
     */
    var title: String?

    /**
     * The name of the file for which the annotation should be created.
     */
    var file: String?

    /**
     * The start line for the annotation.
     */
    var startLine: Number?

    /**
     * The end line for the annotation. Defaults to `startLine` when `startLine` is provided.
     */
    var endLine: Number?

    /**
     * The start column for the annotation. Cannot be sent when `startLine` and `endLine` are different values.
     */
    var startColumn: Number?

    /**
     * The start column for the annotation. Cannot be sent when `startLine` and `endLine` are different values.
     * Defaults to `startColumn` when `startColumn` is provided.
     */
    var endColumn: Number?
}

// Action state

/**
 * Saves state for current action, the state can only be retrieved by this action's post job execution.
 *
 * @param     name     name of the state to store
 * @param     value    value to store. Non-string values will be converted to a string via JSON.stringify
 */
external fun saveState(name: String, value: Any?)

/**
 * Gets the value of an state set by this action's main execution.
 *
 * @param     name     name of the state to get
 * @returns   string
 */
external fun getState(name: String): String

// OIDC Token

external fun getIDToken(aud: String = definedExternally): Promise<String>
