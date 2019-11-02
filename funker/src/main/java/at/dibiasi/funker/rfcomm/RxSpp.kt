package at.dibiasi.funker.rfcomm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import at.dibiasi.funker.SerialPortServiceClass
import at.dibiasi.funker.common.BluetoothConnection
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
class RxSpp(val device: BluetoothDevice, readBufferSize: Int = 1014) : BluetoothConnection(device) {
    /**
     * Buffer used for reading values from target
     */
    private var readBuffer: ByteArray = ByteArray(readBufferSize)

    /**
     * Serial communication rfcomm uuid
     */
    private val sppUuid: UUID = UUID.fromString(SerialPortServiceClass)


    private var closingConnectionFlag = false


    /**
     * Cancels running discovery and tries to connect to the device.
     */
    fun connect(): Completable {
        return Completable.create { source ->
            try {
                connect(sppUuid)
                source.onComplete()
            } catch (e: java.lang.Exception) {
                source.onError(e)
            }
        }
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
     * Sends given string to connected client.
     * @param msg Message to be sent
     * @param enableCr Appends carriage return to msg
     * @param enableLf Appends line feed to msg
     *
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