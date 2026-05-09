package com.pdm.orchestration.client

import com.pdm.orchestration.exception.PdmApiException
import com.pdm.orchestration.model.UploadRequest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

@RestClientTest(PdmClient::class)
class PdmClientTest {

    @TestConfiguration
    class Config {
        @Bean("pdmRestClient")
        fun pdmRestClient(builder: RestClient.Builder): RestClient = builder.build()
    }

    @Autowired lateinit var pdmClient: PdmClient
    @Autowired lateinit var server: MockRestServiceServer

    private val request = UploadRequest(modelId = "model-1")

    @Test
    fun `queryFiles returns parsed file list`() {
        server.expect(requestTo(containsString("/files?modelId=model-1")))
            .andRespond(
                withSuccess(
                    """[{"fileId":"f1","fileName":"file1.stp","downloadUrl":"/download/f1"}]""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = pdmClient.queryFiles(request)

        assertEquals(1, result.size)
        assertEquals("f1", result[0].fileId)
    }

    @Test
    fun `queryFiles throws PdmApiException on server error`() {
        server.expect(requestTo(containsString("/files")))
            .andRespond(withServerError())

        assertThrows<PdmApiException> { pdmClient.queryFiles(request) }
    }

    @Test
    fun `downloadFile returns bytes`() {
        server.expect(requestTo(containsString("/download/f1")))
            .andRespond(withSuccess("filecontent".toByteArray(), MediaType.APPLICATION_OCTET_STREAM))

        val result = pdmClient.downloadFile(com.pdm.orchestration.model.PdmFile("f1", "file1.stp", "/download/f1"))

        assertEquals("filecontent", String(result))
    }

    @Test
    fun `downloadFile throws PdmApiException on server error`() {
        server.expect(requestTo(containsString("/download/f1")))
            .andRespond(withServerError())

        assertThrows<PdmApiException> {
            pdmClient.downloadFile(com.pdm.orchestration.model.PdmFile("f1", "file1.stp", "/download/f1"))
        }
    }
}
