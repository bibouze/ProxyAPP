package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.ProxyLogEntity
import com.example.ui.screens.LogItemRow
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val dummyLog = ProxyLogEntity(
        id = 1,
        timestamp = 1781023854000L, // Fixed time to prevent screenshot mismatch anomalies
        protocol = "SOCKS5",
        clientIp = "192.168.1.15",
        destination = "google.com:443",
        action = "CONNECT",
        status = "RELAI",
        payloadSize = 10485760L // 10 Mo
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        LogItemRow(log = dummyLog)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
