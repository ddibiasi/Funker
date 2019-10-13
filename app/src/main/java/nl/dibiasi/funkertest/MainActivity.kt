package nl.dibiasi.funkertest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import at.dibiasi.funker.BluetoothDeviceFinder
import at.dibiasi.funker.rfcomm.RxSpp
import at.dibiasi.funker.utils.subscribeBy
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import com.uber.autodispose.autoDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers

private const val TAG = "### MainActivity"
private const val MAX_RETRIES = 5L

class MainActivity : AppCompatActivity() {

    private val scopeProvider by lazy { AndroidLifecycleScopeProvider.from(this) }


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
                    Log.d(TAG, "Found device: ")
                    sendRfcommCommand(device, "oh hi mark")
                },
                onError = {
                    Log.e(TAG, "Error occoured")
                    it.printStackTrace()
                },
                onComplete = { Log.d(TAG, "Completed search") }
            )
    }

    @SuppressLint("CheckResult")
    fun sendRfcommCommand(device: BluetoothDevice, command: String) {
        val rxSpp = RxSpp(device)
        rxSpp
            .send(command)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .retry(MAX_RETRIES)
            .subscribeBy(
                onComplete = {
                    Log.d(TAG, "Succesfully sent $command to ${device.address}")
                },
                onError = { e ->
                    Log.e(TAG, "Received error!")
                })
    }
}
