package com.pdm.orchestration.client

import com.pdm.orchestration.exception.PdmApiException
import com.pdm.orchestration.model.PdmFile
import com.pdm.orchestration.model.UploadRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class PdmClient(@param:Qualifier("pdmRestClient") private val restClient: RestClient) {

    fun queryFiles(request: UploadRequest): List<PdmFile> {
        try {
            return restClient.get()
                .uri { builder ->
                    builder.path("/files")
                        .queryParam("modelId", request.modelId)
                        .apply { request.orderId?.let { queryParam("orderId", it) } }
                        .apply { request.dateFrom?.let { queryParam("dateFrom", it) } }
                        .apply { request.dateTo?.let { queryParam("dateTo", it) } }
                        .build()
                }
                .retrieve()
                .body(object : ParameterizedTypeReference<List<PdmFile>>() {})
                ?: emptyList()
        } catch (e: RestClientException) {
            throw PdmApiException("Failed to query PDM files for modelId=${request.modelId}", e)
        }
    }

    // Assumes downloadUrl is always a relative path resolved against the PDM base URL.
    // If PDM ever returns absolute URLs (e.g. CDN links), this will break.
    fun downloadFile(file: PdmFile): ByteArray {
        try {
            return restClient.get()
                .uri(file.downloadUrl)
                .retrieve()
                .body<ByteArray>()
                ?: throw PdmApiException("Empty response downloading fileId=${file.fileId}")
        } catch (e: RestClientException) {
            throw PdmApiException("Failed to download fileId=${file.fileId}", e)
        }
    }
}
