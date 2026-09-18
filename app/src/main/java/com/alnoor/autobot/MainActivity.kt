package com.alnoor.autobot

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val BASE = "https://auto-bot-al-noor-stores-projects.vercel.app"
    private val REQ_CALL = 101
    private val REQ_CONTACTS = 102

    private lateinit var webView: WebView
    private lateinit var nativeScreen: View
    private lateinit var inputName: EditText
    private lateinit var inputPhone: EditText
    private lateinit var statusText: TextView
    private var pendingCall = false

    // ---------- lifecycle ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        nativeScreen = findViewById(R.id.nativeScreen)
        inputName = findViewById(R.id.inputName)
        inputPhone = findViewById(R.id.inputPhone)
        statusText = findViewById(R.id.statusText)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            setSupportZoom(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.addJavascriptInterface(NativeBridge(), "AutoBotNative")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                return if (u.startsWith("tel:") || u.startsWith("https://wa.me") || u.startsWith("mailto:")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                    true
                } else false
            }
        }

        findViewById<Button>(R.id.btnCloseNative).setOnClickListener { showNative(false) }
        findViewById<Button>(R.id.btnSaveContact).setOnClickListener { saveContact() }
        findViewById<Button>(R.id.btnAutoCall).setOnClickListener { autoCall() }
        findViewById<Button>(R.id.btnWhatsApp).setOnClickListener { openWhatsApp() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadSite()
        }
    }

    // exact web UI: online → live site | offline → bundled copy (UI phir bhi poora dikhta hai)
    private fun loadSite() {
        if (isOnline()) webView.loadUrl(BASE)
        else {
            webView.loadUrl("file:///android_asset/index.html")
            Toast.makeText(this, "Offline mode — UI chalu hai, server ke features online hone pe chalein ge", Toast.LENGTH_LONG).show()
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetworkInfo
        return n != null && n.isConnected
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onBackPressed() {
        if (nativeScreen.visibility == View.VISIBLE) { showNative(false); return }
        if (webView.canGoBack()) { webView.goBack(); return }
        super.onBackPressed()
    }

    private fun showNative(show: Boolean) {
        nativeScreen.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ---------- JS bridge (website se native calls) ----------
    inner class NativeBridge {
        @JavascriptInterface
        fun openNativePanel() { runOnUiThread { showNative(true) } }

        @JavascriptInterface
        fun appStatus(): String = "Auto Bot native v1.5 — online: ${isOnline()}"

        @JavascriptInterface
        fun saveToPhoneBook(name: String, phone: String) {
            runOnUiThread {
                inputName.setText(name)
                inputPhone.setText(phone)
                saveContact()
            }
        }

        @JavascriptInterface
        fun callNumber(phone: String) {
            runOnUiThread {
                inputPhone.setText(phone)
                autoCall()
            }
        }

        @JavascriptInterface
        fun openWhatsApp(phone: String) {
            runOnUiThread {
                inputPhone.setText(phone)
                openWhatsApp()
            }
        }
    }

    // ---------- native powers ----------
    private fun cleanPhone(): String {
        var p = inputPhone.text.toString().trim().replace(Regex("[^+\\d]"), "")
        if (p.startsWith("00")) p = "+" + p.substring(2)
        if (p.length in 10..12 && !p.startsWith("+")) p = "+$p"
        return p
    }

    private fun validPhone(): Boolean {
        val p = cleanPhone()
        if (p.length < 8) { Toast.makeText(this, "Pehle number likho", Toast.LENGTH_SHORT).show(); status("Error: number missing"); return false }
        return true
    }

    private fun saveContact() {
        val name = inputName.text.toString().trim()
        val phone = cleanPhone()
        if (name.isEmpty() || phone.length < 8) { Toast.makeText(this, "Naam aur number dono likho", Toast.LENGTH_SHORT).show(); status("Error: naam/number missing"); return }
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
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val existing = c.getString(0)?.replace(Regex("[^\\d]"), "") ?: continue
                if (existing.length >= 8 && existing.takeLast(8) == tail) return true
            }
        }
        return false
    }

    private fun doSaveContact(name: String, phone: String) {
        try {
            if (isDuplicate(phone)) { Toast.makeText(this, "Ye number pehle se saved hai", Toast.LENGTH_LONG).show(); status("Duplicate: $name"); return }
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?).build())
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build())
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (results.isNotEmpty()) { Toast.makeText(this, "✅ $name phone ki contact book mein save ho gaya", Toast.LENGTH_LONG).show(); status("Saved: $name ($phone)") }
            else { Toast.makeText(this, "Save fail", Toast.LENGTH_SHORT).show(); status("Save fail") }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show(); status("Error: ${e.message}")
        }
    }

    private fun autoCall() {
        if (!validPhone()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) doCall()
        else { pendingCall = true; ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL) }
    }

    private fun doCall() {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + cleanPhone())))
            status("Calling: " + cleanPhone())
        } catch (e: Exception) { Toast.makeText(this, "Call fail: ${e.message}", Toast.LENGTH_SHORT).show(); status("Call fail") }
    }

    private fun openWhatsApp() {
        if (!validPhone()) return
        val num = cleanPhone().replace("+", "")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
            status("WhatsApp: $num")
        } catch (e: Exception) { Toast.makeText(this, "WhatsApp fail: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission chahiye — Settings se allow karo", Toast.LENGTH_LONG).show(); status("Permission denied"); return
        }
        when (requestCode) {
            REQ_CALL -> doCall()
            REQ_CONTACTS -> saveContact()
        }
    }

    private fun status(msg: String) { statusText.text = msg }
}
