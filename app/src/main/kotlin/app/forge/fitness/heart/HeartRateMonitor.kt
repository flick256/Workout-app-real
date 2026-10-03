package app.forge.fitness.heart

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
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.forge.domain.heart.HeartRateParser
import app.forge.domain.heart.HrReading
import app.forge.fitness.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

sealed interface StrapState {
    data object Off : StrapState
    data object NoPermission : StrapState
    data object BluetoothOff : StrapState
    data class Connecting(val name: String?) : StrapState
    data class Live(val name: String?, val bpm: Int, val contact: Boolean?, val atMillis: Long) : StrapState
    /** Lost the connection; trying again in the background. */
    data class Reconnecting(val name: String?) : StrapState
    data class Failed(val message: String) : StrapState
}

data class FoundStrap(val address: String, val name: String, val rssi: Int)

/**
 * Talks to any heart-rate strap or watch that broadcasts the standard Bluetooth Heart
 * Rate service (the Amazfit Helio Strap does, with "Heart Rate Push" on in Zepp). No
 * pairing needed; Forge only listens.
 */
@Singleton
class HeartRateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val _state = MutableStateFlow<StrapState>(StrapState.Off)
    val state: StateFlow<StrapState> = _state.asStateFlow()
    private val _readings = MutableSharedFlow<HrReading>(extraBufferCapacity = 64)
    val readings: SharedFlow<HrReading> = _readings.asSharedFlow()

    // Connection state is only touched on the main thread: Bluetooth callbacks arrive on
    // binder threads and are posted over, so connects, disconnects and retries can't race.
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var wanted: Pair<String, String?>? = null
    private var retry: Job? = null
    private var watchdog: Job? = null
    @Volatile private var lastActivityAt = 0L

    init {
        // Bluetooth switched back on mid-workout: pick the strap up again.
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val (address, name) = wanted ?: return
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_ON -> connect(address, name)
                        BluetoothAdapter.STATE_OFF -> { closeGatt(); _state.value = StrapState.BluetoothOff }
                    }
                }
            },
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Connecting, connected or retrying by itself: nothing for anyone else to do. */
    val isHealthy: Boolean
        get() = when (_state.value) {
            is StrapState.Live, is StrapState.Connecting, is StrapState.Reconnecting -> true
            else -> false
        }

    /** Permissions needed to find and connect to a strap on this Android version. */
    val permissions: Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasPermission(): Boolean = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    /** Nearby straps and watches broadcasting heart rate, strongest signal first. */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<List<FoundStrap>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !hasPermission()) {
            close(IllegalStateException(if (!hasPermission()) "Allow Nearby devices first" else "Bluetooth is off"))
            return@callbackFlow
        }
        val found = linkedMapOf<String, FoundStrap>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull() ?: "Heart-rate sensor"
                found[device.address] = FoundStrap(device.address, name, result.rssi)
                trySend(found.values.sortedByDescending { it.rssi })
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Bluetooth scan failed (code $errorCode)"))
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /** Connects (and keeps reconnecting if the strap drops out) until [disconnect]. Any thread. */
    fun connect(address: String, name: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { connect(address, name) }; return }
        wanted = address to name
        retry?.cancel()
        when {
            !hasPermission() -> { _state.value = StrapState.NoPermission; return }
            !isBluetoothOn() -> { _state.value = StrapState.BluetoothOff; return }
        }
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            ?: run { _state.value = StrapState.Failed("That strap's address isn't valid"); return }
        if (_state.value !is StrapState.Live) _state.value = StrapState.Connecting(name)
        closeGatt()
        lastActivityAt = System.currentTimeMillis()
        @SuppressLint("MissingPermission")
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        gatt = g
        startWatchdog()
    }

    /** Any thread. */
    fun disconnect() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { disconnect() }; return }
        wanted = null
        retry?.cancel()
        watchdog?.cancel()
        closeGatt()
        _state.value = StrapState.Off
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
        gatt = null
    }

    private fun scheduleReconnect() {
        val (address, name) = wanted ?: return
        closeGatt()
        _state.value = StrapState.Reconnecting(name)
        retry?.cancel()
        retry = scope.launch(Dispatchers.Main) {
            delay(RECONNECT_MS)
            if (wanted != null) connect(address, name)
        }
    }

    /** Connected but silent (a failed subscribe, a strap that stopped sending): start again. */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch(Dispatchers.Main) {
            while (wanted != null) {
                delay(WATCHDOG_CHECK_MS)
                val quiet = System.currentTimeMillis() - lastActivityAt > SILENT_MS
                val waiting = _state.value is StrapState.Live || _state.value is StrapState.Connecting
                if (quiet && waiting && gatt != null) scheduleReconnect()
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                // A late callback from a connection we've already replaced or closed.
                if (g !== gatt) { runCatching { g.close() }; return@post }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        lastActivityAt = System.currentTimeMillis()
                        @SuppressLint("MissingPermission")
                        val ok = g.discoverServices()
                        if (!ok) scheduleReconnect()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (wanted != null) scheduleReconnect() else closeGatt()
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g !== gatt) return@post
                // A failed discovery is usually temporary: reconnect rather than give up.
                if (status != BluetoothGatt.GATT_SUCCESS) { scheduleReconnect(); return@post }
                val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
                if (characteristic == null) {
                    _state.value = StrapState.Failed(
                        "This device isn't sharing heart rate. On the Helio Strap, turn on Heart Rate Push in Zepp.",
                    )
                    return@post
                }
                if (!subscribe(g, characteristic)) scheduleReconnect()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post { if (g === gatt && status != BluetoothGatt.GATT_SUCCESS) scheduleReconnect() }
        }

        // Android 13+.
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handle(g, characteristic, value)
        }

        // Android 12 and older.
        @Deprecated("Replaced by the ByteArray overload on Android 13+")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) handle(g, characteristic, characteristic.value?.copyOf() ?: return)
        }
    }

    /** Turns on heart-rate notifications. False if Android refused, so the caller can retry. */
    @SuppressLint("MissingPermission")
    private fun subscribe(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(characteristic, true)) return false
        val cccd = characteristic.getDescriptor(CCCD) ?: return false
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, enable) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = enable
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    private fun handle(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != HR_MEASUREMENT) return
        val reading = HeartRateParser.parse(value) ?: return
        main.post {
            if (g !== gatt) return@post
            lastActivityAt = System.currentTimeMillis()
            _readings.tryEmit(reading)
            _state.value = StrapState.Live(wanted?.second, reading.bpm, reading.contact, lastActivityAt)
        }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val RECONNECT_MS = 3_000L
        private const val WATCHDOG_CHECK_MS = 5_000L
        /** Straps send about once a second; this long with nothing means it's stuck. */
        private const val SILENT_MS = 15_000L
    }
}
