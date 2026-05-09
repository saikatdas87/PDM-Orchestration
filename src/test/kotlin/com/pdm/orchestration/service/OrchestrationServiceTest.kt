package com.pdm.orchestration.service

import com.pdm.orchestration.client.PdmClient
import com.pdm.orchestration.client.UploadClient
import com.pdm.orchestration.config.OrchestrationProperties
import com.pdm.orchestration.exception.PdmApiException
import com.pdm.orchestration.exception.UploadApiException
import com.pdm.orchestration.model.PdmFile
import com.pdm.orchestration.model.PresignedUrlResponse
import com.pdm.orchestration.model.UploadRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrchestrationServiceTest {

    private val pdmClient = mockk<PdmClient>()
    private val uploadClient = mockk<UploadClient>()
    private val service = OrchestrationService(pdmClient, uploadClient, OrchestrationProperties(maxConcurrency = 10))

    private val request = UploadRequest(modelId = "model-1")
    private val file1 = PdmFile("f1", "file1.stp", "/download/f1")
    private val file2 = PdmFile("f2", "file2.stp", "/download/f2")
    private val content = "bytes".toByteArray()
    private val presigned1 = PresignedUrlResponse("f1", "https://s3.example.com/f1")
    private val presigned2 = PresignedUrlResponse("f2", "https://s3.example.com/f2")

    @Test
    fun `all files succeed`() {
        every { pdmClient.queryFiles(request) } returns listOf(file1, file2)
        every { pdmClient.downloadFile(any()) } returns content
        every { uploadClient.getPresignedUrl(file1) } returns presigned1
        every { uploadClient.getPresignedUrl(file2) } returns presigned2
        every { uploadClient.uploadToS3(any(), any()) } returns Unit

        val result = service.orchestrate(request)

        assertEquals(listOf("f1", "f2"), result.succeeded)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun `one file fails download, other succeeds`() {
        every { pdmClient.queryFiles(request) } returns listOf(file1, file2)
        every { pdmClient.downloadFile(file1) } throws PdmApiException("download failed")
        every { pdmClient.downloadFile(file2) } returns content
        every { uploadClient.getPresignedUrl(file2) } returns presigned2
        every { uploadClient.uploadToS3(any(), any()) } returns Unit

        val result = service.orchestrate(request)

        assertEquals(listOf("f2"), result.succeeded)
        assertEquals(1, result.failed.size)
        assertEquals("f1", result.failed[0].fileId)
    }

    @Test
    fun `one file fails S3 upload, other succeeds`() {
        every { pdmClient.queryFiles(request) } returns listOf(file1, file2)
        every { pdmClient.downloadFile(any()) } returns content
        every { uploadClient.getPresignedUrl(file1) } returns presigned1
        every { uploadClient.getPresignedUrl(file2) } returns presigned2
        every { uploadClient.uploadToS3(presigned1.uploadUrl, any()) } throws UploadApiException("S3 error")
        every { uploadClient.uploadToS3(presigned2.uploadUrl, any()) } returns Unit

        val result = service.orchestrate(request)

        assertEquals(listOf("f2"), result.succeeded)
        assertEquals("f1", result.failed[0].fileId)
    }

    @Test
    fun `PDM query failure propagates as exception`() {
        every { pdmClient.queryFiles(request) } throws PdmApiException("PDM unreachable")

        assertThrows<PdmApiException> { service.orchestrate(request) }
    }

    @Test
    fun `empty file list returns empty response`() {
        every { pdmClient.queryFiles(request) } returns emptyList()

        val result = service.orchestrate(request)

        assertTrue(result.succeeded.isEmpty())
        assertTrue(result.failed.isEmpty())
        verify(exactly = 0) { pdmClient.downloadFile(any()) }
    }

    @Test
    fun `queryOnly delegates to pdmClient`() {
        every { pdmClient.queryFiles(request) } returns listOf(file1)

        val result = service.queryOnly(request)

        assertEquals(listOf(file1), result)
    }
}
