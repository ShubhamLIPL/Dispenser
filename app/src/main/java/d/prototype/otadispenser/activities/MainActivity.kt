package d.prototype.otadispenser.activities

import android.app.AlertDialog
import android.app.ProgressDialog
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import d.prototype.otadispenser.usbcommunication.USBHelper
import d.prototype.otaotadispenser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.crypt.EncryptionMode
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnectUsb: Button
    private lateinit var btnBackup: Button
    private lateinit var btnRestore: Button
    private lateinit var btnReleaseUsb: Button
    private lateinit var btnClearData: Button
    private lateinit var btnSheets: Button
    private lateinit var btnSaveExcel: Button
    private lateinit var tvReceivedData: TextView
    private lateinit var etDispenserId: EditText
    private lateinit var repository: UsbDataRepository
    private lateinit var progressDialog: ProgressDialog
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressDialog = ProgressDialog(this).apply {
            setMessage("Sending data to device, please wait...")
            setCancelable(false)
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
        }

        repository = UsbDataRepository(applicationContext)
        USBHelper.initialize(this)

        btnConnectUsb = findViewById(R.id.btnConnectUsb)
        btnBackup = findViewById(R.id.btnBackup)
        btnRestore = findViewById(R.id.btnRestore)
        btnReleaseUsb = findViewById(R.id.btnReleaseUsb)
        btnClearData = findViewById(R.id.btnClearData)
        btnSheets = findViewById(R.id.sheetbutton)
        btnSaveExcel = findViewById(R.id.btnSaveExcel)
        tvReceivedData = findViewById(R.id.tvReceivedData)
        etDispenserId = findViewById(R.id.etDispenserId)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)

        btnConnectUsb.setOnClickListener { showConnectionDialog() }
        btnBackup.setOnClickListener { sendBackupCommand() }
        btnRestore.setOnClickListener { sendRecoveryData() }  // Updated to handle recovery
        btnReleaseUsb.setOnClickListener { releaseUsbConnection() }
        btnClearData.setOnClickListener { clearReceivedData() }
        btnSaveExcel.setOnClickListener {
            val id = etDispenserId.text.toString().trim()
            if (id.isNotEmpty()) saveDataToExcel(id)
            else Toast.makeText(this, "Dispenser ID is required!", Toast.LENGTH_SHORT).show()
        }

        loadReceivedData()
    }

    private fun showConnectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Connect to USB Device")
            .setMessage("Do you want to connect to the USB device?")
            .setPositiveButton("OK") { _, _ ->
                USBHelper.connectToDevice(this) { success ->
                    runOnUiThread {
                        val message =
                            if (success) "USB Device Connected" else "Failed to Connect USB Device"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendBackupCommand() {
        if (USBHelper.sendCommand("0x38")) {
            progressDialog.setMessage("Backing up data, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show() // Show the progress dialog

            Toast.makeText(this, "Backup command sent", Toast.LENGTH_SHORT).show()
            Log.d("Dharmik", "Backup command sent")

            lifecycleScope.launch(Dispatchers.IO) {
                USBHelper.receiveData(this@MainActivity) {
                    runOnUiThread {
                        progressDialog.dismiss() // Dismiss the progress dialog when backup completes
                        showSaveDialog()         // Show the save dialog after backup
                    }
                }
            }
        } else {
            Toast.makeText(this, "USB not connected!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_excel, null)
        val etDispenserId = dialogView.findViewById<EditText>(R.id.etDispenserId)

        AlertDialog.Builder(this)
            .setTitle("Save Backup Data")
            .setMessage("*Note: Press OK to save the data into Excel.")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val dispenserId = etDispenserId.text.toString().trim()
                if (dispenserId.isNotEmpty()) {
                    saveDataToExcel(dispenserId)  // Pass dispenserId here
                } else {
                    Toast.makeText(this, "Dispenser ID is required!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    private fun sendRecoveryData() {
        filePickerLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { readExcelFileAndSendToUsb(it) }
        }

    private fun sendCompletionSignal() {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = USBHelper.sendCommand("0x3C")
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@MainActivity, "Recovery Completed. 0x3C Sent!", Toast.LENGTH_SHORT).show()
                    Log.d("USBHelper", "0x3C command sent successfully after recovery.")
                } else {
                    Toast.makeText(this@MainActivity, "Failed to send 0x3C after recovery!", Toast.LENGTH_SHORT).show()
                    Log.e("USBHelper", "Failed to send 0x3C after recovery.")
                }
            }
        }
    }

    private fun readExcelFileAndSendToUsb(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Failed to open InputStream")
                val fs = POIFSFileSystem(inputStream)
                val info = EncryptionInfo(fs)
                val decryptor = info.decryptor

                if (!decryptor.verifyPassword("Leons8051")) { // Use the same password used during encryption
                    throw GeneralSecurityException("Unable to process: document is encrypted")
                }

                decryptor.getDataStream(fs).use { dataStream ->
                    val workbook = XSSFWorkbook(dataStream)
                    val sheet = workbook.getSheetAt(0) ?: throw Exception("No sheet found in file")

                    val transactions = mutableListOf<TransactionData>()
                    for (rowIndex in sheet.lastRowNum downTo 1) {        // changed from last to first
                        val row = sheet.getRow(rowIndex) ?: continue
                        try {
                            val transaction = TransactionData(
                                availableSize = getCellValue(row.getCell(0))?.toIntOrNull() ?: 0,
                                size = getCellValue(row.getCell(1))?.toIntOrNull() ?: 0,
                                amount = getCellValue(row.getCell(2))?.toDoubleOrNull() ?: 0.0,
                                volume = getCellValue(row.getCell(3))?.toDoubleOrNull() ?: 0.0,
                                concentration = getCellValue(row.getCell(4))?.toDoubleOrNull()
                                    ?: 0.0,
                                attendantId = getCellValue(row.getCell(5)) ?: "",
                                customerId = getCellValue(row.getCell(6)) ?: "",
                                lastFlowCount = getCellValue(row.getCell(7))?.toIntOrNull() ?: 0,
                                epoch = getCellValue(row.getCell(8))?.toLongOrNull() ?: 0L,
                                pid = getCellValue(row.getCell(9))?.toIntOrNull() ?: 0,
                                flag = getCellValue(row.getCell(10))?.toIntOrNull() ?: 0,
                                transactionId = getCellValue(row.getCell(11)) ?: "",
                                transactionType = getCellValue(row.getCell(12)) ?: "",
                                vehicleId = getCellValue(row.getCell(13)) ?: ""
                            )
                            transactions.add(transaction)
                            println("Transactions : $transaction")
                        } catch (e: Exception) {
                            Log.e("Recovery", "Error parsing row $rowIndex", e)
                        }
                    }

                    workbook.close()

                    if (transactions.isNotEmpty()) {
                        sendTransactionsToUsb(transactions)
                    } else {
                        showToast("No valid data found in file!")
                    }
                }
                inputStream.close()
            } catch (e: Exception) {
                Log.e("Recovery", "Error reading file", e)
                showToast("Error reading file!")
            }
        }
    }

    private fun getCellValue(cell: XSSFCell?): String? {
        return when (cell?.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            else -> null
        }
    }

    private fun sendTransactionsToUsb(transactions: List<TransactionData>) {
        if (!USBHelper.isDeviceConnected()) {
            showToast("USB not connected!")
            return
        }

        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            tvProgress.visibility = View.VISIBLE
            progressBar.max = transactions.size
            tvProgress.text = "0/${transactions.size} Transactions"
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var completed = 0
            for (transaction in transactions) {
                val command = buildUsbCommand(transaction)
                val sent = USBHelper.sendCommand(command)

                if (sent) {
                    if (USBHelper.waitForAck()) {
                        completed++
                        runOnUiThread {
                            progressBar.progress = completed
                            tvProgress.text = "$completed/${transactions.size} Transactions"
                        }
                        Log.d("USBHelper", "Acknowledgment sent for ${transaction.transactionId}")
                    } else {
                        runOnUiThread {
                            showToast("Transaction ${transaction.transactionId} failed: No ACK")
                            progressBar.visibility = View.GONE
                            tvProgress.visibility = View.GONE
                        }
                        return@launch
                    }
                } else {
                    runOnUiThread {
                        showToast("Transaction ${transaction.transactionId} failed to send")
                        progressBar.visibility = View.GONE
                        tvProgress.visibility = View.GONE
                    }
                    return@launch
                }
            }

            runOnUiThread {
                showToast("All transactions sent successfully!")
                progressBar.visibility = View.GONE
                tvProgress.visibility = View.GONE
            }
            sendCompletionSignal()
        }
    }


    private fun buildUsbCommand(transaction: TransactionData): String {
        val commandBody =
            "${transaction.availableSize};${transaction.size};${transaction.amount};${transaction.volume};" +
                    "${transaction.concentration};${transaction.attendantId};${transaction.customerId};${transaction.lastFlowCount};" +
                    "${transaction.epoch};${transaction.pid};${transaction.flag};${transaction.transactionId};" +
                    "${transaction.transactionType};${transaction.vehicleId}"

        return ":*;$commandBody#"
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearReceivedData() {
        tvReceivedData.text = "Received Data:"
    }

    private fun releaseUsbConnection() {
        USBHelper.releaseConnection()
        Toast.makeText(this, "USB Connection Released", Toast.LENGTH_SHORT).show()
    }

    private fun loadReceivedData() {
        lifecycleScope.launch {
            val transactions = repository.getAllTransactions()
            val displayText = buildString {
                append("Available | Size | Amount | Volume | Concentration | Attendant ID | Customer ID | Last Flowcount | Epoch | PID | Flag | Transaction ID | Transaction Type | Vehicle ID\n")
                append("-----------------------------------------------------------------------------------------------------\n")
                transactions.forEach { transaction ->
                    append("${transaction.availableSize} | ${transaction.size} | ${transaction.amount} | ${transaction.volume} | ${transaction.concentration} | ${transaction.attendantId} | ${transaction.customerId} | ${transaction.lastFlowCount} | ${transaction.epoch} | ${transaction.pid} | ${transaction.flag} | ${transaction.transactionId} | ${transaction.transactionType} | ${transaction.vehicleId}\n")
                }
            }
            runOnUiThread {
                tvReceivedData.text = displayText.ifEmpty { "No data received yet." }
            }
        }
    }

//    private fun sendDataToGoogleSheets() {
//        lifecycleScope.launch {
//            val transactions = repository.getAllTransactions()
//            //updateGoogleSheet(transactions)
//        }
//    }

    private fun saveDataToExcel(dispenserId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val transactions = repository.getAllTransactions()
            if (transactions.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "No data to save!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val currentDateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Dispenser_${dispenserId}_${currentDateTime}.xlsx"
            val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), fileName)

            try {
                XSSFWorkbook().use { workbook ->
                    val sheet = workbook.createSheet("Transaction Data")

                    // Write Headers
                    val headers = listOf(
                        "Available Size", "Size", "Amount", "Volume", "Concentration",
                        "Attendant ID", "Customer ID", "Last Flowcount", "Epoch",
                        "PID", "Flag", "Transaction ID", "Transaction Type", "Vehicle ID"
                    )
                    val headerRow = sheet.createRow(0)
                    headers.forEachIndexed { index, title ->
                        headerRow.createCell(index).setCellValue(title)
                    }

                    // Write Each Transaction in a New Row
                    transactions.forEachIndexed { rowIndex, transaction ->
                        val dataRow = sheet.createRow(rowIndex + 1)
                        val transactionData = listOf(
                            transaction.availableSize, transaction.size, transaction.amount,
                            transaction.volume, transaction.concentration, transaction.attendantId,
                            transaction.customerId, transaction.lastFlowCount, transaction.epoch,
                            transaction.pid, transaction.flag, transaction.transactionId,
                            transaction.transactionType, transaction.vehicleId
                        )
                        transactionData.forEachIndexed { colIndex, value ->
                            dataRow.createCell(colIndex).setCellValue(value.toString())
                        }
                    }

                    val fs = POIFSFileSystem()
                    val info = EncryptionInfo(EncryptionMode.agile)
                    val encryptor = info.encryptor
                    encryptor.confirmPassword("Leons8051")  // Encryption password

                    FileOutputStream(filePath).use { fos ->
                        encryptor.getDataStream(fs).use { encryptedStream ->
                            workbook.write(encryptedStream)
                        }
                        fs.writeFilesystem(fos)
                    }

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Encrypted Excel saved: ${filePath.absolutePath}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                repository.clearAllTransactions()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Database Cleared!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error saving encrypted Excel file!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
