package at.dibiasi.funker.error

import java.lang.Exception

class NoBluetoothException(message: String = "This device has no at.ddibiasi.funker.android.bluetooth.") : Exception(message)
class BluetoothDisabledException(message: String = "Bluetooth is disabled.") : Exception(message)

