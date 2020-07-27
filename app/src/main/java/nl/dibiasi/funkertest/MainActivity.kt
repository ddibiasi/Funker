package nl.dibiasi.funkertest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.dibiasi.funker.utils.BluetoothDeviceFinder
import at.dibiasi.funker.obex.RxOBEX
import at.dibiasi.funker.rfcomm.RxSpp
import at.dibiasi.funker.utils.*
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import com.uber.autodispose.autoDisposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException

private const val TAG = "### MainActivity"
private const val MAX_RETRIES = 5L
// Bluetooth Request codes
const val FUNKER_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 199
const val REQUEST_ENABLE_BT = 125

class MainActivity : AppCompatActivity() {

    private val scopeProvider by lazy { AndroidLifecycleScopeProvider.from(this) }

    var rxSpp: RxSpp? = null
    var rxOBEX: RxOBEX? = null
    lateinit var finder: BluetoothDeviceFinder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        finder = BluetoothDeviceFinder(applicationContext)
        setupBluetooth()
    }

    private fun setupBluetooth() {
        checkBluetoothPermission()
        testBlHelper()
        if (!BluetoothAdapterHelper.isBluetoothEnabled()) {
            enableBluetooth()
        } else {
            Log.d(TAG, "Dispatched search")
            startBluetoothDeviceSearch(finder)
        }
    }

    @SuppressLint("CheckResult")
    private fun testBlHelper() {
        val helper = BluetoothAdapterHelper(context = applicationContext)
        helper.getObservable()
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onNext = {
                    Log.d(TAG, intent.action)
                },
                onError = {Log.e(TAG, it.message)}
            )
    }

    private fun checkBluetoothPermission() {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocalPermissions()
        }
    }

    private fun requestLocalPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            FUNKER_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION
        )
    }


    private fun enableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (REQUEST_ENABLE_BT == requestCode) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled")
                startBluetoothDeviceSearch(finder)
            } else {
                Log.e(TAG, "Bluetooth not enabled")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            FUNKER_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(TAG, "Permission granted.")
                } else {
                    Log.e(TAG, "Permission denied!")
                }
            }
        }
    }


    @SuppressLint("CheckResult")
    private fun startBluetoothDeviceSearch(finder: BluetoothDeviceFinder) {
        finder
            .search(checkPaired = true, indefinite = true)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .distinct()
            .filter {
                it.name?.contains("test", ignoreCase = true) ?: false
            }
            .autoDisposable(scopeProvider)
            .subscribeBy(
                onNext = { device ->
                    Log.d(TAG, "Found device: ${device.name}")
                    finder.stopSearch()
                    return@subscribeBy
                },
                onError = {
                    Log.e(TAG, "Error occoured")
                    it.printStackTrace()
                },
                onComplete = {
                    Log.d(TAG, "Completed search")
                }
            )
    }

    @SuppressLint("CheckResult")
    fun connectToDevice(device: BluetoothDevice) {
        rxSpp = RxSpp(device)
        runOnUiThread {
            rxSpp!!
                .connect()
                .observeOn(Schedulers.io())
                .autoDisposable(scopeProvider)
                .subscribeBy(
                    onError = { Log.e(TAG, "Received an error", it) },
                    onComplete = { Log.d(TAG, "Connected to device") }

                )
        }
    }

    @SuppressLint("CheckResult")
    fun readRfcomm() {
        val rxSpp = rxSpp ?: return // Make sure variable is not null
        runOnUiThread {
            rxSpp.read()
                .observeOn(Schedulers.newThread())
                .subscribeOn(Schedulers.newThread())
                .retryConditional(
                    predicate = { it is IOException },
                    maxRetry = 5,
                    delayBeforeRetryInMillis = 100
                )
                .autoDisposable(scopeProvider)
                .subscribeBy(
                    onNext = {
                        Log.d(TAG, "Received: $it")
                    },
                    onError = { Log.e(TAG, "Received an error", it) },
                    onComplete = { Log.d(TAG, "Read completed") }
                )
        }
    }

    @SuppressLint("CheckResult")
    fun sendRfcommCommand(command: String) {
        val rxSpp = rxSpp ?: return // Make sure variable is not null
        runOnUiThread {
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
    }

    @SuppressLint("CheckResult")
    fun sendFile() {
        val rxOBEX = rxOBEX ?: return // Make sure variable is not null
        runOnUiThread {
            rxOBEX
                .putFile("rubberduck.txt", "text/plain", "oh hi mark".toByteArray(), "firmware")
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
        runOnUiThread {
            rxOBEX
                .listFiles("firmware") // List files in /test
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .autoDisposable(scopeProvider)
                .subscribeBy(
                    onSuccess = { folderlisting ->
                        Log.d(TAG, "Retrieved folderlisting")
                        Log.d(TAG, folderlisting.toString())
                    },
                    onError = { e ->
                        Log.e(TAG, "Received error!")
                    })
        }
    }

}
