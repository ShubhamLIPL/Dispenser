package d.prototype.otadispenser.usbcommunication

internal object Constants {
    // Replace with your actual application ID
    private const val APPLICATION_ID = "d.prototype.ota_app"
    // Use the manual APPLICATION_ID in your constants
    val INTENT_ACTION_GRANT_USB: String = "$APPLICATION_ID.GRANT_USB"
    val INTENT_ACTION_DISCONNECT: String = "$APPLICATION_ID.Disconnect"
    val NOTIFICATION_CHANNEL: String = "$APPLICATION_ID.Channel"
    val INTENT_CLASS_MAIN_ACTIVITY: String = "$APPLICATION_ID.MainActivity"
    // values have to be unique within each app
    const val NOTIFY_MANAGER_START_FOREGROUND_SERVICE: Int = 1001
}

