package com.pdm.orchestration.coroutines

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.pdm.orchestration.coroutines.Retry")

suspend fun <T> retry(maxAttempts: Int, delayMs: Long, block: () -> T): T {
    var lastException: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            log.warn("Attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
            if (attempt < maxAttempts - 1) delay(delayMs * (attempt + 1))
        }
    }
    throw lastException!!
}
