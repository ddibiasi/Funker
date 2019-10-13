package at.dibiasi.funker.rfcomm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import at.dibiasi.funker.SerialPortServiceClass
import at.dibiasi.funker.error.BluetoothConnectionException
import at.dibiasi.funker.error.BluetoothSocketException
import at.dibiasi.funker.error.BluetoothStreamException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

private const val TAG = "### RxSpp"

/**
 * The RxSpp class enables easy communication via rfcomm.
 * It is heavily substituted by Rx and threads
 * @param device Bluetooth device you want to talk to
 */
class RxSpp(val device: BluetoothDevice, readBufferSize: Int = 1014) {

    /**
     * Default bluetooth adapter of phone
     */
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    /**
     * Buffer used for reading values from target
     */
    private var readBuffer: ByteArray = ByteArray(readBufferSize)

    /**
     * Serial communication rfcomm uuid
     */
    private val sppUuid: UUID = UUID.fromString(SerialPortServiceClass)

    /**
     * Bluetooth socket to be used in communication
     */
    private var bluetoothSocket: BluetoothSocket? = null

    /**
     * Input stream from bluetoothSocket
     */
    private var inputStream: InputStream? = null

    /**
     * Output stream from bluetoothSocket
     */
    private var outputStream: OutputStream? = null

    private var closingConnectionFlag = false

    /**
     * Cancels running discovery and tries to connect to the device.
     * @param retries How many times should be tried to connect to device. (Low end computer tend to not respond immediately)
     *
     */
    fun connect(retries: Int = 5): Completable {
        return Completable.create { source ->
            if (bluetoothAdapter != null) {
                bluetoothAdapter.cancelDiscovery()
                bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                if (bluetoothSocket == null) {
                    source.onError(Exception("Could not create bluetooth socket"))
                    return@create
                }
                val bluetoothSocket = bluetoothSocket!!
                var count = 0
                while (!bluetoothSocket.isConnected && count < retries && !closingConnectionFlag) {
                    try {
                        bluetoothSocket.connect()
                    } catch (e: IOException) {
                        Log.e(TAG, "Could not connect. ${count + 1} try")
                    }
                    count++
                }
                if (!bluetoothSocket.isConnected) {
                    source.onError(BluetoothConnectionException(tries = retries))
                    return@create
                }
                inputStream = bluetoothSocket.inputStream
                outputStream = bluetoothSocket.outputStream
                source.onComplete()
            } else {
                source.onError(BluetoothConnectionException("Bluetooth adapter is not initialized."))
            }
        }
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected ?: false
    }

    /**
     * Constantly reads from connected socket, emits when something is written
     */
    fun read(): Observable<String> {
        return Observable.create { emitter ->
            val bluetoothSocket = bluetoothSocket ?: throw BluetoothSocketException()
            val inputStream = inputStream ?: throw BluetoothStreamException()
            while (bluetoothSocket.isConnected) {
                try {
                    Log.d(TAG, "Waiting for reply")
                    inputStream.read(readBuffer)
                } catch (e: Exception) {
                    if (closingConnectionFlag) {
                        Log.d(TAG, "Input stream was intentionally disconnected")
                        emitter.onComplete()
                    } else {
                        Log.e(TAG, "Input stream was disconnected")
                        emitter.onError(e)
                    }
                    break
                }
                val answer = String(readBuffer)
                Log.d(TAG, "Received : $readBuffer")
                emitter.onNext(answer)
            }
        }
    }

    /**
     * Wrapper to use command object
     * Simply converts command to string and sends it to device
     */
    fun send(command: SppCommand, enableCr: Boolean = true, enableLf: Boolean = true): Completable {
        return send(command.toString(), enableCr, enableLf)
    }

    /**
     * Sends given string to connected client.
     * @param msg Message to be sent
     * @param enableCr Appends carriage return to msg
     * @param enableLf Appends line feed to msg
     *
     * todo alter observable type to enum
     */
    fun send(msg: String, enableCr: Boolean = true, enableLf: Boolean = true): Completable {
        Log.d(TAG, "Sending $msg")
        var formattedMsg = msg
        if (enableCr) formattedMsg += "\r"
        if (enableLf) formattedMsg += "\n"
        return Completable.create { source ->
            try {
                val outputStream = outputStream ?: throw BluetoothStreamException()
                outputStream.write(formattedMsg.toByteArray())
                Log.d(TAG, "Successfully sent $formattedMsg")
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred when sending data")
                source.onError(e)
            }
            source.onComplete()
        }
    }

    fun disconnect() {
        closingConnectionFlag = true
        bluetoothSocket?.close()
    }
}