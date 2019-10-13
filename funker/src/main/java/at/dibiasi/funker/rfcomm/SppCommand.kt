package at.dibiasi.funker.rfcomm

interface SppCommand {
    val controller: SppControllers
    val request: String

    interface SppControllers
}