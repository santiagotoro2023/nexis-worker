package ch.toroag.nexis.worker.util

import android.content.Context

/**
 * Stores the pinned server certificate fingerprint in SharedPreferences.
 * Uses synchronous SharedPreferences (not DataStore) because the TLS trust
 * manager needs synchronous reads during the handshake.
 */
object CertPinStore {

    private const val PREFS = "nexis_cert_pin"
    private const val KEY   = "sha256_pin"

    fun getPin(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)

    fun savePin(context: Context, pin: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, pin).apply()

    fun clearPin(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
}
