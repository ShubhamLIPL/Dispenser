package d.prototype.otadispenser.usbcommunication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class UsbBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.example.USB_PERMISSION" -> {
                val permissionGranted = intent.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (permissionGranted) {
                    Toast.makeText(context, "USB Permission Granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "USB Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
