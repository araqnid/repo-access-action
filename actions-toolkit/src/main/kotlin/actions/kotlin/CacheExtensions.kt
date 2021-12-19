package actions.kotlin

import actions.cache.DownloadOptions
import actions.cache.UploadOptions
import kotlinx.coroutines.await

private inline fun <T> jsObject(config: T.() -> Unit): T {
    return js("{}").unsafeCast<T>().apply(config)
}

private const val DEFAULT_UPLOAD_CHUNK_SIZE = 32 * 1048576
private const val DEFAULT_UPLOAD_CONCURRENCY = 4

private const val DEFAULT_DOWNLOAD_TIMEOUT_MS = 30_000
private const val DEFAULT_DOWNLOAD_CONCURRENCY = 8
private const val DEFAULT_DOWNLOAD_USE_AZURE_SDK = true

/**
 * @see actions.cache.saveCache
 */
suspend fun saveCache(
    paths: Set<String>,
    primaryKey: String,
    uploadConcurrency: Number = DEFAULT_UPLOAD_CONCURRENCY,
    uploadChunkSize: Number = DEFAULT_UPLOAD_CHUNK_SIZE
): Number {
    return actions.cache.saveCache(paths.toTypedArray(), primaryKey, uploadOptions(uploadConcurrency, uploadChunkSize))
        .await()
}

/**
 * @see actions.cache.restoreCache
 */
suspend fun restoreCache(
    paths: Set<String>,
    primaryKey: String,
    restoreKeys: List<String> = emptyList(),
    useAzureSDK: Boolean = DEFAULT_DOWNLOAD_USE_AZURE_SDK,
    downloadConcurrency: Number = DEFAULT_DOWNLOAD_CONCURRENCY,
    timeoutInMs: Number = DEFAULT_DOWNLOAD_TIMEOUT_MS
) {
    return actions.cache.restoreCache(
        paths.toTypedArray(),
        primaryKey,
        restoreKeys.toTypedArray(),
        downloadOptions(useAzureSDK, downloadConcurrency, timeoutInMs)
    ).await()
}

fun uploadOptions(
    uploadConcurrency: Number = DEFAULT_UPLOAD_CONCURRENCY,
    uploadChunkSize: Number = DEFAULT_UPLOAD_CHUNK_SIZE
): UploadOptions {
    return jsObject {
        this.uploadConcurrency = uploadConcurrency
        this.uploadChunkSize = uploadChunkSize
    }
}

fun downloadOptions(
    useAzureSdk: Boolean = DEFAULT_DOWNLOAD_USE_AZURE_SDK,
    downloadConcurrency: Number = DEFAULT_DOWNLOAD_CONCURRENCY,
    timeoutInMs: Number = DEFAULT_DOWNLOAD_TIMEOUT_MS
): DownloadOptions {
    return jsObject {
        this.useAzureSdk = useAzureSdk
        this.downloadConcurrency = downloadConcurrency
        this.timeoutInMs = timeoutInMs
    }
}
