package io.github.mouse233.localsendkotlin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import io.github.mouse233.localsendkotlin.ui.SystemBars
import io.github.mouse233.localsendkotlin.ui.ThemeColors

class TextDocumentActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_text_document)
        ThemeColors.apply(this)
        findViewById<android.view.View>(R.id.document_back_button).setOnClickListener { finish() }
        findViewById<TextView>(R.id.document_title).text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        findViewById<TextView>(R.id.document_content).text = try {
            assets.open(intent.getStringExtra(EXTRA_ASSET_NAME).orEmpty()).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            getString(R.string.document_unavailable)
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ASSET_NAME = "asset_name"

        fun intent(context: Context, title: String, assetName: String): Intent =
            Intent(context, TextDocumentActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ASSET_NAME, assetName)
    }
}
