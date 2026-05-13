package ch.toroag.nexis.ios.data

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

actual fun generateUuid(): String = NSUUID().UUIDString()

actual fun createSettings(): Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
