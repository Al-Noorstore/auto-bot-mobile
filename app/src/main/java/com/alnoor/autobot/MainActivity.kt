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
import android.widget.ScrollView
import org.json.JSONObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CrashLogger(private val ctx: Context) : Thread.UncaughtExceptionHandler {
    private val prev = Thread.getDefaultUncaughtExceptionHandler()
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val log = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date()) + "\n" + e::class.java.name + ": " + e.message + "\n" +
                e.stackTraceToString().take(4000) + "\n\n"
            java.io.File(ctx.getExternalFilesDir(null), "crash_log.txt").appendText(log)
        } catch (_: Exception) {}
        prev?.uncaughtException(t, e)
    }
}

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
    private lateinit var terminalScreen: View
    private lateinit var termOut: TextView
    private lateinit var termScroll: ScrollView
    private lateinit var termIn: EditText
    private val termBuf = StringBuilder()

    // ---------- Python 3.11 engine (APK ke andar bundled) ----------
    private var pipDir: String = ""
    private var currentProject: File = File("")

    private fun pythonReady(): Boolean {
        return try {
            Python.getInstance()
            true
        } catch (e: Exception) { false }
    }

    private fun runPython(code: String, fromChat: Boolean = false) {
        appendTerm("\n>>> $code\n")
        if (!pythonReady()) { val m = "❌ Python engine load nahi hui (install/storage check karo)"; appendTerm(m + "\n"); if (fromChat) chatReply(m); return }
        Thread {
            var out = ""
            try {
                val runner = Python.getInstance().getModule("runner")
                if (pipDir.isEmpty()) pipDir = File(getExternalFilesDir(null), "pip").absolutePath
                val wd = if (currentProject.exists()) currentProject.absolutePath else null
                out = runner.callAttr("run_code", code, wd).toString()
            } catch (e: Exception) { out = "Python error: " + e.message }
            val res = out.trim().take(3000)
            runOnUiThread {
                appendTerm(res + "\n")
                if (fromChat) chatReply(res)
            }
        }.start()
    }

    private fun pipInstall(pkg: String, fromChat: Boolean = false) {
        appendTerm("\n$ pip install $pkg\n")
        if (!pythonReady()) { val m = "❌ Python engine load nahi hui"; appendTerm(m + "\n"); if (fromChat) chatReply(m); return }
        Thread {
            var out = ""
            try {
                val target = if (currentProject.exists()) File(currentProject, "libs").absolutePath
                            else File(getExternalFilesDir(null), "pip").absolutePath
                val runner = Python.getInstance().getModule("runner")
                out = runner.callAttr("pip_install", pkg.trim(), target).toString()
            } catch (e: Exception) { out = "pip error: " + e.message }
            val res = out.trim().take(3000)
            runOnUiThread {
                appendTerm(res + "\n")
                if (fromChat) chatReply(res)
            }
        }.start()
    }

    // shell engine: real Android sh, background mein bot bhi use karta hai
    private fun runShell(cmd: String, fromChat: Boolean = false, label: String = "$") {
        appendTerm("\n$ $cmd\n")
        Thread {
            var out = ""
            try {
                val cwd = File(getExternalFilesDir(null), "work").apply { mkdirs() }
                if (cmd.trim().startsWith("py ")) { runPython(cmd.trim().substring(3).removeSurrounding("\""), fromChat); return@Thread }
                if (cmd.trim().startsWith("pip install ")) { pipInstall(cmd.trim().substring(12), fromChat); return@Thread }
                val p = ProcessBuilder("sh", "-c", cmd)
                    .directory(cwd)
                    .redirectErrorStream(true)
                    .start()
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                val sb = StringBuilder()
                var line: String? = reader.readLine()
                var count = 0
                while (line != null && count < 500) { sb.append(line).append("\n"); line = reader.readLine(); count++ }
                val done = try { p.waitFor() == 0 } catch (e: Exception) { false }
                out = sb.toString().ifBlank { "(no output, exit ok)" }
                reader.close(); p.destroy()
            } catch (e: Exception) { out = "Error: " + e.message }
            val res = out.trim().take(3000)
            runOnUiThread {
                appendTerm(res + "\n")
                if (fromChat) chatReply(if (res.isEmpty() || res == "(no output, exit ok)") "✅ Command chal gaya: $cmd" else "\n$res")
            }
        }.start()
    }

    private fun appendTerm(text: String) {
        termBuf.append(text)
        runOnUiThread {
            termOut.text = termBuf.toString()
            termScroll.post { termScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun chatReply(text: String) {
        try {
            webView.evaluateJavascript("window.__localBotReply(" + JSONObject.quote(text) + ")", null)
        } catch (e: Exception) { appendTerm("[chat-reply-fail]\n" + text) }
    }

    private fun showTerminal(show: Boolean) {
        terminalScreen.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openAppByName(name: String): String {
        val n = name.trim().lowercase()
        val pkgMap = mapOf(
            "whatsapp" to "com.whatsapp", "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome", "browser" to "com.android.chrome",
            "gmail" to "com.google.android.gm", "email" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps", "playstore" to "com.android.vending",
            "play store" to "com.android.vending", "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos", "camera" to "com.android.camera2",
            "facebook" to "com.facebook.katana", "instagram" to "com.instagram.android",
            "tiktok" to "com.zhiliaoapp.musically", "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger", "settings" to "com.android.settings"
        )
        var pkg = pkgMap[n] ?: if (n.contains(".")) n else null
        if (pkg == null) { // fuzzy: koi bhi installed app jiska naam match kare
            for (pi in packageManager.getInstalledPackages(0)) {
                val lbl = pi.applicationInfo.loadLabel(packageManager).toString().lowercase()
                if (lbl.contains(n)) { pkg = pi.packageName; break }
            }
        }
        if (pkg == null) return "App nahi mili: $name. Spelling check karo ya package naam do (e.g. open com.whatsapp)."
        return try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return "App installed nahi hai: $pkg"
            startActivity(intent)
            "✅ App khul gayi: $name"
        } catch (e: Exception) { "App open fail: " + e.message }
    }

    private fun openUrl(url: String) { try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))); } catch (e: Exception) {} }

    // ---------- lifecycle ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
        setContentView(R.layout.activity_main)
        try {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        } catch (e: Exception) {
            Toast.makeText(this, "Python engine load fail: " + e.message, Toast.LENGTH_LONG).show()
        }

        webView = findViewById(R.id.webView)
        nativeScreen = findViewById(R.id.nativeScreen)
        inputName = findViewById(R.id.inputName)
        inputPhone = findViewById(R.id.inputPhone)
        statusText = findViewById(R.id.statusText)
        terminalScreen = findViewById(R.id.terminalScreen)
        termOut = findViewById(R.id.termOut)
        termScroll = findViewById(R.id.termScroll)
        termIn = findViewById(R.id.termIn)
        findViewById<Button>(R.id.btnTermRun).setOnClickListener { runShell(termIn.text.toString().trim()); termIn.setText("") }
        findViewById<Button>(R.id.btnTermClear).setOnClickListener { termBuf.setLength(0); termOut.text = "" }
        findViewById<Button>(R.id.btnTermClose).setOnClickListener { showTerminal(false) }
        appendTerm("Auto Bot Terminal v1.6 — real Android shell (sh)\nWorking dir: " + File(getExternalFilesDir(null), "work").absolutePath + "\nShell: ls, mkdir, echo, cat, rm, cp, mv, ps, df...\nPython 3.11 BUILT-IN: 'py print(2+2)' | 'py import requests'\nPip: 'pip install <package>' (pure-python packages)\nChalo koi bhi command do!\n")

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

    private fun runCommand(low: String, msg: String): Boolean {
        if (low == "terminal" || low == "open terminal") { runOnUiThread { showTerminal(true) }; chatReply("🖥 Terminal khul gaya — screen pe command likho."); return true }
        if (low.startsWith("run ")) { runShell(msg.substring(4).trim(), fromChat = true); chatReply("⏳ Command chal raha hai terminal mein..."); return true }
        if (low.startsWith("python ") || low.startsWith("py ")) { runPython(msg.substring(low.indexOf(' ') + 1).trim(), fromChat = true); return true }
        if (low.startsWith("project ")) {
            val name = msg.substring(8).trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
            currentProject = File(getExternalFilesDir(null), "work/" + name).apply { mkdirs() }
            File(currentProject, "libs").mkdirs()
            val proj = if (name.isEmpty()) "main" else name
            chatReply("📁 Project '" + proj + "' ready!\nPath: " + currentProject.absolutePath + "\nAb 'py ...' isi project mein chalega, aur 'pip install <pkg>' isi ke libs/ mein install hoga."); return true
        }
        if (low.startsWith("pip install ")) { pipInstall(msg.substring(12).trim(), fromChat = true); chatReply("⏳ pip install chal raha hai..."); return true }
        if (low.startsWith("cmd ")) { runShell(msg.substring(4).trim(), fromChat = true); chatReply("⏳ Command chal raha hai terminal mein..."); return true }
        if (low.startsWith("open ")) {
            val target = msg.substring(5).trim()
            return if (target.startsWith("http")) { runOnUiThread { openUrl(target) }; chatReply("🌐 Khol diya: $target"); true }
            else { val r = openAppByName(target); chatReply(r); true }
        }
        if (low.startsWith("youtube ") || low.startsWith("play ")) {
            val q = msg.substring(low.indexOf(' ') + 1).trim()
            runOnUiThread { openUrl("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(q, "UTF-8")) }
            chatReply("▶️ YouTube pe khel raha hoon: \"$q\" — pehla video kholne ke liye bola jao to \"play now\" likho."); return true
        }
        if (low == "play" || low == "play now") { runOnUiThread { openUrl("https://www.youtube.com") }; chatReply("▶️ YouTube khul gaya."); return true }
        if (low.startsWith("search ")) {
            val q = msg.substring(7).trim()
            runOnUiThread { openUrl("https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")) }
            chatReply("🔍 Google pe search khol diya: $q"); return true
        }
        if (low.startsWith("call ")) { runOnUiThread { inputPhone.setText(msg.substring(5).trim()); autoCall() }; chatReply("📞 Call kar raha hoon..."); return true }
        if (low.startsWith("wa ") || low.startsWith("whatsapp ")) {
            val q = msg.substring(low.indexOf(' ') + 1).trim()
            runOnUiThread { openUrl("https://wa.me/" + q.replace(Regex("[^0-9]"), "")) }
            chatReply("💬 WhatsApp chat khul rahi hai..."); return true
        }
        if (low.startsWith("mkdir ")) { runShell("mkdir -p " + msg.substring(6).trim(), fromChat = true); chatReply("📁 Folder ban raha hai..."); return true }
        if (low.startsWith("file ")) { runShell("touch " + msg.substring(5).trim(), fromChat = true); chatReply("📄 File ban rahi hai..."); return true }
        return false
    }

    override fun onBackPressed() {
        if (terminalScreen.visibility == View.VISIBLE) { showTerminal(false); return }
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
        fun openTerminal() { runOnUiThread { showTerminal(true) } }

        // website chat se local commands — ye bot ko powerful banata hai
        @JavascriptInterface
        fun handleChatCommand(msg: String): Boolean {
            val m = msg.trim()
            val low = m.lowercase()
            return runCommand(low, m)
        }

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
