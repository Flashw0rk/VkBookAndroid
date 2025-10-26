package com.example.vkbookandroid.editor

import com.example.vkbookandroid.EditorMarkerOverlayView
import java.io.File

interface IArmaturesExcelWriter {
    fun write(
        file: File,
        pdfName: String?,
        items: List<EditorMarkerOverlayView.EditorMarkerItem>,
        deletedIds: Set<String> = emptySet()
    )
}



