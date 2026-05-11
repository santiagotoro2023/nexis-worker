package ch.toroag.nexis.desktop.util

import java.io.File

object VncServerManager {
    private var proc: Process? = null

    fun start(port: Int = 5900) {
        if (proc?.isAlive == true) return   // already running
        val os = System.getProperty("os.name", "").lowercase()
        val cmd: List<String> = when {
            os.contains("linux") -> listOf(
                "bash", "-c",
                "x11vnc -display :0 -rfbport $port -nopw -forever -bg -quiet 2>/dev/null || " +
                "Xvfb :99 -screen 0 1920x1080x24 & DISPLAY=:99 x11vnc -rfbport $port -nopw -forever -bg -quiet 2>/dev/null"
            )
            os.contains("win") -> listOf(
                "cmd", "/c",
                "net start tvnserver 2>nul || " +
                "\"C:\\Program Files\\TightVNC\\tvnserver.exe\" -start 2>nul || " +
                "\"C:\\Program Files\\RealVNC\\VNC Server\\vncserver.exe\" -service-start 2>nul"
            )
            os.contains("mac") -> listOf(
                "bash", "-c",
                "sudo /System/Library/CoreServices/RemoteManagement/ARDAgent.app/Contents/Resources/kickstart" +
                " -activate -configure -access -on -clientopts -setvnclegacy -vnclegacy yes" +
                " -clientopts -setvncpw -vncpw '' 2>/dev/null"
            )
            else -> return
        }
        try {
            proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            // VNC server not available on this system — silently ignore
        }
    }

    fun stop() {
        proc?.destroyForcibly()
        proc = null
        val os = System.getProperty("os.name", "").lowercase()
        val cmd: List<String>? = when {
            os.contains("linux") -> listOf("bash", "-c", "pkill x11vnc 2>/dev/null; true")
            os.contains("win")   -> listOf("cmd", "/c", "net stop tvnserver 2>nul; true")
            else                 -> null
        }
        cmd?.let { try { ProcessBuilder(it).start() } catch (_: Exception) {} }
    }
}
