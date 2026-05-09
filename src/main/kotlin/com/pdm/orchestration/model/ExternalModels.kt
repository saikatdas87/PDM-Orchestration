package com.pdm.orchestration.model

// Represents a file record returned by the PDM query API
data class PdmFile(
    val fileId: String,
    val fileName: String,
    val downloadUrl: String
)

// Represents a presigned URL response from the upload API
data class PresignedUrlResponse(
    val fileId: String,
    val uploadUrl: String
)
