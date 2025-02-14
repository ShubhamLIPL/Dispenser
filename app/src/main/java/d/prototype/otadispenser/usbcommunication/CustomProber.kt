package d.prototype.otadispenser.usbcommunication

import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * add devices here, that are not known to DefaultProber
 *
 * if the App should auto start for these devices, also
 * add IDs to app/src/main/res/xml/usb_device_filter.xml
 */
object CustomProber {
    fun getCustomProber(): UsbSerialProber {
        val customTable = ProbeTable()
        customTable.addProduct(
            0x1234,
            0xabcd,
            FtdiSerialDriver::class.java
        ) // e.g. device with custom VID+PID
        return UsbSerialProber(customTable)
    }
}
