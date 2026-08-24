package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.github.mouse233.localsendkotlin.ui.SystemBars

class ChangelogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_changelog)
        findViewById<android.view.View>(R.id.changelog_back_button).setOnClickListener { finish() }
        findViewById<TextView>(R.id.changelog_content).text = try {
            assets.open(CHANGELOG_FILE).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            getString(R.string.changelog_unavailable)
        }
    }

    private companion object {
        const val CHANGELOG_FILE = "CHANGELOG.md"
    }
}
