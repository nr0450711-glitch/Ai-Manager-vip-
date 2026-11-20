package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.assistant.PhoneControlExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and firestore_database_id from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("Jarvis VIP", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `phone control telemetry returns valid device stats`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = PhoneControlExecutor(context)
        val telemetry = executor.getDeviceTelemetry()
        assertTrue(telemetry.batteryPercent in 0..100)
        assertTrue(telemetry.storageTotalGb >= 0.0)
    }
}
