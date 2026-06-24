package com.qingyi.hear.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 默认重试配置
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 10_000L,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
) {
    companion object {
        val DEFAULT = RetryConfig()
        val AGGRESSIVE = RetryConfig(maxRetries = 5, initialDelayMs = 500L)
        val NONE = RetryConfig(maxRetries = 0)
    }
}

/**
 * 可重试的 HTTP 异常
 */
class RetryableHttpException(
    message: String,
    val statusCode: Int,
    cause: Throwable? = null,
) : IOException(message, cause)

suspend fun OkHttpClient.await(request: Request): Response =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            },
        )
    }

/**
 * 带重试机制的 HTTP 请求
 *
 * 优化点：
 * 1. 指数退避策略，避免服务器压力
 * 2. 随机抖动，防止请求风暴
 * 3. 可配置重试次数和延迟
 * 4. 自动识别可重试的错误类型
 */
suspend fun OkHttpClient.awaitWithRetry(
    request: Request,
    retryConfig: RetryConfig = RetryConfig.DEFAULT,
): Response {
    var lastException: IOException? = null
    var delayMs = retryConfig.initialDelayMs

    repeat(retryConfig.maxRetries + 1) { attempt ->
        try {
            val response = await(request)

            // 检查是否需要重试
            if (response.isSuccessful || !isRetryableStatusCode(response.code)) {
                return response
            }

            // 关闭不成功的响应
            response.close()

            if (attempt < retryConfig.maxRetries) {
                lastException = RetryableHttpException(
                    message = "HTTP ${response.code}: ${response.message}",
                    statusCode = response.code,
                )
                // 等待后重试
                delay(calculateDelayWithJitter(delayMs, retryConfig))
                delayMs = min(
                    (delayMs * retryConfig.backoffMultiplier).toLong(),
                    retryConfig.maxDelayMs,
                )
            } else {
                throw RetryableHttpException(
                    message = "HTTP ${response.code}: ${response.message} (重试${retryConfig.maxRetries}次后失败)",
                    statusCode = response.code,
                )
            }
        } catch (e: IOException) {
            if (attempt < retryConfig.maxRetries && isRetryableException(e)) {
                lastException = e
                delay(calculateDelayWithJitter(delayMs, retryConfig))
                delayMs = min(
                    (delayMs * retryConfig.backoffMultiplier).toLong(),
                    retryConfig.maxDelayMs,
                )
            } else {
                throw e
            }
        }
    }

    throw lastException ?: IOException("请求失败")
}

suspend fun OkHttpClient.getText(request: Request): String =
    await(request).use { response ->
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }
        response.body.string()
    }

/**
 * 带重试的 getText
 */
suspend fun OkHttpClient.getTextWithRetry(
    request: Request,
    retryConfig: RetryConfig = RetryConfig.DEFAULT,
): String =
    awaitWithRetry(request, retryConfig).use { response ->
        response.body.string()
    }

/**
 * 判断 HTTP 状态码是否可重试
 */
private fun isRetryableStatusCode(code: Int): Boolean {
    return code in listOf(
        408, // Request Timeout
        429, // Too Many Requests
        500, // Internal Server Error
        502, // Bad Gateway
        503, // Service Unavailable
        504, // Gateway Timeout
    )
}

/**
 * 判断异常是否可重试
 */
private fun isRetryableException(e: IOException): Boolean {
    val message = e.message?.lowercase() ?: return false
    return message.contains("timeout") ||
        message.contains("connection") ||
        message.contains("reset") ||
        message.contains("broken pipe") ||
        message.contains("canceled")
}

/**
 * 计算带抖动的延迟时间
 */
private fun calculateDelayWithJitter(
    baseDelayMs: Long,
    config: RetryConfig,
): Long {
    val jitter = baseDelayMs * config.jitterFactor
    val jitterRange = (-jitter).toLong()..jitter.toLong()
    return (baseDelayMs + Random.nextLong(jitterRange.first, jitterRange.lastInclusive))
        .coerceAtLeast(0L)
}
