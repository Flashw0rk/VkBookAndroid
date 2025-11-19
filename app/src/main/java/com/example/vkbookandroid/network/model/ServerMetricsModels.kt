package com.example.vkbookandroid.network.model

import com.google.gson.annotations.SerializedName

data class ServerInfoPayload(
    val metrics: ServerMetricsResponse? = null,
    val r2Usage: R2UsageResponse? = null
)

data class ServerMetricsResponse(
    @SerializedName("rateLimit") val rateLimit: RateLimitInfo? = null,
    @SerializedName("downloads") val downloads: DownloadsInfo? = null,
    @SerializedName("uploads") val uploads: UploadsInfo? = null,
    @SerializedName("rateLimitGlobal") val rateLimitGlobal: RateLimitGlobalInfo? = null,
    @SerializedName("r2Warnings") val r2Warnings: R2WarningsInfo? = null
)

data class RateLimitInfo(
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("used") val used: Int? = null,
    @SerializedName("remaining") val remaining: Int? = null,
    @SerializedName("resetTimestamp") val resetTimestamp: String? = null,
    @SerializedName("warning") val warning: Boolean? = null,
    @SerializedName("blocked") val blocked: Boolean? = null
)

data class DownloadsInfo(
    @SerializedName("personalToday") val personalToday: Int? = null,
    @SerializedName("globalToday") val globalToday: Int? = null,
    @SerializedName("lastDownload") val lastDownload: DownloadEntry? = null
)

data class DownloadEntry(
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class UploadsInfo(
    @SerializedName("today") val today: Int? = null,
    @SerializedName("total") val total: Int? = null
)

data class RateLimitGlobalInfo(
    @SerializedName("requestsPerHour") val requestsPerHour: Int? = null,
    @SerializedName("clientsAtLimit") val clientsAtLimit: Int? = null,
    @SerializedName("blockedClients") val blockedClients: Int? = null,
    @SerializedName("activeIps") val activeIps: Int? = null, // совместимость со старыми версиями
    @SerializedName("blockedIps") val blockedIps: Int? = null,
    @SerializedName("totalRequests") val totalRequests: Int? = null
)

data class R2WarningsInfo(
    @SerializedName("storage") val storage: WarningFlag? = null,
    @SerializedName("classA") val classA: WarningFlag? = null,
    @SerializedName("classB") val classB: WarningFlag? = null,
    @SerializedName("warningFlags") val warningFlags: List<String>? = null,
    @SerializedName("month") val month: String? = null,
    @SerializedName("operationsMonth") val operationsMonth: String? = null
)

data class WarningFlag(
    @SerializedName("warning") val warning: Boolean? = null,
    @SerializedName("blocked") val blocked: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("value") val value: Boolean? = null,
    @SerializedName("operations") val operations: Double? = null,
    @SerializedName("monthStart") val monthStart: String? = null
)

data class UsageQuotaInfo(
    @SerializedName("used") val used: Double? = null,
    @SerializedName("limit") val limit: Double? = null,
    @SerializedName("remaining") val remaining: Double? = null,
    @SerializedName("warning") val warning: Boolean? = null,
    @SerializedName("blocked") val blocked: Boolean? = null,
    @SerializedName("percentage") val percentage: Double? = null,
    @SerializedName("unit") val unit: String? = null,
    @SerializedName("operations") val operations: Double? = null,
    @SerializedName("monthStart") val monthStart: String? = null
)

data class R2UsageResponse(
    @SerializedName("storage") val storage: UsageQuotaInfo? = null,
    @SerializedName("classA") val classA: UsageQuotaInfo? = null,
    @SerializedName("classB") val classB: UsageQuotaInfo? = null,
    @SerializedName("warnings") val warnings: List<String>? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

