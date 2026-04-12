package ch.toroag.nexis.worker.util

import android.content.Context
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Trust On First Use (TOFU) trust manager — same model as SSH host keys.
 *
 * First connection to a new server: the certificate is accepted automatically
 * and its SHA-256 fingerprint is pinned to local storage.
 *
 * All subsequent connections: the presented cert must match the pinned
 * fingerprint. If it doesn't (e.g. the server cert was regenerated), a
 * [CertificateException] is thrown and the user sees a connection error.
 * They can clear the pin in Settings → "Forget certificate" to re-pair.
 */
class TofuTrustManager(
    private val context: Context,
    /** Called when a new cert is pinned (first connection or after clear). */
    private val onPinned: ((fingerprint: String) -> Unit)? = null,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val cert        = chain[0]
        val fingerprint = sha256(cert.encoded)
        val pinned      = CertPinStore.getPin(context)

        when {
            pinned == null -> {
                // First ever connection — pin and trust
                CertPinStore.savePin(context, fingerprint)
                onPinned?.invoke(fingerprint)
            }
            pinned != fingerprint -> throw CertificateException(
                "Server certificate changed.\n" +
                "Pinned:   $pinned\n" +
                "Received: $fingerprint\n\n" +
                "If you regenerated the server cert, go to Settings and tap " +
                "\"Forget certificate\" to re-pair."
            )
            // else: fingerprint matches — trusted
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString(":") { "%02x".format(it) }
    }
}
