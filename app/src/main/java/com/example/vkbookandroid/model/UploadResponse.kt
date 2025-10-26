package com.example.vkbookandroid.model

data class UploadResponse(
    val success: Boolean,
    val message: String,
    val filename: String?,
    val size: Long?
)

data class AuthStatusResponse(
    val success: Boolean,
    val message: String,
    val enabled: Boolean
)


