package ch.toroag.nexis.desktop.data

import java.nio.file.Files
import java.nio.file.Path

/** File-based certificate pin store — same TOFU semantics as the Android version. */
object CertPinStore {
    private val pinFile: Path = configDir().resolve("cert_pin.txt")

    fun getPin(): String? = runCatching {
        if (Files.exists(pinFile)) pinFile.toFile().readText().trim().takeIf { it.isNotEmpty() }
        else null
    }.getOrNull()

    fun savePin(pin: String) = runCatching {
        Files.createDirectories(pinFile.parent)
        pinFile.toFile().writeText(pin)
    }

    fun clearPin() = runCatching { Files.deleteIfExists(pinFile) }

    private fun configDir(): Path =
        Path.of(System.getProperty("user.home"), ".config", "nexis-worker")
}
