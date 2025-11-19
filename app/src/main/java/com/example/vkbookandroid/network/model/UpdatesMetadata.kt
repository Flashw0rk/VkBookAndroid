package com.example.vkbookandroid.network.model

import com.google.gson.annotations.SerializedName

/**
 * Метаданные файла в Cloudflare R2, возвращаемые VkBookServer
 */
data class UpdateFileMetadata(
    @SerializedName("filename") val filename: String = "",
    @SerializedName("size") val size: Long? = null,
    @SerializedName("lastModified") val lastModified: String? = null,
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("hasUpdates") val hasUpdates: Boolean? = null,
    @SerializedName("etag") val etag: String? = null,
    @SerializedName("contentType") val contentType: String? = null
)

