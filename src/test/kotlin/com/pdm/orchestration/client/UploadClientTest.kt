package com.pdm.orchestration.client

import com.pdm.orchestration.exception.UploadApiException
import com.pdm.orchestration.model.PdmFile
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

@RestClientTest(UploadClient::class)
class UploadClientTest {

    @TestConfiguration
    class Config {
        @Bean("uploadRestClient")
        fun uploadRestClient(builder: RestClient.Builder): RestClient = builder.build()

        @Bean("s3RestClient")
        fun s3RestClient(): RestClient = RestClient.create()
    }

    @Autowired lateinit var uploadClient: UploadClient
    @Autowired lateinit var server: MockRestServiceServer

    private val file = PdmFile("f1", "file1.stp", "/download/f1")

    @Test
    fun `getPresignedUrl returns parsed response`() {
        server.expect(requestTo(containsString("/presigned-url")))
            .andRespond(
                withSuccess(
                    """{"fileId":"f1","uploadUrl":"https://s3.example.com/f1"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = uploadClient.getPresignedUrl(file)

        assertEquals("f1", result.fileId)
        assertEquals("https://s3.example.com/f1", result.uploadUrl)
    }

    @Test
    fun `getPresignedUrl throws UploadApiException on server error`() {
        server.expect(requestTo(containsString("/presigned-url")))
            .andRespond(withServerError())

        assertThrows<UploadApiException> { uploadClient.getPresignedUrl(file) }
    }
}
