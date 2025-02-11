package d.prototype.ota.activities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usb_transactions")
data class UsbTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val notation: String,
    val availableSize: String,
    val size: String,
    val amount: Double,
    val volume: Double,
    val concentration: Double,
    val attendantId: String,
    val customerId: String,
    val lastFlowCount: Int,
    val epoch: Long,
    val pid: Int,
    val flag: Int,
    val transactionId: String,
    val transactionType: String,
    val vehicleId: String
)
