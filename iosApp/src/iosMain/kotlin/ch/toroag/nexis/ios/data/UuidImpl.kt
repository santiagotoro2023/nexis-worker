package ch.toroag.nexis.ios.data

import platform.Foundation.NSUUID

actual fun generateUuid(): String = NSUUID().UUIDString()
