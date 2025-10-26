package com.example.vkbookandroid.repository

/**
 * Исключение для случая превышения лимита запросов к серверу (HTTP 429)
 */
class RateLimitException(message: String = "Достигнут лимит запросов к серверу") : Exception(message)

