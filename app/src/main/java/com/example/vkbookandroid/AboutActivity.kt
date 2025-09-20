package com.example.vkbookandroid

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран "О приложении": показывает версию (name/code) и позволяет быстро скопировать информацию.
 */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val btnCopy = findViewById<Button>(R.id.btnCopyVersion)

        // Получаем версию приложения из PackageManager
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = pInfo.versionName
        val versionCode = pInfo.longVersionCode
        val versionText = "Версия: ${'$'}versionName (${ '$'}versionCode)"

        tvVersion.text = versionText

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("version", versionText))
            android.widget.Toast.makeText(this, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}




