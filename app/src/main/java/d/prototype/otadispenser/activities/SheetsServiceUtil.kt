package d.prototype.otadispenser.activities

import android.content.Context
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.apache.ApacheHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import java.io.InputStream

object SheetsServiceUtil {
    private const val APPLICATION_NAME = "Simple USB Terminal"
    private val SCOPES = listOf("https://www.googleapis.com/auth/spreadsheets")
    private const val CREDENTIALS_FILE_NAME = "credentials.json"

    fun getSheetsService(context: Context): Sheets {
        val credential = getCredentials(context)

        return Sheets.Builder(
            ApacheHttpTransport(),  // ✅ FIXED: Use ApacheHttpTransport instead of GoogleNetHttpTransport
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private fun getCredentials(context: Context): GoogleCredential {
        val inputStream: InputStream = context.resources.openRawResource(
            context.resources.getIdentifier("credentials", "raw", context.packageName)
        )

        return GoogleCredential.fromStream(inputStream)
            .createScoped(SCOPES)
    }
}
