@file:JsModule("@actions/cache")

package actions.cache

import kotlin.js.Promise

// see https://github.com/actions/toolkit/tree/main/packages/cache

external class ValidationError : Throwable

external class ReserveCacheError : Throwable

/**
 * Options to control cache upload
 */
external interface UploadOptions {
    /**
     * Number of parallel cache upload
     *
     * Default: 4
     */
    var uploadConcurrency: Number?

    /**
     * Maximum chunk size in bytes for cache upload
     *
     * Default: 32Mb
     */
    var uploadChunkSize: Number?
}

/**
 * Options to control cache download
 */
external interface DownloadOptions {
    /**
     * Indicates whether to use the Azure Blob SDK to download caches
     * that are stored on Azure Blob Storage to improve reliability and
     * performance
     *
     * Default: true
     */
    var useAzureSdk: Boolean?

    /**
     * Number of parallel downloads (this option only applies when using
     * the Azure SDK)
     *
     * Default: 8
     */
    var downloadConcurrency: Number?

    /**
     * Maximum time for each download request, in milliseconds (this
     * option only applies when using the Azure SDK)
     *
     * Default: 30000
     */
    var timeoutInMs: Number?
}

/**
 * Saves a list of files with the specified key
 *
 * @param paths a list of file paths to be cached
 * @param primaryKey an explicit key for restoring the cache
 * @param options cache upload options
 * @returns number returns cacheId if the cache was saved successfully and throws an error if save fails
 */
external fun saveCache(
    paths: Array<String>,
    primaryKey: String,
    options: UploadOptions = definedExternally
): Promise<Number>

/**
 * Restores cache from keys
 *
 * @param paths a list of file paths to restore from the cache
 * @param primaryKey an explicit key for restoring the cache
 * @param restoreKeys an optional ordered list of keys to use for restoring the cache if no cache hit occurred for key
 * @param downloadOptions cache download options
 * @returns string returns the key for the cache hit, otherwise returns undefined
 */
external fun restoreCache(
    paths: Array<String>,
    primaryKey: String,
    restoreKeys: Array<String> = definedExternally,
    downloadOptions: DownloadOptions = definedExternally
): Promise<Unit>
