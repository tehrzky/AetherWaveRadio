package com.aetherwave.radio.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isConnected: Boolean = false
)

class BluetoothHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isAvailable = MutableStateFlow(bluetoothAdapter != null)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceInfo>> = _pairedDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDeviceInfo?>(null)
    val connectedDevice: StateFlow<BluetoothDeviceInfo?> = _connectedDevice.asStateFlow()

    init {
        refreshBluetoothState()
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun refreshBluetoothState() {
        val available = bluetoothAdapter != null
        _isAvailable.value = available
        val enabled = bluetoothAdapter?.isEnabled == true
        _isEnabled.value = enabled

        if (!available || !enabled) {
            _pairedDevices.value = emptyList()
            _connectedDevice.value = null
            return
        }

        val connectedAudioList = getConnectedAudioDevices()

        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasConnectPermission) {
            try {
                val bonded = bluetoothAdapter?.bondedDevices
                if (bonded != null) {
                    val list = bonded.map { dev ->
                        val devName = dev.name ?: "Unknown Device"
                        val isConnected = connectedAudioList.any { it.contains(devName, ignoreCase = true) }
                        BluetoothDeviceInfo(
                            name = devName,
                            address = dev.address ?: "",
                            isConnected = isConnected
                        )
                    }
                    _pairedDevices.value = list
                    _connectedDevice.value = list.firstOrNull { it.isConnected }
                        ?: if (connectedAudioList.isNotEmpty()) {
                            BluetoothDeviceInfo(connectedAudioList.first(), "Audio Output", true)
                        } else null
                    return
                }
            } catch (e: Exception) {
                // fall through if permission or security error
            }
        }

        if (connectedAudioList.isNotEmpty()) {
            val dev = BluetoothDeviceInfo(connectedAudioList.first(), "Active Audio Device", true)
            _connectedDevice.value = dev
            _pairedDevices.value = listOf(dev)
        } else {
            _pairedDevices.value = emptyList()
            _connectedDevice.value = null
        }
    }

    fun getConnectedAudioDevices(): List<String> {
        val deviceNames = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (dev in devices) {
                when (dev.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                        val name = dev.productName?.toString() ?: "Bluetooth Audio Output"
                        deviceNames.add(name)
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        deviceNames.add("Wired Headphones / Aux")
                    }
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                        deviceNames.add("Phone Built-in Speaker")
                    }
                }
            }
        }
        return deviceNames.distinct()
    }
}
