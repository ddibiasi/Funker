package at.dibiasi.funker.error

import java.lang.Exception

class NoBluetoothException(message: String = "This device has no bluetooth.") : Exception(message)
class BluetoothDisabledException(message: String = "Bluetooth is disabled.") : Exception(message)
class BluetoothConnectionException(message: String = "Could not connect to bluetooth.", tries: Int = 1) : Exception(message)
class BluetoothSocketException(message: String = "Could not open socket!") : Exception(message)
class BluetoothStreamException(message: String = "Could not open stream!") : Exception(message)

