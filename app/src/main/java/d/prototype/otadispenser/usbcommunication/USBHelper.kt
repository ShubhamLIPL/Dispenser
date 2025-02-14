package d.prototype.otadispenser.usbcommunication

import android.content.Context
import android.hardware.usb.*
import android.util.Log
import d.prototype.otadispenser.activities.UsbDataRepository
import d.prototype.otadispenser.activities.UsbTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object USBHelper {
    private lateinit var usbManager: UsbManager
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbEndpointOut: UsbEndpoint? = null
    private var usbEndpointIn: UsbEndpoint? = null
    private var repository: UsbDataRepository? = null

    fun initialize(context: Context) {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        repository = UsbDataRepository(context)
    }

    fun isDeviceConnected(): Boolean {
        return usbDeviceConnection != null
    }

    fun connectToDevice(context: Context, callback: (Boolean) -> Unit) {
        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val usbDevice = deviceList.values.firstOrNull()
            usbDevice?.let { device ->
                val usbConnection = usbManager.openDevice(device)
                if (usbConnection != null) {
                    establishConnection(usbConnection, device, context, callback)
                } else {
                    Log.e("USBHelper", "Failed to open USB connection")
                    callback(false)
                }
            }
        } else {
            Log.w("USBHelper", "No USB devices detected")
            callback(false)
        }
    }

    private fun establishConnection(
        connection: UsbDeviceConnection,
        device: UsbDevice,
        context: Context,
        callback: (Boolean) -> Unit
    ) {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            connection.claimInterface(usbInterface, true)

            for (j in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(j)
                when {
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_OUT -> {
                        usbEndpointOut = endpoint
                    }
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_IN -> {
                        usbEndpointIn = endpoint
                    }
                }
            }
        }

        if (usbEndpointOut != null && usbEndpointIn != null) {
            usbDeviceConnection = connection
            callback(true)
        } else {
            callback(false)
        }
    }

    fun sendCommand(command: String): Boolean {
        val connection = usbDeviceConnection
        val endpoint = usbEndpointOut
        if (connection == null || endpoint == null) return false

        val commandBytes = if (command.startsWith("0x")) {
            byteArrayOf(command.removePrefix("0x").toInt(16).toByte())
        } else {
            command.toByteArray(Charsets.UTF_8)
        }

        Log.d("USBHelper", "Sending Command: ${commandBytes.joinToString { "0x%02X".format(it) }}")

        var attempt = 0
        while (attempt < 3) {
            val result = connection.bulkTransfer(endpoint, commandBytes, commandBytes.size, 5000)
            if (result >= 0) {
                Log.d("USBHelper", "Command sent successfully")
                return true
            } else {
                Log.e("USBHelper", "Failed to send command. Attempt ${attempt + 1}")
                attempt++
            }
        }
        return false
    }

    fun waitForAck(): Boolean {
        val connection = usbDeviceConnection ?: return false
        val endpointIn = usbEndpointIn ?: return false
        val ackBuffer = ByteArray(1)

        repeat(3) {
            val result = connection.bulkTransfer(endpointIn, ackBuffer, ackBuffer.size, 5000)
            if (result > 0 && ackBuffer[0] == 0xFF.toByte()) {
                Log.d("USBHelper", "ACK received successfully")
                return true
            }
        }
        Log.e("USBHelper", "ACK not received after 3 attempts")
        return false
    }

    fun sendAcknowledgment() {
        val connection = usbDeviceConnection ?: return
        val endpoint = usbEndpointOut ?: return
        val ack = byteArrayOf(0xFF.toByte())
        val result = connection.bulkTransfer(endpoint, ack, ack.size, 5000)
        if (result >= 0) {
            Log.d("USBHelper", "ACK 0xFF sent successfully!")
        } else {
            Log.e("USBHelper", "Failed to send ACK")
        }
    }

    // USBHelper.kt

    fun receiveData(context: Context, callback: () -> Unit) {
        val connection = usbDeviceConnection ?: return
        val endpointIn = usbEndpointIn ?: return
        val endpointOut = usbEndpointOut ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            var allDataReceived = false

            while (!allDataReceived) {
                val receivedBytes = connection.bulkTransfer(endpointIn, buffer, buffer.size, 10000)
                if (receivedBytes > 0) {
                    val rawData = buffer.copyOf(receivedBytes).decodeToString().trim()
                    Log.d("USBHelper", "Received Data: $rawData")

                    if (rawData.contains("FINISH")) {
                        Log.d("USBHelper", "FINISH received, stopping reception.")
                        allDataReceived = true
                        break
                    }

                    val parsedData = parseDeviceData(rawData)
                    if (parsedData != null) {
                        repository?.insertParsedData(parsedData)
                        withContext(Dispatchers.Main) { callback() }

                        // Send ACK for each successful data receipt
                        val ack = byteArrayOf(0xFF.toByte())
                        val result = connection.bulkTransfer(endpointOut, ack, ack.size, 5000)
                        if (result >= 0) {
                            Log.d("USBHelper", "ACK 0xFF sent!")
                        } else {
                            Log.e("USBHelper", "Failed to send ACK!")
                            break
                        }
                    }
                } else {
                    Log.e("USBHelper", "No data received.")
                    break
                }
            }
        }
    }


    private fun parseDeviceData(rawData: String): UsbTransaction? {
        try {
            // Remove start (*) and end (#) markers
            val cleanedData = rawData.trim().removePrefix("*;").removeSuffix(";#")

            // Split values by `;`
            val values = cleanedData.split(";")

            if (values.size < 14) {
                Log.e("USBHelper", "Invalid data format received: $rawData")
                return null
            }

            return UsbTransaction(
                notation = values[0],
                availableSize = values[1],   // Corrected index
                size = values[2],            // Corrected index
                amount = values[3].toDoubleOrNull() ?: 0.0,
                volume = values[4].toDoubleOrNull() ?: 0.0,
                concentration = values[5].toDoubleOrNull() ?: 0.0,
                attendantId = values[6],
                customerId = values[7],
                lastFlowCount = values[8].toIntOrNull() ?: 0,
                epoch = values[9].toLongOrNull() ?: 0L,
                pid = values[10].toIntOrNull() ?: 0,
                flag = values[11].toIntOrNull() ?: 0,
                transactionId = values[12],
                transactionType = values[13],
                vehicleId = values[14]
            )
        } catch (e: Exception) {
            Log.e("USBHelper", "Error parsing data", e)
            return null
        }
    }

    fun sendStoredData(context: Context) {
        val connection = usbDeviceConnection ?: return
        val endpointOut = usbEndpointOut ?: return
        val endpointIn = usbEndpointIn ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val transactions = repository?.getAllTransactions() ?: return@launch
            for (transaction in transactions) {
                val dataChunk = transaction.toString().toByteArray() // Convert transaction to bytes
                Log.d("USBHelper", "Sending transaction: ${transaction.transactionId}")

                val result = connection.bulkTransfer(endpointOut, dataChunk, dataChunk.size, 5000)
                if (result < 0) {
                    Log.e("USBHelper", "Failed to send transaction: ${transaction.transactionId}")
                    break
                }

                // **Wait for acknowledgment**
                val ackBuffer = ByteArray(1)
                val ackResult = connection.bulkTransfer(endpointIn, ackBuffer, ackBuffer.size, 5000)

                if (ackResult > 0 && ackBuffer[0] == 0xFF.toByte()) {
                    Log.d("USBHelper", "ACK received for ${transaction.transactionId}")
                } else {
                    Log.e("USBHelper", "No ACK received for ${transaction.transactionId}. Stopping transmission.")
                    break
                }
            }

            Log.d("USBHelper", "All transactions sent.")
        }
    }

    fun releaseConnection() {
        usbDeviceConnection?.close()
        usbDeviceConnection = null
        usbEndpointOut = null
        usbEndpointIn = null
        Log.d("USBHelper", "USB Connection released")
    }
}
