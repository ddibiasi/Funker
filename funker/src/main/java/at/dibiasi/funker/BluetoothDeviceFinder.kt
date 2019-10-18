package at.dibiasi.funker

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
import kotlin.NoSuchElementException

private const val TAG = "## BluetoothHelper"

class BluetoothDeviceFinder(private val context: Context) {

    companion object {
        private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        /**
         * Searches bonded devices and doesnt look for new ones
         * is NOT async
         * @return Bluetoothdevice
         */
        fun quickSearchSync(address: String): BluetoothDevice? {
            Log.d(TAG, "Starting search")
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            return try {
                pairedDevices?.first { it.address == address }
            } catch (e: NoSuchElementException) {
                Log.e(TAG, "No previously paired device found")
                null
            }
        }
    }

    private var addressToBeSearchedFor: String = ""
    private var indefiniteSearch: Boolean = false
    private var emitter: ObservableEmitter<BluetoothDevice>? = null

    /**
     * Searches for specified bluetooth device
     * This method is threaded and therefor async
     * @return Emits when ever a device is found
     */
    fun search(
        address: String = "",
        checkPaired: Boolean = false,
        indefinite: Boolean = false
    ): Observable<BluetoothDevice> {
        Log.d(TAG, "Starting search")
        return Observable.create {
            emitter = it
            indefiniteSearch = indefinite
            Log.d(TAG, "Setting up helper")
            checkAdapter()
            addressToBeSearchedFor = address
            registerReceiver()
            if (checkPaired) checkPairedDevices()
            startDiscovery()
        }
    }

    private fun checkAdapter() {
        if (bluetoothAdapter == null) { // is bluetooth available on this device
            emitter?.onError(NoBluetoothException())
        }

        if (bluetoothAdapter?.isEnabled != true) { // enable bluetooth if not enabled
            Log.e(TAG, "Bluetooth is disabled")
            emitter?.onError(BluetoothDisabledException())
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(deviceReceiver, filter)
    }

    /**
     * Glue method, searches for new devices
     */
    private fun startDiscovery() {
        bluetoothAdapter?.let { btAdapter ->
            Log.d(TAG, "Starting bluetooth discovery")
            if (btAdapter.isDiscovering) {
                btAdapter.cancelDiscovery()
            }
            btAdapter.startDiscovery()
        }
    }

    fun stopSearch() {
        Log.d(TAG, "Cancel bluetooth discovery")
        indefiniteSearch = false
        bluetoothAdapter?.cancelDiscovery()
        emitter?.onComplete()
        try {
            context.unregisterReceiver(deviceReceiver)
        } catch (ex: IllegalArgumentException) {
            Log.d(TAG, "Broadcast deviceReceiver already unregistered")
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
        val evaluation: Boolean = if (addressToBeSearchedFor.isEmpty()) {
            true // All devices shall be emited
        } else {
            device.address == addressToBeSearchedFor // only device with matching address
        }
        if (evaluation) {
            Log.d(TAG, "Found match: ${device.name} , ${device.address}")
            emitter?.onNext(device)
            if (addressToBeSearchedFor.isNotEmpty()) stopSearch()
        }
    }

    /**
     *  BroadcastReceiver for ACTION_FOUND. Needs to be public.
     */
    val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let { action ->
                when (action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        Log.d(TAG, "Found device: ${device.name} , ${device.address}")
                        emitDevice(device)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Finished discovery")
                        if (indefiniteSearch){
                            startDiscovery()
                        }else{
                            stopSearch()
                        }
                    }
                    else -> {
                        Log.d(TAG, action)
                    }
                }
            }
        }
    }
}