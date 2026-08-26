package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.RadioGroup
import android.widget.TextView
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.security.VerificationCode
import io.github.mouse233.localsendkotlin.ui.SystemBars
import io.github.mouse233.localsendkotlin.ui.ThemeColors

class VerificationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_verification)
        ThemeColors.apply(this)
        findViewById<View>(R.id.verification_back_button).setOnClickListener { finish() }
        val remoteFingerprint = intent.getStringExtra(EXTRA_FINGERPRINT).orEmpty()
        if (remoteFingerprint.isBlank()) { finish(); return }
        val iconGrid = findViewById<GridLayout>(R.id.verification_icon_grid)
        val textCode = findViewById<TextView>(R.id.verification_text_code)
        val mode = findViewById<RadioGroup>(R.id.verification_mode)
        mode.isEnabled = false
        mode.setOnCheckedChangeListener { _, checkedId ->
            iconGrid.visibility = if (checkedId == R.id.verification_icons) View.VISIBLE else View.GONE
            textCode.visibility = if (checkedId == R.id.verification_text) View.VISIBLE else View.GONE
        }
        Thread {
            // The first identity load may create and persist a TLS certificate.
            // Never make the Activity transition wait for that disk/crypto work.
            val code = VerificationCode.create(LocalIdentity(this).deviceInfo().fingerprint, remoteFingerprint)
            val iconTypeface = Typeface.createFromAsset(assets, "MaterialIcons-Regular.ttf")
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                textCode.text = code.text
                code.iconNames.forEach { name ->
                    iconGrid.addView(createIconView(name, iconTypeface))
                }
                findViewById<View>(R.id.verification_loading).visibility = View.GONE
                mode.isEnabled = true
            }
        }.start()
    }

    private fun createIconView(name: String, iconTypeface: Typeface): TextView = TextView(this).apply {
        layoutParams = GridLayout.LayoutParams().apply { width = 52.dp; height = 52.dp; setMargins(4.dp, 4.dp, 4.dp, 4.dp) }
        gravity = android.view.Gravity.CENTER
        contentDescription = name
        text = name
        typeface = iconTypeface
        setFontFeatureSettings("liga")
        textSize = 32f
        setTextColor(resources.getColor(R.color.primary_text))
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_FINGERPRINT = "fingerprint" }
}
