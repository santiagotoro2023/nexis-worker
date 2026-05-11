package ch.toroag.nexis.desktop.ui.commands

import androidx.lifecycle.ViewModel

data class WorkerCommand(
    val name:        String,
    val description: String,
    val isBuiltin:   Boolean,
)

class CommandsViewModel : ViewModel() {
    val builtinCommands = listOf(
        WorkerCommand("shell_exec",     "Execute a shell command on this device",                              true),
        WorkerCommand("screenshot",     "Capture a screenshot and send it to the controller",                  true),
        WorkerCommand("start_vnc",      "Start VNC server (x11vnc / TightVNC / ARDAgent) for remote screen",  true),
        WorkerCommand("stop_vnc",       "Stop VNC server on this device",                                      true),
        WorkerCommand("lock_screen",    "Lock the desktop session",                                            true),
        WorkerCommand("notify",         "Show a desktop notification",                                         true),
        WorkerCommand("open_url",       "Open a URL in the default browser",                                   true),
        WorkerCommand("set_volume",     "Set system volume (0-100)",                                           true),
        WorkerCommand("sleep",          "Put the device to sleep",                                             true),
        WorkerCommand("wake_on_lan",    "Send WoL magic packet to a MAC address",                              true),
        WorkerCommand("probe",          "Run system diagnostics and return report",                            true),
        WorkerCommand("file_read",      "Read a file and return its contents",                                 true),
        WorkerCommand("file_write",     "Write content to a file",                                             true),
    )
}
