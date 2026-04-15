package ch.toroag.nexis.desktop.util

import ch.toroag.nexis.desktop.data.NexisApiService
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

object DesktopCommandExecutor {

    fun execute(cmd: NexisApiService.PendingCommand) {
        try {
            when (cmd.action.lowercase()) {
                "open_url"  -> run("xdg-open", cmd.arg)
                "open_app"  -> run("xdg-open", cmd.arg)
                "notify"    -> run("notify-send", "NeXiS", cmd.arg)
                "clip"      -> copyToClipboard(cmd.arg)
                "media"     -> dispatchMedia(cmd.arg)
                "volume"    -> setVolume(cmd.arg)
            }
        } catch (_: Exception) {}
    }

    private fun run(vararg args: String) {
        ProcessBuilder(*args)
            .redirectErrorStream(true)
            .start()
    }

    private fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    private fun dispatchMedia(action: String) {
        val key = when (action.lowercase()) {
            "play", "play-pause", "toggle" -> "play-pause"
            "pause"                        -> "pause"
            "next"                         -> "next"
            "previous", "prev"             -> "previous"
            "stop"                         -> "stop"
            "seek_forward"                 -> "position 10+"
            "seek_backward"                -> "position 10-"
            else                           -> "play-pause"
        }
        if (key.startsWith("position")) {
            ProcessBuilder("playerctl", *key.split(" ").toTypedArray())
                .redirectErrorStream(true).start()
        } else {
            ProcessBuilder("playerctl", key)
                .redirectErrorStream(true).start()
        }
    }

    private fun setVolume(arg: String) {
        val pct = arg.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: return
        // Try pactl first (PulseAudio / PipeWire), fall back to amixer
        try {
            ProcessBuilder("pactl", "set-sink-volume", "@DEFAULT_SINK@", "$pct%")
                .redirectErrorStream(true).start()
        } catch (_: Exception) {
            try {
                ProcessBuilder("amixer", "sset", "Master", "$pct%")
                    .redirectErrorStream(true).start()
            } catch (_: Exception) {}
        }
    }
}
