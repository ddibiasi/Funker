package at.ddibiasi.funker

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import at.dibiasi.funker.error.BluetoothDisabledException
import at.dibiasi.funker.error.NoBluetoothException
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.timerTask

private const val TAG = "## BluetoothHelper"

class BluetoothDeviceFinder(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    var discoveryDuration: Long = 200000 //ms
    private var deviceToBeSearchedFor = ""
    private var emitter: ObservableEmitter<BluetoothDevice>? = null

    /**
     * Searches for specified at.ddibiasi.funker.android.bluetooth device
     * This method is threaded and therefor async
     * @return Emits when ever a device is found
     */
    fun search(deviceName: String): Observable<BluetoothDevice> {
        return Observable.create {
            val searchThread = thread {
                emitter = it
                Log.d(TAG, "Setting up helper")
                if (bluetoothAdapter == null) { // is at.ddibiasi.funker.android.bluetooth available on this device
                    emitter?.onError(NoBluetoothException())
                    return@thread
                }
                if (!bluetoothAdapter.isEnabled) { // enable at.ddibiasi.funker.android.bluetooth if not enabled
                    Log.e(TAG, "Bluetooth is disabled")
                    emitter?.onError(BluetoothDisabledException())
                    return@thread
                }
                deviceToBeSearchedFor = deviceName
                handleDiscovery()
            }
            searchThread.interrupt()
        }
    }

    /**
     * Glue method, searches previously paired devices and looks for new ones.
     */
    private fun handleDiscovery() {
        checkPairedDevices()
        bluetoothAdapter?.let { btAdapter ->
            Log.d(TAG, "Starting at.ddibiasi.funker.android.bluetooth discovery")
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
            btAdapter.startDiscovery()
            // Stop discovery in x seconds
            Timer().schedule(timerTask {
                stopSearch()
            }, discoveryDuration)
        }
    }

    fun stopSearch() {
        Log.d(TAG, "Cancel at.ddibiasi.funker.android.bluetooth discovery")
        bluetoothAdapter?.cancelDiscovery()
        emitter?.onComplete()
        try {
            context.unregisterReceiver(receiver)
        } catch (ex: IllegalArgumentException) {
            Log.d(TAG, "Broadcast receiver already unregistered")
        }
    }

    /**
     * Checks previously paired devices
     */
    private fun checkPairedDevices() {
        Log.d(TAG, "Checking already paired devices")
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            emitDevice(device)
        }
    }

    /**
     * Emits device to the initialized observable
     */
    private fun emitDevice(device: BluetoothDevice) {
        val evaluation: Boolean = if (device.name == null) {
            deviceToBeSearchedFor == ""
        } else {
            device.name.contains(deviceToBeSearchedFor)
        }
        if (evaluation) {
            Log.d(TAG, "Found device device: ${device.name} , ${device.address}")
            emitter?.onNext(device)
        }
    }

    /**
     *  BroadcastReceiver for ACTION_FOUND. Needs to be public.
     */
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(TAG, "Found device: ${device.name} , ${device.address}")
                    emitDevice(device)
                }
            }
        }
    }
}