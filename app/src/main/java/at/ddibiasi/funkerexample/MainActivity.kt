package at.ddibiasi.funkerexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import at.ddibiasi.funker.BluetoothDeviceFinder
import at.dibiasi.funker.error.BluetoothDisabledException
import at.dibiasi.funker.error.NoBluetoothException
import io.reactivex.disposables.CompositeDisposable

private const val TAG = "### MainActivity"
class MainActivity : AppCompatActivity() {

    private val btSearchCompDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findBluetoothDevices()
    }

    private fun findBluetoothDevices() {
        val finder = BluetoothDeviceFinder(applicationContext)
        val disposable = finder.search("Hoss")
            .doOnComplete {
                Log.d(TAG, "Bluetooth search complete")
                btSearchCompDisposable.dispose()
            }
            .doOnError {
                Log.e(TAG, it.localizedMessage)
            }
            .subscribe(
                { device ->
                    Log.d(TAG, "Found device")
                    btSearchCompDisposable.dispose()
                    finder.stopSearch()
                  // connect to devise
                }, { throwable ->
                    when (throwable.javaClass) {
                        NoBluetoothException::class.java -> Log.d(TAG, "No Bluetooth")
                        BluetoothDisabledException::class.java -> Log.d(TAG, "Bluetooth disabled")
                    }
                })
        btSearchCompDisposable.add(disposable)
    }
}
