package com.pdm.orchestration.service

import com.pdm.orchestration.client.PdmClient
import com.pdm.orchestration.client.UploadClient
import com.pdm.orchestration.config.OrchestrationProperties
import com.pdm.orchestration.model.FailedFile
import com.pdm.orchestration.model.PdmFile
import com.pdm.orchestration.model.UploadRequest
import com.pdm.orchestration.model.UploadResponse
import com.pdm.orchestration.coroutines.Virtual
import com.pdm.orchestration.coroutines.retry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OrchestrationService(
    private val pdmClient: PdmClient,
    private val uploadClient: UploadClient,
    props: OrchestrationProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val semaphore = Semaphore(props.maxConcurrency)

    fun orchestrate(request: UploadRequest): UploadResponse {
        log.info("Starting orchestration for modelId=${request.modelId} orderId=${request.orderId} dateFrom=${request.dateFrom} dateTo=${request.dateTo}")

        val files = pdmClient.queryFiles(request)

        if (files.isEmpty()) {
            log.info("No files found for modelId=${request.modelId}")
            return UploadResponse(emptyList(), emptyList())
        }

        log.info("Found ${files.size} file(s) for modelId=${request.modelId}: ${files.map { it.fileId }}")

        val results = runBlocking(Dispatchers.Virtual) {
            files.map { file ->
                async {
                    semaphore.withPermit {
                        log.info("Processing fileId=${file.fileId} fileName=${file.fileName}")
                        file.fileId to runCatching { retry(maxAttempts = 3, delayMs = 500) { processFile(file) } }
                    }
                }
            }.awaitAll()
        }

        val (successResults, failureResults) = results.partition { (_, result) -> result.isSuccess }

        val succeeded = successResults.map { (fileId, _) ->
            log.info("Successfully uploaded fileId=$fileId")
            fileId
        }

        val failed = failureResults.map { (fileId, result) ->
            val reason = result.exceptionOrNull()?.message ?: "Unknown error"
            log.warn("Failed to process fileId=$fileId reason=$reason")
            FailedFile(fileId, reason)
        }

        log.info("Orchestration complete for modelId=${request.modelId} — succeeded=${succeeded.size} failed=${failed.size}")
        if (failed.isNotEmpty()) {
            log.warn("Failed files: ${failed.map { "${it.fileId}: ${it.reason}" }}")
        }

        return UploadResponse(succeeded, failed)
    }

    fun queryOnly(request: UploadRequest): List<PdmFile> = pdmClient.queryFiles(request)

    // FIXME: holds entire file in memory — won't scale for large CAD files under concurrency.
    //  Could stream via temp file, or use S3 multipart upload (needs AWS SDK + arch change).
    private fun processFile(file: PdmFile) {
        log.info("Downloading fileId=${file.fileId}")
        val content = pdmClient.downloadFile(file)
        log.info("Getting presigned URL for fileId=${file.fileId}")
        val presigned = uploadClient.getPresignedUrl(file)
        log.info("Uploading fileId=${file.fileId} to S3")
        uploadClient.uploadToS3(presigned.uploadUrl, content)
    }
}
