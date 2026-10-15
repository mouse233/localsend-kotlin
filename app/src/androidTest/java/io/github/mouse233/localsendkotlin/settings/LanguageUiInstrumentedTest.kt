package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.app.NotificationManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.SettingsActivity
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.history.ReceiveHistoryText
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.security.VerificationCode
import io.github.mouse233.localsendkotlin.transfer.UploadClient
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.transfer.TransferServiceState
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test

class LanguageUiInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var originalLanguage: String

    @Before
    fun rememberLanguage() {
        originalLanguage = AppSettings(context).language()
    }

    @After
    fun restoreLanguage() {
        AppSettings(context).setLanguage(originalLanguage)
    }

    @Test
    fun runningServiceUpdatesNotificationLanguageWithoutRestarting() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val wasRunning = TransferServiceState.isRunning()
        val connected = CountDownLatch(1)
        lateinit var service: TransferService
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as TransferService.LocalBinder).service()
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use {
            assertTrue(context.bindService(Intent(context, TransferService::class.java), connection, Context.BIND_AUTO_CREATE))
            try {
                assertTrue(connected.await(10, TimeUnit.SECONDS))
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                for ((language, expected) in listOf("en" to "File transfers", "zh" to "文件传输")) {
                    AppSettings(context).setLanguage(language)
                    instrumentation.runOnMainSync {
                        service.onStartCommand(Intent().setAction(TransferService.ACTION_REFRESH_LANGUAGE), 0, 0)
                    }
                    assertTrue(TransferServiceState.isRunning())
                    assertEquals(expected, manager.notificationChannels.first { it.id == "transfer_progress" }.name.toString())
                }
            } finally {
                AppSettings(context).setLanguage(originalLanguage)
                if (connected.count == 0L) instrumentation.runOnMainSync {
                    service.onStartCommand(Intent().setAction(TransferService.ACTION_REFRESH_LANGUAGE), 0, 0)
                }
                context.unbindService(connection)
                if (!wasRunning) context.stopService(Intent(context, TransferService::class.java))
            }
        }
    }

    @Test
    fun multicastAndDeviceDialogsUseAppLanguageOnNarrowWindows() {
        for ((language, cancel) in listOf("en" to "Cancel", "zh" to "取消")) {
            AppSettings(context).setLanguage(language)
            ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use {
                for ((row, name) in listOf(R.id.multicast_address_row to "multicast", R.id.device_info_row to "device")) {
                    onView(withId(row)).perform(scrollTo(), click())
                    onView(withId(android.R.id.button2)).check(matches(withText(cancel)))
                    onView(withId(android.R.id.button2)).perform(narrowDialog())
                    onView(withId(android.R.id.button2)).check(matches(isDisplayed()))
                    saveScreenshot("$language-$name-narrow")
                    onView(withId(android.R.id.button2)).perform(click())
                }
            }
        }
    }

    @Test
    fun backgroundUploadTextUsesCurrentAppLanguage() {
        val client = UploadClient(context, LocalIdentity(context))
        val device = RemoteDevice("Peer", "Android", "mobile", "", "127.0.0.1", 53317, "http", true)
        for ((language, expected) in listOf("en" to "Preparing files (1)…", "zh" to "正在准备 1 个文件…")) {
            AppSettings(context).setLanguage(language)
            val finished = CountDownLatch(1)
            val messages = mutableListOf<String>()
            client.send(listOf(Uri.EMPTY), device, object : UploadClient.Listener {
                override fun onStatus(message: String) { messages += message }
                override fun onError(message: String) { finished.countDown() }
                override fun onPinRequired(device: RemoteDevice, attempt: Int, reply: (String?) -> Unit) = Unit
                override fun onPreparationStarted(sessionId: String, files: List<UploadClient.QueueFile>) = Unit
                override fun onChecksumProgress(sessionId: String, current: Int, total: Int) = Unit
                override fun onSessionPrepared(preparationSessionId: String, sessionId: String, files: List<UploadClient.QueueFile>) = Unit
                override fun onProgress(sessionId: String, fileId: String, fileName: String, fileIndex: Int, fileCount: Int, sent: Long, total: Long, totalSent: Long, totalBytes: Long) = Unit
                override fun onFileCompleted(sessionId: String, fileId: String) = Unit
                override fun onCompleted(names: List<String>) { finished.countDown() }
            })
            assertTrue("Upload preparation did not finish", finished.await(10, TimeUnit.SECONDS))
            assertEquals(expected, messages.first())
            assertEquals(AppLocale.text(context, R.string.upload_preparing_file, 1, 1), messages[1])
        }
    }

    @Test
    fun verificationIconsHaveLocalizedAccessibilityDescriptions() {
        for ((language, firstLabel) in listOf("en" to "Snowflake", "zh" to "雪花")) {
            val labels = AppLocale.wrap(context, language).resources.getStringArray(R.array.verification_icon_labels)
            assertEquals(VerificationCode.ICON_ALPHABET.size, labels.size)
            assertEquals(firstLabel, labels.first())
            val code = VerificationCode.create("A", "B")
            for (name in code.iconNames) assertTrue(labels[VerificationCode.ICON_ALPHABET.indexOf(name)].isNotBlank())
        }
    }

    @Test
    fun unknownHistorySenderUsesDisplayLanguageWithoutChangingAliases() {
        for ((language, expected) in listOf("en" to "Unknown device", "zh" to "未知设备")) {
            val localized = AppLocale.wrap(context, language)
            assertEquals(expected, ReceiveHistoryText.sender(localized, ReceiveHistoryText.LEGACY_UNKNOWN_SENDER))
            assertEquals(expected, ReceiveHistoryText.sender(localized, ""))
            assertEquals("用户的手机", ReceiveHistoryText.sender(localized, "用户的手机"))
        }
    }

    private fun narrowDialog() = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()
        override fun getDescription() = "Check dialog at 280dp width"
        override fun perform(controller: UiController, view: View) {
            val decor = view.rootView
            val parameters = decor.layoutParams as WindowManager.LayoutParams
            parameters.width = (280 * view.resources.displayMetrics.density).toInt()
            val manager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            manager.updateViewLayout(decor, parameters)
            controller.loopMainThreadForAtLeast(250)
            assertEquals("Dialog did not reach the requested narrow width", parameters.width, decor.width)
            val label = view as TextView
            assertTrue("Button text is truncated", (0 until label.lineCount).all { label.layout.getEllipsisCount(it) == 0 })
        }
    }

    private fun saveScreenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "i18n-$name.png")
        file.outputStream().use { output ->
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()).apply {
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
        }
    }
}
