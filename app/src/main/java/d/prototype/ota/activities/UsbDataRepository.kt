package d.prototype.ota.activities

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbDataRepository(context: Context) {
    private val usbTransactionDao = UsbDatabase.getDatabase(context).usbTransactionDao()

    suspend fun insertParsedData(transaction: UsbTransaction) {
        withContext(Dispatchers.IO) {
            usbTransactionDao.insertTransaction(transaction)
        }
    }

    suspend fun getAllTransactions(): List<UsbTransaction> {
        return withContext(Dispatchers.IO) {
            usbTransactionDao.getAllTransactions()
        }
    }

    // ✅ Corrected: Call the function from `usbTransactionDao`
    suspend fun clearAllTransactions() {
        withContext(Dispatchers.IO) {
            usbTransactionDao.clearAllTransactions() // Fixed: Call the DAO method properly
        }
    }
}
