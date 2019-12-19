package at.dibiasi.funker.obex

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothObexTransport
import android.bluetooth.BluetoothSocket
import android.util.Log
import android.webkit.MimeTypeMap
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import at.dibiasi.funker.common.OBEXFileTransferServiceClass
import at.dibiasi.funker.common.OBEXFolderBrowsing
import at.dibiasi.funker.common.BluetoothConnection
import at.dibiasi.funker.error.BluetoothConnectionException
import at.dibiasi.funker.error.BluetoothSocketException
import io.reactivex.Completable
import io.reactivex.Single
import java.io.*
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*
import javax.obex.*


private const val TAG = "### RxOBEX"

class RxOBEX(val device: BluetoothDevice) : BluetoothConnection(device) {

    private var session: ClientSession? = null
    private val obexFiletransferUUID = UUID.fromString(OBEXFileTransferServiceClass)
    private val folderBrowsingUUID = UUID.fromString(OBEXFolderBrowsing)

    /**
     * Cancels running discovery and tries to connect to the device.
     * @param retries
     */
    fun connect(retries: Int = 5): Completable {
        return Completable.create { source ->
            try {
                connect(uuid = obexFiletransferUUID, retries = retries)
                createSession()
                source.onComplete()
            } catch (e: Exception) {
                source.onError(e)
            }
        }
    }

    private fun createSession() {
        val bluetoothSocket = bluetoothSocket ?: throw BluetoothSocketException()
        session = createSession(bluetoothSocket)
    }

    private fun closeSession() {
        session?.disconnect(null)
        session?.close()
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
        val session = session ?: throw BluetoothConnectionException("Session is null")
        return Completable.create { emitter ->
            var outputStream: OutputStream? = null
            var putOperation: Operation? = null
            try {
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
                Log.d(TAG, "Closing Operation.")
                try {
                    outputStream?.close()
                    putOperation?.close()
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
        val session = session ?: throw BluetoothConnectionException("Session is null")
        return Completable.create { emitter ->
            try {
                val sessionHeaderSet = HeaderSet()
                sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())
                setPathOnSession(path, session)

                val headerSet = HeaderSet()
                headerSet.setHeader(HeaderSet.NAME, name)
                Log.d(TAG, "Deleting file")
                session.delete(headerSet)
            } catch (e: IOException) {
                Log.e(TAG, "Error: ", e)
                emitter.onError(e)
            } finally {
                emitter.onComplete()
            }
        }
    }

    /**
     * TODO Testing
     * Warning: Untested!
     * @param path Subfolder to navigate to. "" shows the root path.
     */
    fun listFiles(path: String): Single<Folderlisting> {
        val session = session ?: throw BluetoothConnectionException("Session is null")
        return Single.create { single ->
            Log.d(TAG, "File listing from directory: $path")
            var getOperation: Operation? = null
            var inputStreamReader: InputStreamReader? = null
            try {
                val sessionHeaderSet = HeaderSet()
                sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())

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
                Log.d(TAG, "Closing connection")
                inputStreamReader?.close()
                getOperation?.close()
            }
        }
    }

    /**
     * TODO Testing
     * Warning: Untested!
     */
    fun getFile(path: String, mimeType: String?): Single<ByteArray> {
        val session = session ?: throw BluetoothConnectionException("Session is null")
        return Single.create { single ->
            var getOperation: Operation? = null
            val inputStreamReader: InputStreamReader? = null
            try {
                val sessionHeaderSet = HeaderSet()
                sessionHeaderSet.setHeader(HeaderSet.TARGET, getTargetBytes())

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
                Log.d(TAG, "Closing connection")
                inputStreamReader?.close()
                getOperation?.close()
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
        closeSession()
        bluetoothSocket?.close()
    }
}
