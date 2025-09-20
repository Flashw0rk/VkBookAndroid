package com.example.vkbookandroid.model

import com.google.gson.annotations.SerializedName

/**
 * Информация о файле на сервере
 */
data class FileInfo(
    @SerializedName("filename")
    val filename: String,
    
    @SerializedName("size")
    val size: Long,
    
    @SerializedName("lastModified")
    val lastModified: String,
    
    @SerializedName("mimeType")
    val mimeType: String?,
    
    @SerializedName("extension")
    val extension: String,
    
    @SerializedName("path")
    val path: String
)






