package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.P2PSecureChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("MeshDrop", appName)
  }

  @Test
  fun `test 4 digit pin calculation and crypto encrypt decrypt`() {
    val channel = P2PSecureChannel()
    val testBytes = "TestPeerKey123".toByteArray()
    val pin = channel.compute4DigitPin(testBytes)
    assertEquals(4, pin.length)
    assert(pin.all { it.isDigit() })

    val plainText = "Hello MeshDrop P2P Zero-Router".toByteArray()
    val cipherText = channel.encrypt(plainText)
    assertNotNull(cipherText)
    val decrypted = channel.decrypt(cipherText)
    assertEquals("Hello MeshDrop P2P Zero-Router", String(decrypted))
  }

  @Test
  fun `test wifi aware manager fallback and connection state`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val manager = com.example.data.WifiAwareMeshManager(context)
    manager.startDiscoveryAndPublish("TestDevice")
    assertNotNull(manager.connectionState.value)
    val state = manager.connectionState.value
    assert(state is com.example.data.WifiAwareMeshManager.ConnectionState.Error)
    assert((state as com.example.data.WifiAwareMeshManager.ConnectionState.Error).isPermissionError)

    manager.forceSimulationMode("TestDevice")
    assertEquals(com.example.data.WifiAwareMeshManager.ConnectionState.Discovering, manager.connectionState.value)
  }

  @Test
  fun `test main view model permission and simulation actions`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.MainViewModel(application)
    assertNotNull(viewModel.connectionState.value)
    viewModel.startSimulationMode()
    assertEquals(com.example.data.WifiAwareMeshManager.ConnectionState.Discovering, viewModel.connectionState.value)
  }

  @Test
  fun `test pin numeric comparison and auto-verification`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.MainViewModel(application)
    assertEquals(false, viewModel.isPinVerified.value)

    // Test 1-tap numeric comparison confirm
    viewModel.confirmPinMatch()
    assertEquals(true, viewModel.isPinVerified.value)

    // Test disconnect resets verification
    viewModel.disconnect()
    assertEquals(false, viewModel.isPinVerified.value)

    // Test verifyPin
    val matches = viewModel.verifyPin("1234", "1234")
    assertEquals(true, matches)
    assertEquals(true, viewModel.isPinVerified.value)
  }
}

