package at.dibiasi.funker.utils

import android.bluetooth.BluetoothDevice
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import io.reactivex.Observable
import io.reactivex.rxkotlin.*
import java.util.concurrent.TimeUnit


fun <T> Observable<T>.retryConditional(
    predicate: (Throwable) -> Boolean,
    maxRetry: Long,
    delayBeforeRetryInMillis: Long
): Observable<T> =
    retryWhen {
        Observables.zip(
            it.map { if (predicate(it)) it else throw it },
            Observable.interval(delayBeforeRetryInMillis, TimeUnit.MILLISECONDS)
        )
            .map { if (it.second >= maxRetry) throw it.first }
    }

fun BluetoothDevice.removeBond() {
    try {
        val method = this.javaClass.getMethod("removeBond")
        method.invoke(this) as Any
    } catch (e: Exception) {
        Log.e("BluetoothDevice", e.message)
    }
}

