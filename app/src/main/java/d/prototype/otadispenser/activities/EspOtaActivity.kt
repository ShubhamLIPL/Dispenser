package d.prototype.otadispenser.activities

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import d.prototype.otadispenser.usbcommunication.USBHelper
import d.prototype.otaotadispenser.R

class EspOtaActivity : AppCompatActivity() {

    private var connectionType: Int = 1 // Default to LAN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esp_ota)

        // Initialize USB Helper
        USBHelper.initialize(this)

        val spinner = findViewById<Spinner>(R.id.spinnerConnectionType)
        val btnSendCommand = findViewById<Button>(R.id.btnProceedOta)

        // Options for LAN and WAN
        val connectionOptions = listOf("LAN", "WAN")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectionOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Spinner selection handling
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                connectionType = if (position == 0) 1 else 2
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                connectionType = 1 // Default to LAN
            }
        }

        // Button click handling
        btnSendCommand.setOnClickListener {
            // Define the command based on the connection type
            val commandHex = when (connectionType) {
                1 -> "0x34 0x01" // LAN
                2 -> "0x34 0x02" // WAN
                else -> ""
            }

            if (commandHex.isNotEmpty()) {
                val success = USBHelper.sendCommand(commandHex)
                if (!success) {
                    //Toast.makeText(this, "Error Sending Command: $commandHex", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Invalid Command", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
