package com.alnoor.autobot

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var inputName: EditText
    private lateinit var inputPhone: EditText
    private lateinit var inputServerUrl: EditText
    private lateinit var statusText: TextView

    private val REQ_CALL = 101
    private val REQ_CONTACTS = 102
    private val PREFS = "autobot_prefs"
    private val KEY_URL = "server_url"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputName = findViewById(R.id.inputName)
        inputPhone = findViewById(R.id.inputPhone)
        inputServerUrl = findViewById(R.id.inputServerUrl)
        statusText = findViewById(R.id.statusText)

        val savedUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URL, "")
        inputServerUrl.setText(savedUrl)

        findViewById<Button>(R.id.btnSaveContact).setOnClickListener { saveContact() }
        findViewById<Button>(R.id.btnAutoCall).setOnClickListener { autoCall() }
        findViewById<Button>(R.id.btnWhatsApp).setOnClickListener { openWhatsApp() }
        findViewById<Button>(R.id.btnDashboard).setOnClickListener { openDashboard() }
    }

    private fun cleanPhone(): String {
        var p = inputPhone.text.toString().trim().replace(Regex("[^+\\d]"), "")
        if (p.startsWith("00")) p = "+" + p.substring(2)
        if (p.length in 10..12 && !p.startsWith("+")) p = "+$p"
        return p
    }

    private fun validPhone(): Boolean {
        val p = cleanPhone()
        if (p.length < 8) {
            toast("Pehle number likho"); status("Error: number missing")
            return false
        }
        return true
    }

    // ---------- SAVE TO REAL PHONE CONTACT BOOK ----------
    private fun saveContact() {
        val name = inputName.text.toString().trim()
        val phone = cleanPhone()
        if (name.isEmpty() || phone.length < 8) {
            toast("Naam aur number dono likho"); status("Error: naam/number missing")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            doSaveContact(name, phone)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_CONTACTS), REQ_CONTACTS)
        }
    }

    private fun isDuplicate(phone: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val target = phone.replace(Regex("[^\\d]"), "")
        if (target.length < 8) return false
        val tail = target.takeLast(8)
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val existing = c.getString(0)?.replace(Regex("[^\\d]"), "") ?: continue
                if (existing.length >= 8 && existing.takeLast(8) == tail) return true
            }
        }
        return false
    }

    private fun doSaveContact(name: String, phone: String) {
        try {
            if (isDuplicate(phone)) {
                toast("Ye number pehle se saved hai"); status("Duplicate: $name")
                return
            }
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
                .build())
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build())
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())

            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (results.isNotEmpty()) {
                toast("✅ $name phone ki contact book mein save ho gaya")
                status("Saved: $name ($phone)")
            } else {
                toast("Save fail"); status("Save fail")
            }
        } catch (e: Exception) {
            toast("Error: ${e.message}")
            status("Error: ${e.message}")
        }
    }

    // ---------- AUTO CALL (bina dialer ke, direct) ----------
    private fun autoCall() {
        if (!validPhone()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            doCall()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
        }
    }

    private fun doCall() {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + cleanPhone())))
            status("Calling: " + cleanPhone())
        } catch (e: Exception) {
            toast("Call fail: ${e.message}"); status("Call fail")
        }
    }

    // ---------- WHATSAPP ----------
    private fun openWhatsApp() {
        if (!validPhone()) return
        val num = cleanPhone().replace("+", "")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
            status("WhatsApp: $num")
        } catch (e: Exception) {
            toast("WhatsApp fail: ${e.message}")
        }
    }

    // ---------- DASHBOARD ----------
    private fun openDashboard() {
        val url = inputServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            toast("Dashboard URL likho (deploy ke baad)"); status("URL missing")
            return
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            status("Dashboard: $url")
        } catch (e: Exception) {
            toast("URL fail: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("Permission chahiye — Settings se allow karo"); status("Permission denied")
            return
        }
        when (requestCode) {
            REQ_CALL -> doCall()
            REQ_CONTACTS -> saveContact()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun status(msg: String) { statusText.text = msg }
}
