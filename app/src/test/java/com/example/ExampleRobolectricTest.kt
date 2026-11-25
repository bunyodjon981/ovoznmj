package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.agent.PhoneCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Telefonim AI", appName)
  }

  @Test
  fun `extract uzbek spoken phone number`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val caps = PhoneCapabilities(context)
    val parsed = caps.extractPhoneNumberOrNull("to'qson bir bir ikki uch to'rt besh olti yetti")
    assertEquals("911234567", parsed)
  }
}
