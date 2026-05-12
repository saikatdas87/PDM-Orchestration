package com.pdm.orchestration.api

import com.ninjasquad.springmockk.MockkBean
import com.pdm.orchestration.exception.PdmApiException
import com.pdm.orchestration.model.FailedFile
import com.pdm.orchestration.model.PdmFile
import com.pdm.orchestration.model.UploadResponse
import com.pdm.orchestration.service.OrchestrationService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(OrchestrationController::class)
class OrchestrationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var orchestrationService: OrchestrationService

    @Test
    fun `upload returns 200 with succeeded and failed files`() {
        every { orchestrationService.orchestrate(any()) } returns UploadResponse(
            succeeded = listOf("f1"),
            failed = listOf(FailedFile("f2", "download failed"))
        )

        mockMvc.post("/api/v1/upload") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"modelId": "model-1"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.succeeded[0]") { value("f1") }
            jsonPath("$.failed[0].fileId") { value("f2") }
            jsonPath("$.failed[0].reason") { value("download failed") }
        }
    }

    @Test
    fun `upload returns 400 when modelId is blank`() {
        mockMvc.post("/api/v1/upload") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"modelId": ""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("modelId: must not be blank") }
        }
    }

    @Test
    fun `upload returns 502 when PDM API fails`() {
        every { orchestrationService.orchestrate(any()) } throws PdmApiException("PDM unreachable")

        mockMvc.post("/api/v1/upload") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"modelId": "model-1"}"""
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.detail") { value("PDM unreachable") }
        }
    }

    @Test
    fun `query returns 200 with file list`() {
        every { orchestrationService.queryOnly(any()) } returns listOf(
            PdmFile("f1", "file1.stp", "/download/f1")
        )

        mockMvc.post("/api/v1/query") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"modelId": "model-1"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].fileId") { value("f1") }
            jsonPath("$[0].fileName") { value("file1.stp") }
            jsonPath("$[0].downloadUrl") { value("/download/f1") }
        }
    }

    @Test
    fun `query returns 400 when modelId is missing`() {
        mockMvc.post("/api/v1/query") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
