package com.pdm.orchestration.model

data class UploadResponse(
    val succeeded: List<String>,
    val failed: List<FailedFile>
)

data class FailedFile(
    val fileId: String,
    val reason: String
)
