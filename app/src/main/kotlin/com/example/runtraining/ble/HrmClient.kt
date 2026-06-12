package com.example.runtraining.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.runtraining.util.Log
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * BLE Heart Rate Monitor client. Scans for HR-service-advertising devices,
 * connects to one via GATT, subscribes to Heart Rate Measurement
 * notifications (0x2A37), and emits parsed [HrSample]s.
 *
 * Permissions are the caller's responsibility — we just bail with
 * `ConnectionState.PermissionMissing` if BLUETOOTH_CONNECT / BLUETOOTH_SCAN
 * are not granted (Spec FR-033 / US3 AS6).
 */
class HrmClient(private val context: Context) {

    enum class ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        PERMISSION_MISSING,
        BLUETOOTH_OFF,
    }

    /** Discovery payload exposed to the Options UI. */
    data class DiscoveredDevice(
        val deviceId: String,    // MAC address — stable per pairing record
        val name: String?,       // null if device hasn't advertised a name
        val rssi: Int,
    )

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _samples = MutableStateFlow<HrSample?>(null)
    /** Latest sample, or `null` if no live connection. UI/engine should clear after ~3 s of silence. */
    val samples: StateFlow<HrSample?> = _samples.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var connectedDeviceId: String? = null

    fun hasScanPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasConnectPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED

    /** Cold flow of discoveries. Cancel the collector to stop scanning. */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val a = adapter
        if (a == null || !a.isEnabled) {
            _state.value = ConnectionState.BLUETOOTH_OFF
            close()
            return@callbackFlow
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            _state.value = ConnectionState.PERMISSION_MISSING
            close()
            return@callbackFlow
        }
        val scanner = a.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        _state.value = ConnectionState.SCANNING
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(HR_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = runCatching { device.name }.getOrNull()
                trySend(
                    DiscoveredDevice(
                        deviceId = device.address ?: return,
                        name = name,
                        rssi = result.rssi,
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d("HrmClient scan failed: $errorCode")
                close()
            }
        }

        scanner.startScan(listOf(filter), settings, callback)
        awaitClose {
            runCatching { scanner.stopScan(callback) }
            if (_state.value == ConnectionState.SCANNING) {
                _state.value = if (connectedDeviceId != null) {
                    ConnectionState.CONNECTED
                } else {
                    ConnectionState.DISCONNECTED
                }
            }
        }
    }

    /** Connect to a device by MAC. Idempotent — disconnects any current GATT first. */
    @SuppressLint("MissingPermission")
    fun connect(deviceId: String) {
        val a = adapter
        if (a == null || !a.isEnabled) {
            _state.value = ConnectionState.BLUETOOTH_OFF
            return
        }
        if (!hasConnectPermission()) {
            _state.value = ConnectionState.PERMISSION_MISSING
            return
        }
        disconnect()
        val device = runCatching { a.getRemoteDevice(deviceId) }.getOrNull() ?: return
        connectedDeviceId = deviceId
        _state.value = ConnectionState.CONNECTING
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.connectGatt(context, /* autoConnect = */ true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, /* autoConnect = */ true, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        connectedDeviceId = null
        _samples.value = null
        if (_state.value != ConnectionState.BLUETOOTH_OFF &&
            _state.value != ConnectionState.PERMISSION_MISSING
        ) {
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    fun shutdown() {
        disconnect()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runCatching { g.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _samples.value = null
                    if (_state.value != ConnectionState.BLUETOOTH_OFF &&
                        _state.value != ConnectionState.PERMISSION_MISSING
                    ) {
                        // Stay in CONNECTING so autoConnect=true can re-attach.
                        _state.value = ConnectionState.CONNECTING
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(UUID.fromString(HR_SERVICE_UUID)) ?: return
            val chr = service.getCharacteristic(UUID.fromString(HR_MEASUREMENT_CHR_UUID)) ?: return
            g.setCharacteristicNotification(chr, true)
            val ccc = chr.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID)) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(ccc)
            }
            _state.value = ConnectionState.CONNECTED
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            parseAndEmit(value)
        }

        @Deprecated("API < 33 fallback", level = DeprecationLevel.HIDDEN)
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            parseAndEmit(value)
        }
    }

    private fun parseAndEmit(value: ByteArray) {
        val bpm = parseHeartRateMeasurement(value) ?: return
        _samples.value = HrSample(
            bpm = bpm,
            monotonicTimestampMs = SystemClock.elapsedRealtime(),
        )
    }

    companion object {
        // Bluetooth SIG-assigned UUIDs.
        const val HR_SERVICE_UUID = "0000180D-0000-1000-8000-00805F9B34FB"
        const val HR_MEASUREMENT_CHR_UUID = "00002A37-0000-1000-8000-00805F9B34FB"
        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

        /**
         * Parse a Heart Rate Measurement characteristic payload per the SIG
         * spec. The first byte is a flags byte; bit 0 selects between UINT8
         * (BPM in byte 1) and UINT16 (little-endian BPM in bytes 1..2).
         * Returns null on malformed input.
         */
        fun parseHeartRateMeasurement(value: ByteArray): Int? {
            if (value.isEmpty()) return null
            val flags = value[0].toInt()
            val isUint16 = (flags and 0x01) == 0x01
            return if (isUint16) {
                if (value.size < 3) null
                else ((value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8))
            } else {
                if (value.size < 2) null
                else value[1].toInt() and 0xFF
            }.takeIf { it != null && it in 1..300 }
        }
    }
}
