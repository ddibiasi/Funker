package at.dibiasi.funker.rfcomm

import android.bluetooth.BluetoothDevice
import android.util.Log
import at.dibiasi.funker.common.SerialPortServiceClass
import at.dibiasi.funker.common.BluetoothConnection
import at.dibiasi.funker.error.BluetoothSocketException
import at.dibiasi.funker.error.BluetoothStreamException
import io.reactivex.Completable
import io.reactivex.Observable
import java.util.*

private const val TAG = "### RxSpp"

/**
 * The RxSpp class enables easy communication via rfcomm.
 * It is heavily substituted by Rx and threads
 * @param device Bluetooth device you want to talk to
 */
class RxSpp(val device: BluetoothDevice) : BluetoothConnection(device) {

    /**
     * Serial communication rfcomm uuid
     */
    private val sppUuid: UUID = UUID.fromString(SerialPortServiceClass)


    private var closingConnectionFlag = false


    /**
     * Cancels running discovery and tries to connect to the device.
     * @param retries
     */
    fun connect(retries: Int = 5): Completable {
        return Completable.create { source ->
            try {
                connect(uuid = sppUuid, retries = retries)
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
            val inputReader = inputStream.bufferedReader()
            while (bluetoothSocket.isConnected) {
                try {
                    Log.d(TAG, "Waiting for reply")
                    val answer = inputReader.readLine()
                    Log.d(TAG, "Received : $answer")
                    emitter.onNext(answer)
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