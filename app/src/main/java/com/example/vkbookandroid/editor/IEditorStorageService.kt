package com.example.vkbookandroid.editor

import java.io.File

interface IEditorStorageService {
    fun editedDir(): File
    fun originalsDir(): File
    fun loadJson(useEditedSource: Boolean): MutableMap<String, Any?>
    fun saveJson(base: Map<String, Any?>, useEditedSource: Boolean): File
}




