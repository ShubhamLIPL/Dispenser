package d.prototype.ota.activities

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import d.prototype.ota.activities.UsbTransaction

@Dao
interface UsbDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: UsbTransaction)

    @Query("SELECT * FROM usb_transactions ORDER BY id DESC")
    suspend fun getAllTransactions(): List<UsbTransaction>

    @Query("DELETE FROM usb_transactions")
    suspend fun clearAllTransactions()
}
