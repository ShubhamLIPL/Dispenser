package d.prototype.ota.activities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usb_data")
data class UsbDataChunk(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val data: ByteArray
)
