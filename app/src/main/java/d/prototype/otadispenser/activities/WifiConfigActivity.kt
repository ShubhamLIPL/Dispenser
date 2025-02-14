package d.prototype.otadispenser.activities

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import d.prototype.otadispenser.usbcommunication.USBHelper
import d.prototype.otaotadispenser.R

class WiFiConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_config)

        // Initialize USB Helper
        USBHelper.initialize(this)

        val ssidField = findViewById<EditText>(R.id.editWifiSSID)
        val passwordField = findViewById<EditText>(R.id.editWifiPassword)
        val ipField = findViewById<EditText>(R.id.editIpAddress)
        val portField = findViewById<EditText>(R.id.editPortNumber)
        val submitButton = findViewById<Button>(R.id.btnSubmitConfig)

        submitButton.setOnClickListener {
            val ssid = ssidField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val ip = ipField.text.toString().trim()
            val port = portField.text.toString().trim()

            if (ssid.isNotEmpty() && password.isNotEmpty() && ip.isNotEmpty() && port.isNotEmpty()) {
                val command = buildWifiConfigCommand(ssid, password, ip, port)
                val success = USBHelper.sendCommand(command)

                if (success) {
                    //Toast.makeText(this, "Command Sent: $command", Toast.LENGTH_SHORT).show()
                    //Log.d("WiFiConfigActivity", "Command Sent: $command")
                }
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                Log.e("WiFiConfigActivity", "Please fill all fields")
            }
        }
    }

    private fun buildWifiConfigCommand(ssid: String, password: String, ip: String, port: String): String {
        // Build the command in the format =*WifSSID;WifiPSWD;IP;Port;#
        return "=*$ssid;$password;$ip;$port;#"
    }
}
