package at.dibiasi.funker.obex

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothObexTransport
import android.bluetooth.BluetoothSocket
import android.util.Log
import android.webkit.MimeTypeMap
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import at.dibiasi.funker.OBEXFileTransferServiceClass
import at.dibiasi.funker.OBEXFolderBrowsing
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import java.io.*
import java.io.File
import java.lang.Exception
import java.net.ConnectException
import java.nio.ByteBuffer
import java.util.*
import javax.obex.*
import kotlin.concurrent.thread


private const val TAG = "### RxOBEX"

/**
 * TODO: fix everytime the device communicates via bl a new socket is created
 * @see http://www.bluecove.org/bluecove/apidocs/javax/javax.obex/ClientSession.html
 */
class RxOBEX(val device: BluetoothDevice) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val obexFiletransferUUID = UUID.fromString(OBEXFileTransferServiceClass)
    private val folderBrowsingUUID = UUID.fromString(OBEXFolderBrowsing)
    private var socket: BluetoothSocket? = null

    private fun createSocketFact(): BluetoothSocket {
        Log.d(TAG, "createSocket")
        if (socket == null || !socket?.isConnected!!) {
            Log.d(TAG, "socket is either null or not connected")
            Log.d(TAG, "recreating")
            bluetoothAdapter?.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(obexFiletransferUUID)
        }
        return socket!!
    }

    private fun createSocket(): BluetoothSocket {
        if(bluetoothAdapter?.isDiscovering == true)  bluetoothAdapter.cancelDiscovery()
        return device.createRfcommSocketToServiceRecord(obexFiletransferUUID)
    }

    /**
     * @return returns somehow important bytes. couldn't find any documentation.
     *
     */
    private fun getTargetBytes(): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(folderBrowsingUUID.mostSignificantBits)
        bb.putLong(folderBrowsingUUID.leastSignificantBits)
        return bb.array()
    }

    /**
     * Wrapper to send file
     */
    fun putFile(file: File, path: String): Completable {
        val mimeType = if (file.extension == "hoss") {
            "text/plain"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        }!!
        val bytes = file.readBytes()
        return putFile(file.name, mimeType, bytes, path)
    }

    fun putTestFile(): Completable {
        return putFile("nice.hoss", "text/plain", "[CLIENT] Test".toByteArray(), "firmware")
    }

    /**
     * @param file to be sent to remote
     */
    fun putFile(name: String, mimeType: String, filebytes: ByteArray, path: String = ""): Completable {
        return Completable.create { emitter ->
            thread {
                var outputStream: OutputStream? = null
                var putOperation: Operation? = null
                var session: ClientSession? = null
                val bluetoothSocket = createSocket()
                try {
                    bluetoothSocket.connect()

                    val sessionHeaderSet = HeaderSet()
                    sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())
                    session = createSession(sessionHeaderSet, bluetoothSocket)
                    setPathOnSession(path, session)

                    val putHeaderSet = HeaderSet()
                    putHeaderSet.setHeader(HeaderSet.NAME, name)
                    putHeaderSet.setHeader(HeaderSet.TYPE, mimeType)
                    putHeaderSet.setHeader(HeaderSet.LENGTH, filebytes.size.toLong())
                    Log.d(TAG, "Sending file")
                    putOperation = session.put(putHeaderSet)

                    outputStream = putOperation.openOutputStream()
                    outputStream.write(filebytes)
                    outputStream.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "Error: ", e)
                    emitter.onError(e)
                } finally {
                    Log.d(TAG, "Closing connection.")
                    try {
                        outputStream?.close()
                        putOperation?.close()
                        session?.close()
                    }catch (e: Exception){
                        Log.e(TAG, "Couldn't close streams", e)
                    }finally {
                        emitter.onComplete()
                    }
                }
            }
        }
    }


    /**
     * @param path Subfolder to navigate to. "" shows the root path.
     */
    fun listFiles(path: String): Single<Folderlisting> {
        return Single.create { single ->
            thread {
                Log.d(TAG, "Started thread to list files from directory: $path")
                var getOperation: Operation? = null
                var inputStreamReader: InputStreamReader? = null
                var session: ClientSession? = null
                val bluetoothSocket = createSocket()
                try {
                    bluetoothSocket.connect()
                    val sessionHeaderSet = HeaderSet()
                    sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())
                    session = createSession(sessionHeaderSet, bluetoothSocket)

                    setPathOnSession(path, session)

                    val requestHeaderSet = HeaderSet()
                    requestHeaderSet.setHeader(HeaderSet.TYPE, FOLDER_LISTING)
                    Log.d(TAG, "Sending request")
                    getOperation = session.get(requestHeaderSet)

                    inputStreamReader = InputStreamReader(getOperation.openInputStream(), "UTF-8")

                    val xmlResponse = inputStreamReader.readText()

                    val mapper = XmlMapper()
                    val listing = mapper.readValue<Folderlisting>(xmlResponse, Folderlisting::class.java)
                    Log.d(TAG, "Loaded all folders from remote")
                    single.onSuccess(listing)
                } catch (e: IOException) {
                    Log.e(TAG, "Error: ", e)
                    single.onError(e)
                } finally {
                    inputStreamReader?.close()
                    getOperation?.close()
                    session?.disconnect(null)
                    Log.d(TAG, "Closing connection")
                }
            }
        }
    }

    /**
     * Sets the operating folder of the supplied session to specified path.
     * The path has to be divided with /
     */
    private fun setPathOnSession(path: String, session: ClientSession, createFolder: Boolean = false) {
        val header = HeaderSet()
        for (folder in path.split('/')) {
            header.setHeader(HeaderSet.NAME, folder) // setpath
            session.setPath(header, false, createFolder)
        }
    }

    @Throws(IOException::class)
    private fun createSession(headers: HeaderSet, socket: BluetoothSocket): ClientSession {
        Log.d(TAG, "Creating session")
        val session = ClientSession((BluetoothObexTransport(socket)) as ObexTransport)
        val responseHeaderset = session.connect(headers)
        val responseCode = responseHeaderset.getResponseCode()
        if (responseCode != ResponseCodes.OBEX_HTTP_OK) {
            session.disconnect(responseHeaderset)
            throw IOException("Remote refused. Response code: $responseCode")
        }
        Log.d(TAG, "Session created")
        return session
    }
}

enum class SendFileState(val id: Int, var message: String = "") {
    DONE(1, "Done"),
    ERROR(2, "An error occurred")
}
