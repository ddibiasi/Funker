package nl.dibiasi.funkertest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import at.dibiasi.funker.obex.RxOBEX
import at.dibiasi.funker.rfcomm.RxSpp
import at.dibiasi.funker.utils.*
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import com.uber.autodispose.autoDisposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException

private const val TAG = "### MainActivity"
private const val MAX_RETRIES = 5L

class MainActivity : AppCompatActivity() {

    private val scopeProvider by lazy { AndroidLifecycleScopeProvider.from(this) }

    var rxSpp: RxSpp? = null
    var rxOBEX: RxOBEX? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val finder = BluetoothDeviceFinder(applicationContext)
        startBluetoothDeviceSearch(finder)

    }

    @SuppressLint("CheckResult")
    private fun startBluetoothDeviceSearch(finder: BluetoothDeviceFinder) {
        finder
            .search(checkPaired = true, indefinite = true)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .distinct()
            .filter {
                it.name?.contains("rubberduck", ignoreCase = true) ?: false
            }
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onNext = { device ->
                    Log.d(TAG, "Found device: ${device.name}")
                },
                onError = {
                    Log.e(TAG, "Error occoured")
                    it.printStackTrace()
                },
                onComplete = { Log.d(TAG, "Completed search") }
            )
    }

    @SuppressLint("CheckResult")
    fun connectToDevice(device:BluetoothDevice){
        rxSpp = RxSpp(device)
        rxSpp!!
            .connect()
            .observeOn(Schedulers.io())
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onError = { Log.e(TAG, "Received an error", it) },
                onComplete = {Log.d(TAG, "Connected to device")}

            )
    }

    @SuppressLint("CheckResult")
    fun readRfcomm(){
        val rxSpp = rxSpp ?: return // Make sure variable is not null
        rxSpp.read()
            .observeOn(Schedulers.newThread())
            .subscribeOn(Schedulers.newThread())
            .retryConditional(
                predicate = { it is IOException },
                maxRetry = 5,
                delayBeforeRetryInMillis = 100
            )
            .autoDisposable(scopeProvider)
            .subscribeBy (
                onNext = {
                    Log.d(TAG, "Received: $it")
                },
                onError = { Log.e(TAG, "Received an error", it) },
                onComplete = { Log.d(TAG, "Read completed")}
            )
    }

    @SuppressLint("CheckResult")
    fun sendRfcommCommand(command: String) {
        val rxSpp = rxSpp ?: return // Make sure variable is not null
        rxSpp
            .send(command)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .retry(MAX_RETRIES)
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onComplete = {
                    Log.d(TAG, "Succesfully sent $command to device")
                },
                onError = { e ->
                    Log.e(TAG, "Received error!")
                })
    }

    @SuppressLint("CheckResult")
    fun sendFile() {
        val rxOBEX = rxOBEX ?: return // Make sure variable is not null
        rxOBEX
            .putFile("rubberduck.txt", "text/plain", "oh hi mark".toByteArray(), "test")
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onComplete = {
                    Log.d(TAG, "Succesfully sent a testfile to device")
                },
                onError = { e ->
                    Log.e(TAG, "Received error!")
                })
    }

    @SuppressLint("CheckResult")
    fun deleteFile() {
        val rxOBEX = rxOBEX ?: return // Make sure variable is not null
        rxOBEX
            .deleteFile("rubberduck.txt", "test")
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onComplete = {
                    Log.d(TAG, "Succesfully deleted the file")
                },
                onError = { e ->
                    Log.e(TAG, "Received error!")
                })
    }

    @SuppressLint("CheckResult")
    fun listFilesInDirectory() {
        val rxOBEX = rxOBEX ?: return // Make sure variable is not null
        rxOBEX
            .listFiles("test") // List files in /test
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onSuccess = {
                    folderlisting ->
                    Log.d(TAG, "Retrieved folderlisting")
                    Log.d(TAG, folderlisting.toString())
                },
                onError = { e ->
                    Log.e(TAG, "Received error!")
                })
    }

}
