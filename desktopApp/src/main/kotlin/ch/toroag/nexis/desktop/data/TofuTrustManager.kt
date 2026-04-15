package ch.toroag.nexis.desktop.data

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/** Trust On First Use (TOFU) trust manager — same logic as the Android version. */
class TofuTrustManager(
    private val onPinned: ((fingerprint: String) -> Unit)? = null,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val cert        = chain[0]
        val fingerprint = sha256(cert.encoded)
        val pinned      = CertPinStore.getPin()

        when {
            pinned == null -> {
                CertPinStore.savePin(fingerprint)
                onPinned?.invoke(fingerprint)
            }
            pinned != fingerprint -> throw CertificateException(
                "Server certificate changed.\n" +
                "Pinned:   $pinned\n" +
                "Received: $fingerprint\n\n" +
                "If you regenerated the server cert, go to Settings and click " +
                "\"Forget certificate\" to re-pair."
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString(":") { "%02x".format(it) }
}
