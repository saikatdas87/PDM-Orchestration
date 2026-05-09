package com.pdm.orchestration.client

import com.pdm.orchestration.exception.UploadApiException
import com.pdm.orchestration.model.PdmFile
import com.pdm.orchestration.model.PresignedUrlResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class UploadClient(
    @param:Qualifier("uploadRestClient") private val restClient: RestClient,
    @param:Qualifier("s3RestClient") private val s3RestClient: RestClient
) {

    fun getPresignedUrl(file: PdmFile): PresignedUrlResponse {
        try {
            return restClient.post()
                .uri("/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("fileId" to file.fileId, "fileName" to file.fileName))
                .retrieve()
                .body<PresignedUrlResponse>()
                ?: throw UploadApiException("Empty presigned URL response for fileId=${file.fileId}")
        } catch (e: RestClientException) {
            throw UploadApiException("Failed to get presigned URL for fileId=${file.fileId}", e)
        }
    }

    // Not retrying S3 upload — presigned URLs may be single-use or short-lived,
    // retrying could cause 403. If needed, get a fresh presigned URL and retry the whole flow.
    fun uploadToS3(presignedUrl: String, content: ByteArray) {
        try {
            s3RestClient.put()
                .uri(presignedUrl)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientException) {
            throw UploadApiException("Failed to upload to S3 via presigned URL", e)
        }
    }
}
