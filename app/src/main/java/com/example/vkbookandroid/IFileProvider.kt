package com.example.vkbookandroid

import java.io.InputStream

/**
 * Интерфейс для провайдеров файлов
 */
interface IFileProvider {
    fun open(relativePath: String): InputStream
}


