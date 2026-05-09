package com.pdm.orchestration.model

import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class UploadRequest(
    @field:NotBlank val modelId: String,
    val orderId: String? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null
)
