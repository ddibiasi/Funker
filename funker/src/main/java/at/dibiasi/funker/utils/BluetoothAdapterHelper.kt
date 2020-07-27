package at.dibiasi.funker.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import at.dibiasi.funker.error.NoBluetoothException
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.rxkotlin.subscribeBy

private const val TAG = "## BluetoothAdptrHelper"

class BluetoothAdapterHelper(private val context: Context) {

    companion object {
        private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        fun isBluetoothEnabled(): Boolean {
            return bluetoothAdapter?.isEnabled ?: false
        }
    }

    private var emitter: ObservableEmitter<Intent>? = null

    /**
     * Returns BluetoothAdapter states on action.
     * Eg.: BluetoothAdapter.STATE_OFF
     * Get notified when the BT Adapter gets turned off
     * helper.getObservable(<your-filter>).subscribeBy(
     *      onNext = { intent ->
     *          intent.action.let { action ->
     *              if (action == BluetoothAdapter.ACTION_STATE_CHANGED){
     *                  if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
     *                      == BluetoothAdapter.STATE_OFF){
     *                      // Do something
     *                  }
     *              }
     *          }
     *      }
     * )
     * @param filter IntentFilter for BluetoothAdapter Actions, eg: IntentFilter(BluetoothDevice.ACTION_FOUND)
     * @return Emits when ever a state has changed
     */
    fun getObservable(filter: IntentFilter?): Observable<Intent> {
        Log.d(TAG, "Subscribing to adapter actions")
        return Observable.create {
            emitter = it
            Log.d(TAG, "Setting up helper")
            checkAdapter()
            registerReceiver(filter)
        }
    }

    private fun checkAdapter() {
        if (bluetoothAdapter == null) { // is bluetooth available on this device
            emitter?.onError(NoBluetoothException())
        }
    }

    private fun registerReceiver(filter: IntentFilter?) {
        context.registerReceiver(adapterReceiver, filter)
    }


    /**
     *  BroadcastReceiver for all filtered actions. Needs to be public.
     *  Emits intent onReceive
     */
    val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let { action ->
                Log.d(TAG, action)
                emitter?.onNext(intent)
            }
        }
    }
}