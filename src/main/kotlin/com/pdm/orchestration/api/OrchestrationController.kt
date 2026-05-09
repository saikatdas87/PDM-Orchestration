package com.pdm.orchestration.api

import com.pdm.orchestration.model.PdmFile
import com.pdm.orchestration.model.UploadRequest
import com.pdm.orchestration.model.UploadResponse
import com.pdm.orchestration.service.OrchestrationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class OrchestrationController(private val orchestrationService: OrchestrationService) {

    @PostMapping("/upload")
    fun upload(@Valid @RequestBody request: UploadRequest): ResponseEntity<UploadResponse> =
        ResponseEntity.ok(orchestrationService.orchestrate(request))

    @PostMapping("/query")
    fun query(@Valid @RequestBody request: UploadRequest): ResponseEntity<List<PdmFile>> =
        ResponseEntity.ok(orchestrationService.queryOnly(request))
}
