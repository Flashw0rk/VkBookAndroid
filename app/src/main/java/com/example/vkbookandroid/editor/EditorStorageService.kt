package com.example.vkbookandroid.editor

import android.content.Context
import com.google.gson.GsonBuilder
import java.io.File

class EditorStorageService(private val context: Context) : IEditorStorageService {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun editedDir(): File = context.getDir("editor_out", Context.MODE_PRIVATE)

    override fun originalsDir(): File = File(context.filesDir, "data")

    override fun loadJson(useEditedSource: Boolean): MutableMap<String, Any?> {
        val root = if (useEditedSource) editedDir() else originalsDir()
        val file = File(root, "armature_coords.json")
        if (!file.exists()) return mutableMapOf()
        val text = file.readText()
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(text, Map::class.java) as? MutableMap<String, Any?> ?: mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }
    }

    override fun saveJson(base: Map<String, Any?>, useEditedSource: Boolean): File {
        val root = if (useEditedSource) editedDir() else originalsDir()
        if (!root.exists()) root.mkdirs()
        val outFile = File(root, "armature_coords.json")
        outFile.writeText(gson.toJson(base))
        return outFile
    }
}


