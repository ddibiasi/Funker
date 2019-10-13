package at.dibiasi.funker.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import at.dibiasi.funker.rfcomm.RxSpp
import io.reactivex.Completable
import kotlin.reflect.KFunction

class FunkerUtils {
    companion object {
        fun isBluetoothEnabled(): Boolean {
            val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            return mBluetoothAdapter.isEnabled
        }
    }
}

fun BluetoothDevice.removeBond() {
    try {
        val method = this.javaClass.getMethod("removeBond")
        method.invoke(this) as Any
    } catch (e: Exception) {
        Log.e("BluetoothDevice", e.message)
    }
}

