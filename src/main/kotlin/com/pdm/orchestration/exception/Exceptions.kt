package com.pdm.orchestration.exception

class PdmApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class UploadApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
