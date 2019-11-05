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
import at.dibiasi.funker.common.BluetoothConnection
import at.dibiasi.funker.error.BluetoothSocketException
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

class RxOBEX(val device: BluetoothDevice) : BluetoothConnection(device) {

    private val obexFiletransferUUID = UUID.fromString(OBEXFileTransferServiceClass)
    private val folderBrowsingUUID = UUID.fromString(OBEXFolderBrowsing)

    /**
     * Cancels running discovery and tries to connect to the device.
     */
    fun connect(): Completable {
        return Completable.create { source ->
            try {
                connect(obexFiletransferUUID)
                source.onComplete()
            } catch (e: Exception) {
                source.onError(e)
            }
        }
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
        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "text/plain"
        val bytes = file.readBytes()
        return putFile(file.name, mimeType, bytes, path)
    }

    /**
     * TODO Testing
     * Warning: Untested!
     * @param name Name of file
     * @param mimeType MimeType of file (eg. text/plain)
     * @param filebytes Byte content of file
     * @param path Path to file on server
     */
    fun putFile(
        name: String,
        mimeType: String,
        filebytes: ByteArray,
        path: String = ""
    ): Completable {
        return Completable.create { emitter ->
            val bluetoothSocket = bluetoothSocket ?: throw BluetoothSocketException()
            var session: ClientSession? = null
            var putOperation: Operation? = null
            try {
                session = createSession(bluetoothSocket)
                setPathOnSession(path, session)

                val putHeaderSet = HeaderSet()
                putHeaderSet.setHeader(HeaderSet.NAME, name)
                putHeaderSet.setHeader(HeaderSet.TYPE, mimeType)
                putHeaderSet.setHeader(HeaderSet.LENGTH, filebytes.size.toLong())
                Log.d(TAG, "Sending file")
                putOperation = session.put(putHeaderSet)

                val outputStream = putOperation.openOutputStream()
                outputStream.write(filebytes)
                outputStream.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Error: ", e)
                emitter.onError(e)
            } finally {
                Log.d(TAG, "Closing Operation.")
                try {
                    putOperation?.close()
                    session?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't close streams", e)
                } finally {
                    emitter.onComplete()
                }
            }
        }
    }

    /**
     * TODO Testing
     * Warning: Untested!
     * @param name Name of file
     * @param path Path to file on server
     */
    fun deleteFile(name: String, path: String = ""): Completable {
        return Completable.create { emitter ->
            var session: ClientSession? = null
            val bluetoothSocket = bluetoothSocket ?: throw BluetoothSocketException()
            try {
                val sessionHeaderSet = HeaderSet()
                sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())
                session = createSession(bluetoothSocket)
                setPathOnSession(path, session)

                val headerSet = HeaderSet()
                headerSet.setHeader(HeaderSet.NAME, name)
                Log.d(TAG, "Deleting file")
                session.delete(headerSet)
            } catch (e: IOException) {
                Log.e(TAG, "Error: ", e)
                emitter.onError(e)
            } finally {
                Log.d(TAG, "Closing connection.")
                try {
                    session?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't close streams", e)
                } finally {
                    emitter.onComplete()
                }
            }

        }
    }


    /**
     * TODO Testing
     * Warning: Untested!
     * @param path Subfolder to navigate to. "" shows the root path.
     */
    fun listFiles(path: String): Single<Folderlisting> {
        return Single.create { single ->
            Log.d(TAG, "File listing from directory: $path")
            var getOperation: Operation? = null
            var inputStreamReader: InputStreamReader? = null
            var session: ClientSession? = null
            val bluetoothSocket = bluetoothSocket ?: throw BluetoothSocketException()
            try {
                val sessionHeaderSet = HeaderSet()
                sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())
                session = createSession(bluetoothSocket)

                setPathOnSession(path, session)

                val requestHeaderSet = HeaderSet()
                requestHeaderSet.setHeader(HeaderSet.TYPE, FOLDER_LISTING)
                Log.d(TAG, "Sending request")
                getOperation = session.get(requestHeaderSet)

                inputStreamReader = InputStreamReader(getOperation.openInputStream(), "UTF-8")

                val xmlResponse = inputStreamReader.readText()

                val mapper = XmlMapper()
                val listing =
                    mapper.readValue<Folderlisting>(xmlResponse, Folderlisting::class.java)
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

    /**
     * TODO Testing
     * Warning: Untested!
     */
    fun getFile(path: String, mimeType: String?): Single<ByteArray> {
        return Single.create { single ->
            var getOperation: Operation? = null
            val inputStreamReader: InputStreamReader? = null
            var session: ClientSession? = null
            val bluetoothSocket = bluetoothSocket ?: throw BluetoothSocketException()
            try {
                val sessionHeaderSet = HeaderSet()
                sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())
                session = createSession(bluetoothSocket)

                setPathOnSession(path, session)

                val requestHeaderSet = HeaderSet()
                if (mimeType != null) {
                    requestHeaderSet.setHeader(HeaderSet.TYPE, mimeType)
                }
                Log.d(TAG, "Sending request")
                getOperation = session.get(requestHeaderSet)

                val file = getOperation.openInputStream().readBytes()
                single.onSuccess(file)
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

    /**
     * Sets the operating folder of the supplied session to specified path.
     * The path has to be divided with /
     */
    private fun setPathOnSession(
        path: String,
        session: ClientSession,
        createFolder: Boolean = false
    ) {
        val header = HeaderSet()
        for (folder in path.split('/')) {
            header.setHeader(HeaderSet.NAME, folder) // setpath
            session.setPath(header, false, createFolder)
        }
    }

    @Throws(IOException::class)
    private fun createSession(socket: BluetoothSocket): ClientSession {
        Log.d(TAG, "Creating session")
        val sessionHeaderSet = HeaderSet()
        sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())
        val session = ClientSession((BluetoothObexTransport(socket)) as ObexTransport)
        val responseHeaderset = session.connect(sessionHeaderSet)
        val responseCode = responseHeaderset.getResponseCode()
        if (responseCode != ResponseCodes.OBEX_HTTP_OK) {
            session.disconnect(responseHeaderset)
            throw IOException("Remote refused. Response code: $responseCode")
        }
        Log.d(TAG, "Session created")
        return session
    }

    fun disconnect() {

        bluetoothSocket?.close()
    }
}
