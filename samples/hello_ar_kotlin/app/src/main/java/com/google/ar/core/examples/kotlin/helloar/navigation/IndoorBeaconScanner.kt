package com.google.ar.core.examples.kotlin.helloar.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Opt-in foreground BLE scanner. Caller must request runtime permissions and call stop()
 * on Activity pause. Beacon identifiers are platform scan-result addresses, not stable
 * identities for rotating-address beacons. Provision manufacturer/service IDs in production.
 */
class IndoorBeaconScanner(
  private val context: Context,
  private val onReading: (id: String, rssi: Int) -> Unit
) {
  private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
  private var active = false
  private val callback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      val id = result.device.address ?: return
      onReading(id, result.rssi)
    }
  }

  fun hasPermissions(): Boolean {
    val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
  }

  @SuppressLint("MissingPermission")
  fun start(): Boolean {
    if (!hasPermissions()) return false
    val adapter: BluetoothAdapter = manager.adapter ?: return false
    if (!adapter.isEnabled) return false
    val scanner = adapter.bluetoothLeScanner ?: return false
    if (active) return true
    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
    scanner.startScan(null, settings, callback)
    active = true
    return true
  }

  @SuppressLint("MissingPermission")
  fun stop() {
    if (!active || !hasPermissions()) { active = false; return }
    manager.adapter?.bluetoothLeScanner?.stopScan(callback)
    active = false
  }
}
