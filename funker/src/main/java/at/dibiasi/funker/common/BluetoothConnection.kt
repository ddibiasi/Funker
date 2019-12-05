package at.dibiasi.funker.common

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import at.dibiasi.funker.error.BluetoothConnectionException
import io.reactivex.Completable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val TAG = "### BluetoothConnection"

open class BluetoothConnection(val blDevice: BluetoothDevice) {
    /**
     * Default bluetooth adapter of phone
     */
    val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    /**
     * Bluetooth socket to be used in communication
     */
    var bluetoothSocket: BluetoothSocket? = null

    /**
     * Input stream from bluetoothSocket
     */
    var inputStream: InputStream? = null

    /**
     * Output stream from bluetoothSocket
     */
    var outputStream: OutputStream? = null

    /**
     * Cancels running discovery and tries to connect to the device.
     * @param retries How many times should be tried to connect to device. (Low end computer tend to not respond immediately)
     *
     */
    fun connect(uuid: UUID, retries: Int = 5, timeoutInMillis: Int = 1000) {
        bluetoothAdapter.cancelDiscovery()
        bluetoothSocket = blDevice.createRfcommSocketToServiceRecord(uuid)
        if (bluetoothSocket == null) {
            throw BluetoothConnectionException("Could not create bluetooth socket")
        }
        val bluetoothSocket = bluetoothSocket!!
        var count = 0
        while (!bluetoothSocket.isConnected && count < retries) {
            try {
                bluetoothSocket.connect()
            } catch (e: IOException) {
                Log.e(TAG, "Could not connect. ${count + 1} try. Waiting 1000ms.")
                Thread.sleep(1000)
            }
            count++
        }
        if (!bluetoothSocket.isConnected) {
            throw BluetoothConnectionException(tries = retries)
        }
        inputStream = bluetoothSocket.inputStream
        outputStream = bluetoothSocket.outputStream
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected ?: false
    }
}