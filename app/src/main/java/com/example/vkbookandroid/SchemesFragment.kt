package com.example.vkbookandroid

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class SchemesFragment : Fragment() {

    private lateinit var buttonPickPdf: Button
    private lateinit var textSelectedPath: TextView
    private lateinit var pdfContainer: FrameLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schemes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonPickPdf = view.findViewById(R.id.button_pick_pdf)
        textSelectedPath = view.findViewById(R.id.text_selected_path)
        pdfContainer = view.findViewById(R.id.pdf_container)

        buttonPickPdf.setOnClickListener { openAssetSchemePicker() }
        // Отключаем звуки кликов у всего дерева фрагмента
        view.isSoundEffectsEnabled = false
    }

    private fun openAssetSchemePicker() {
        val assets = requireContext().assets
        val files = (assets.list("Schemes") ?: emptyArray())
            .filter { it.endsWith(".pdf", ignoreCase = true) || it.endsWith(".txt", ignoreCase = true) }
            .sorted()

        if (files.isEmpty()) {
            textSelectedPath.text = "В папке assets/Schemes нет файлов"
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите схему из assets/Schemes")
            .setItems(files.toTypedArray()) { _, which ->
                val fileName = files[which]
                val assetPath = "assets/Schemes/$fileName"
                textSelectedPath.text = assetPath
                openAssetPath(assetPath)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openAssetPath(assetPath: String) {
        val intent = Intent(requireContext(), PdfViewerActivity::class.java).apply {
            putExtra("pdf_path", assetPath)
        }
        startActivity(intent)
    }
}