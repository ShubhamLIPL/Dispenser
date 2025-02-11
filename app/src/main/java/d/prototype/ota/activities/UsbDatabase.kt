package d.prototype.ota.activities

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UsbTransaction::class], version = 6, exportSchema = false)
abstract class UsbDatabase : RoomDatabase() {
    abstract fun usbTransactionDao(): UsbDataDao

    companion object {
        @Volatile
        private var INSTANCE: UsbDatabase? = null

        fun getDatabase(context: Context): UsbDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UsbDatabase::class.java,
                    "usb_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
