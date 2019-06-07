package at.dibiasi.funker.obex

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import android.webkit.MimeTypeMap
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import at.ddibiasi.funker.OBEXFileTransferServiceClass
import at.ddibiasi.funker.OBEXFolderBrowsing
import at.ddibiasi.funker.android.bluetooth.BluetoothObexTransport
import io.reactivex.Observable
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*
import javax.obex.*
import kotlin.concurrent.thread




private const val TAG = "### RxOBEX"

/**
 * TODO: fix everytime the device communicates via bl a new socket is created
 * @see http://www.bluecove.org/bluecove/apidocs/javax/javax.javax.obex/ClientSession.html
 */
class RxOBEX(val device: BluetoothDevice) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val obexFiletransferUUID = UUID.fromString(OBEXFileTransferServiceClass)
    private val folderBrowsingUUID = UUID.fromString(OBEXFolderBrowsing)


    private fun createSocket(): BluetoothSocket {
        bluetoothAdapter?.cancelDiscovery()
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
    fun putFile(file: File): Observable<String> {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        val bytes = file.readBytes()
        return putFile(file.name, mimeType, bytes)
    }

    fun putTestFile(): Observable<String> {
       return putFile("nice.hoss", "text/plain", "[CLIENT] Test".toByteArray(), "firmware")
    }

    /**
     * @param file to be sent to remote
     */
    fun putFile(name: String, mimeType: String, filebytes: ByteArray, path: String = ""): Observable<String> {
        return Observable.create { emitter ->
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
                    Log.d(TAG, "Sending completed. Closing connection.")
                    outputStream?.close()
                    putOperation?.close()
                    session?.close()
                    bluetoothSocket.close()
                    emitter.onComplete()
                }
            }
        }
    }


    /**
     * @param path Subfolder to navigate to. "" shows the root path.
     */
    fun listFiles(path: String = ""): Observable<Folderlisting> {
        return Observable.create { emitter ->
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
                    emitter.onNext(listing)

                } catch (e: IOException) {
                    Log.e(TAG, "Error: ", e)
                    emitter.onError(e)
                } finally {
                    Log.d(TAG, "Closing connection")
                    inputStreamReader?.close()
                    getOperation?.close()
                    session?.disconnect(null)
                    bluetoothSocket.close()
                    emitter.onComplete()
                }
            }
        }
    }

    /**
     * Sets the operating folder of the supplied session to specified path.
     * The path has to be divided with /
     */
    private fun setPathOnSession(path: String, session: ClientSession, createFolder: Boolean = false){
        val header = HeaderSet()
        for ( folder in path.split('/')){
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