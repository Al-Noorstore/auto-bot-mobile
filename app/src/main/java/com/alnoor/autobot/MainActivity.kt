package com.alnoor.autobot

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val BASE = "https://auto-bot-al-noor-stores-projects.vercel.app"
    private val REQ_CALL = 101
    private val REQ_CONTACTS = 102

    private lateinit var chatScreen: View
    private lateinit var listScreen: View
    private lateinit var nativeScreen: View
    private lateinit var msgContainer: LinearLayout
    private lateinit var chatScroll: android.widget.ScrollView
    private lateinit var inputMsg: EditText
    private lateinit var listTitle: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var actionArea: LinearLayout
    private lateinit var sidebar: View
    private lateinit var dim: View
    private lateinit var inputName: EditText
    private lateinit var inputPhone: EditText
    private lateinit var statusText: TextView

    // ============ lifecycle ============
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatScreen = findViewById(R.id.chatScreen)
        listScreen = findViewById(R.id.listScreen)
        nativeScreen = findViewById(R.id.nativeScreen)
        msgContainer = findViewById(R.id.msgContainer)
        chatScroll = findViewById(R.id.chatScroll)
        inputMsg = findViewById(R.id.inputMsg)
        listTitle = findViewById(R.id.listTitle)
        listContainer = findViewById(R.id.listContainer)
        actionArea = findViewById(R.id.actionArea)
        sidebar = findViewById(R.id.sidebar)
        dim = findViewById(R.id.dim)
        inputName = findViewById(R.id.inputName)
        inputPhone = findViewById(R.id.inputPhone)
        statusText = findViewById(R.id.statusText)

        buildSidebar()
        findViewById<Button>(R.id.btnMenu).setOnClickListener { toggleSidebar(true) }
        dim.setOnClickListener { toggleSidebar(false) }
        findViewById<Button>(R.id.btnNative).setOnClickListener { showScreen("native") }
        findViewById<Button>(R.id.btnSend).setOnClickListener { sendChat() }
        inputMsg.setOnEditorActionListener { _, _, _ -> sendChat(); true }

        findViewById<Button>(R.id.btnSaveContact).setOnClickListener { saveContact() }
        findViewById<Button>(R.id.btnAutoCall).setOnClickListener { autoCall() }
        findViewById<Button>(R.id.btnWhatsApp).setOnClickListener { openWhatsApp() }

        botSay("Salam Mr Wishal! 👋 Main Auto Bot hoon. Commands try karo: \"task product hunt daily US\", \"contact Ali 03001234567\", \"rule ...\", \"remember ...\", \"status\". Sidebar se Tasks, Approvals, Contacts, Rules, Memory sab manage karo.")
    }

    override fun onBackPressed() {
        if (sidebar.visibility == View.VISIBLE) { toggleSidebar(false); return }
        if (listScreen.visibility == View.VISIBLE || nativeScreen.visibility == View.VISIBLE) {
            showScreen("chat"); return
        }
        super.onBackPressed()
    }

    // ============ sidebar ============
    private fun menuBtn(label: String, tag: String): Button {
        val b = Button(this)
        b.text = label
        b.tag = tag
        b.setTextColor(Color.parseColor("#E6EDF3"))
        b.textSize = 15f
        b.background = null
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.setPadding(40, 28, 28, 28)
        b.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        b.setOnClickListener {
            toggleSidebar(false)
            val t = b.tag as String
            if (t == "newchat") { msgContainer.removeAllViews(); showScreen("chat") }
            else if (t == "native") showScreen("native")
            else openPanel(t)
        }
        return b
    }

    private fun buildSidebar() {
        val menu = findViewById<LinearLayout>(R.id.sidebarMenu)
        menu.addView(menuBtn("➕ New Chat", "newchat"))
        menu.addView(menuBtn("💬 Chat", "chat"))
        menu.addView(menuBtn("🕒 Tasks", "tasks"))
        menu.addView(menuBtn("✅ Approvals", "approvals"))
        menu.addView(menuBtn("📞 Contacts", "contacts"))
        menu.addView(menuBtn("🚀 Product Hunt", "product-hunt"))
        menu.addView(menuBtn("🔍 Find Buyers", "find-buyers"))
        menu.addView(menuBtn("📜 Rules", "rules"))
        menu.addView(menuBtn("🧠 Memory", "memory"))
        menu.addView(menuBtn("📱 Native Powers", "native"))
    }

    private fun toggleSidebar(open: Boolean) {
        sidebar.visibility = if (open) View.VISIBLE else View.GONE
        dim.visibility = if (open) View.VISIBLE else View.GONE
    }

    // ============ screens ============
    private fun showScreen(mode: String) {
        chatScreen.visibility = if (mode == "chat") View.VISIBLE else View.GONE
        listScreen.visibility = if (mode == "list") View.VISIBLE else View.GONE
        nativeScreen.visibility = if (mode == "native") View.VISIBLE else View.GONE
    }

    private fun openPanel(tag: String) {
        showScreen("list")
        actionArea.removeAllViews()
        listContainer.removeAllViews()
        when (tag) {
            "chat" -> { showScreen("chat"); return }
            "tasks" -> { listTitle.text = "🕒 Tasks"; loadTasks() }
            "approvals" -> { listTitle.text = "✅ Approvals"; loadApprovals() }
            "contacts" -> { listTitle.text = "📞 Contacts"; loadContacts() }
            "rules" -> { listTitle.text = "📜 Rules (permanent)"; loadRules() }
            "memory" -> { listTitle.text = "🧠 Memory"; loadMemory() }
            "product-hunt" -> { listTitle.text = "🚀 Product Hunt"; productHuntUI() }
            "find-buyers" -> { listTitle.text = "🔍 Find Buyers"; findBuyersUI() }
        }
    }

    // ============ HTTP ============
    private fun api(path: String, method: String = "GET", body: JSONObject? = null, cb: (JSONObject?) -> Unit) {
        thread {
            var result: JSONObject? = null
            try {
                val conn = URL("$BASE/api/$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.requestMethod = method
                if (method == "POST") {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    body?.let { conn.outputStream.write(it.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val reader = BufferedReader(InputStreamReader(if (code < 400) conn.inputStream else conn.errorStream))
                val sb = StringBuilder(); var line: String? = reader.readLine()
                while (line != null) { sb.append(line); line = reader.readLine() }
                reader.close()
                result = try { JSONObject(sb.toString()) } catch (e: Exception) { JSONObject().put("error", sb.toString()) }
            } catch (e: Exception) {
                result = JSONObject().put("error", "Network: ${e.message}")
            }
            runOnUiThread { cb(result) }
        }
    }

    // ============ chat ============
    private fun sendChat() {
        val msg = inputMsg.text.toString().trim()
        if (msg.isEmpty()) return
        inputMsg.setText("")
        userSay(msg)
        botSay("...")
        api("chat", "POST", JSONObject().put("message", msg)) { r ->
            msgContainer.removeViewAt(msgContainer.childCount - 1)
            val reply = r?.optString("reply") ?: r?.optString("error") ?: "No response"
            botSay(reply)
        }
    }

    private fun userSay(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.setTextSize(15f)
        tv.background = ColorDrawable("#1E3A5F")
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.END
        lp.setMargins(60, 8, 8, 8)
        tv.layoutParams = lp
        tv.setPadding(32, 20, 32, 20)
        msgContainer.addView(tv)
        chatScroll.post { chatScroll.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
    }

    private fun botSay(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#E6EDF3"))
        tv.setTextSize(15f)
        tv.background = ColorDrawable("#1F2937")
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(8, 8, 60, 8)
        tv.layoutParams = lp
        tv.setPadding(32, 20, 32, 20)
        msgContainer.addView(tv)
        chatScroll.post { chatScroll.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
    }

    // helper for solid color background (API-safe)
    private fun ColorDrawable(hex: String): android.graphics.drawable.ColorDrawable =
        android.graphics.drawable.ColorDrawable(Color.parseColor(hex))

    // ============ list row helpers ============
    private fun cardView(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = ColorDrawable("#161D28")
        card.setPadding(28, 24, 28, 24)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, 12)
        card.layoutParams = lp
        return card
    }

    private fun cardTitle(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#E6EDF3"))
        tv.setTextSize(16f)
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return tv
    }

    private fun cardSub(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#8B98A9"))
        tv.setTextSize(13f)
        tv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return tv
    }

    private fun actionBtn(label: String, color: String): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(Color.parseColor(color))
        b.setTextSize(12f)
        b.background = ColorDrawable("#0F1720")
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(0, 0, 8, 0)
        b.layoutParams = lp
        return b
    }

    private fun btnRow(card: LinearLayout): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 20, 0, 0)
        card.addView(row)
        return row
    }

    private fun input(hint: String): EditText {
        val et = EditText(this)
        et.hint = hint
        et.setTextColor(Color.parseColor("#E6EDF3"))
        et.setHintTextColor(Color.parseColor("#5B6675"))
        et.textSize = 14f
        et.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return et
    }

    private fun addAreaBtn(label: String): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(Color.parseColor("#0B0F14"))
        b.background = ColorDrawable("#22D3EE")
        b.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return b
    }

    // ============ panels ============
    private fun loadTasks() {
        listContainer.addView(cardSub("Loading..."))
        api("tasks") { r ->
            listContainer.removeAllViews()
            val arr = r?.optJSONArray("tasks") ?: JSONArray()
            if (arr.length() == 0) { listContainer.addView(cardSub("Koi task nahi. Chat mein likho: \"task product hunt daily US\"")); return@api }
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val card = cardView()
                card.addView(cardTitle("🚀 ${t.optString("type")}  ${if (t.optBoolean("active")) "● running" else "‖ paused"}"))
                card.addView(cardSub("Schedule: ${t.optString("schedule")} | Geo: ${t.optJSONObject("config")?.optString("geo") ?: "-"}"))
                val row = btnRow(card)
                val bPause = actionBtn(if (t.optBoolean("active")) "Pause" else "Resume", "#FBBF24")
                val bDel = actionBtn("Delete", "#F87171")
                bPause.setOnClickListener { api("tasks", "POST", JSONObject().put("id", t.opt("id")).put("action", if (t.optBoolean("active")) "pause" else "resume")) { loadTasks() } }
                bDel.setOnClickListener { api("tasks", "POST", JSONObject().put("id", t.opt("id")).put("action", "delete")) { loadTasks() } }
                row.addView(bPause); row.addView(bDel)
                listContainer.addView(card)
            }
        }
    }

    private fun loadApprovals() {
        listContainer.addView(cardSub("Loading..."))
        api("approvals") { r ->
            listContainer.removeAllViews()
            val arr = r?.optJSONArray("approvals") ?: JSONArray()
            if (arr.length() == 0) { listContainer.addView(cardSub("Koi pending approval nahi ✅")); return@api }
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                if (a.optString("status") != "pending") continue
                val card = cardView()
                card.addView(cardTitle("⏳ ${a.optString("type")}"))
                card.addView(cardSub(a.optString("title", a.optString("summary", a.optString("detail", "")))))
                val row = btnRow(card)
                val bOk = actionBtn("✓ Approve", "#34D399")
                val bNo = actionBtn("✗ Reject", "#F87171")
                bOk.setOnClickListener { api("approvals", "POST", JSONObject().put("id", a.opt("id")).put("decision", "approve")) { loadApprovals() } }
                bNo.setOnClickListener { api("approvals", "POST", JSONObject().put("id", a.opt("id")).put("decision", "reject")) { loadApprovals() } }
                row.addView(bOk); row.addView(bNo)
                listContainer.addView(card)
            }
        }
    }

    private fun loadContacts() {
        // add form
        val etName = input("Naam")
        val etPhone = input("Number (+923001234567)")
        val etNote = input("Note (optional)")
        val bAdd = addAreaBtn("➕ Save Contact")
        actionArea.addView(etName); actionArea.addView(etPhone); actionArea.addView(etNote); actionArea.addView(bAdd)
        bAdd.setOnClickListener {
            api("contacts", "POST", JSONObject().put("name", etName.text.toString().trim()).put("phone", etPhone.text.toString().trim()).put("note", etNote.text.toString().trim())) { r ->
                toast(r?.optString("error") ?: "Saved ✅"); loadContacts()
            }
        }
        listContainer.addView(cardSub("Loading..."))
        api("contacts") { r ->
            listContainer.removeAllViews()
            val arr = r?.optJSONArray("contacts") ?: JSONArray()
            if (arr.length() == 0) { listContainer.addView(cardSub("Koi contact nahi. Chat mein: \"contact Ali 03001234567\"")); return@api }
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val phone = c.optString("phone")
                val card = cardView()
                card.addView(cardTitle("👤 ${c.optString("name")}"))
                card.addView(cardSub("$phone ${if (c.optString("note").isNotEmpty()) "| ${c.optString("note")}" else ""}"))
                val row = btnRow(card)
                val bCall = actionBtn("📞 Call", "#34D399")
                val bWa = actionBtn("💬 WA", "#22D3EE")
                val bDel = actionBtn("🗑", "#F87171")
                bCall.setOnClickListener { inputPhone.setText(phone); autoCall() }
                bWa.setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone.replace("+", "")))) } catch (e: Exception) { toast("WhatsApp fail") } }
                bDel.setOnClickListener { api("contacts", "POST", JSONObject().put("id", c.opt("id")).put("action", "delete")) { loadContacts() } }
                row.addView(bCall); row.addView(bWa); row.addView(bDel)
                listContainer.addView(card)
            }
        }
    }

    private fun loadRules() {
        val etRule = input("Naya rule likho...")
        val bAdd = addAreaBtn("➕ Add Rule (permanent)")
        actionArea.addView(etRule); actionArea.addView(bAdd)
        bAdd.setOnClickListener {
            val rule = etRule.text.toString().trim()
            if (rule.isEmpty()) { toast("Rule likho"); return@setOnClickListener }
            api("rules", "POST", JSONObject().put("action", "add").put("rule", rule)) { loadRules() }
        }
        listContainer.addView(cardSub("Loading..."))
        api("rules") { r ->
            listContainer.removeAllViews()
            val arr = r?.optJSONArray("rules") ?: JSONArray()
            if (arr.length() == 0) { listContainer.addView(cardSub("Koi rule nahi")); return@api }
            for (i in 0 until arr.length()) {
                val card = cardView()
                card.addView(cardTitle("📜 Rule #${i + 1}"))
                card.addView(cardSub(arr.optString(i)))
                listContainer.addView(card)
            }
        }
    }

    private fun loadMemory() {
        val etMem = input("Yaad rakhne wali baat likho...")
        val bAdd = addAreaBtn("🧠 Remember")
        actionArea.addView(etMem); actionArea.addView(bAdd)
        bAdd.setOnClickListener {
            val text = etMem.text.toString().trim()
            if (text.isEmpty()) { toast("Text likho"); return@setOnClickListener }
            api("memory", "POST", JSONObject().put("text", text)) { loadMemory() }
        }
        listContainer.addView(cardSub("Loading..."))
        api("memory") { r ->
            listContainer.removeAllViews()
            val arr = r?.optJSONArray("memories") ?: JSONArray()
            if (arr.length() == 0) { listContainer.addView(cardSub("Memory khali hai")); return@api }
            for (i in arr.length() - 1 downTo 0) {
                val m = arr.getJSONObject(i)
                val card = cardView()
                card.addView(cardSub(m.optString("text")))
                listContainer.addView(card)
            }
        }
    }

    private fun productHuntUI() {
        val etGeo = input("Geo: US, UK, PK...")
        etGeo.setText("US")
        val etNiche = input("Niche (optional, e.g. pet products)")
        val bRun = addAreaBtn("🚀 Hunt Products")
        actionArea.addView(etGeo); actionArea.addView(etNiche); actionArea.addView(bRun)
        bRun.setOnClickListener {
            listContainer.removeAllViews()
            listContainer.addView(cardSub("🔍 Hunting ${etNiche.text} in ${etGeo.text}..."))
            api("product-hunt", "POST", JSONObject().put("geo", etGeo.text.toString().trim()).put("niche", etNiche.text.toString().trim())) { r ->
                listContainer.removeAllViews()
                val arr = r?.optJSONArray("products") ?: r?.optJSONArray("trends") ?: JSONArray()
                if (arr.length() == 0) { listContainer.addView(cardSub(r?.toString() ?: "No results")); return@api }
                for (i in 0 until arr.length()) {
                    val p = arr.opt(i)
                    val card = cardView()
                    val name = if (p is JSONObject) p.optString("name", p.optString("title", p.toString())) else p.toString()
                    card.addView(cardTitle("🚀 ${i + 1}. $name"))
                    if (p is JSONObject && p.has("why")) card.addView(cardSub(p.optString("why")))
                    listContainer.addView(card)
                }
            }
        }
        listContainer.addView(cardSub("Geo aur niche likho, phir Hunt dabao"))
    }

    private fun findBuyersUI() {
        val etNiche = input("Niche (e.g. custom t-shirts)")
        val bRun = addAreaBtn("🔍 Find Buyers")
        actionArea.addView(etNiche); actionArea.addView(bRun)
        bRun.setOnClickListener {
            val niche = etNiche.text.toString().trim()
            if (niche.isEmpty()) { toast("Niche likho"); return@setOnClickListener }
            listContainer.removeAllViews()
            listContainer.addView(cardSub("🔍 Buyers dhoond raha hoon ($niche)..."))
            api("find-buyers", "POST", JSONObject().put("niche", niche)) { r ->
                listContainer.removeAllViews()
                val arr = r?.optJSONArray("buyers") ?: r?.optJSONArray("results") ?: JSONArray()
                if (arr.length() == 0) { listContainer.addView(cardSub(r?.toString() ?: "No results")); return@api }
                for (i in 0 until arr.length()) {
                    val b = arr.optJSONObject(i) ?: continue
                    val card = cardView()
                    card.addView(cardTitle("🛒 ${b.optString("name", b.optString("buyer", "Buyer"))}"))
                    card.addView(cardSub(b.optString("detail", b.optString("why", b.toString()))))
                    listContainer.addView(card)
                }
            }
        }
        listContainer.addView(cardSub("Niche likho, phir Find Buyers dabao"))
    }

    // ============ native powers ============
    private fun cleanPhone(): String {
        var p = inputPhone.text.toString().trim().replace(Regex("[^+\\d]"), "")
        if (p.startsWith("00")) p = "+" + p.substring(2)
        if (p.length in 10..12 && !p.startsWith("+")) p = "+$p"
        return p
    }

    private fun validPhone(): Boolean {
        val p = cleanPhone()
        if (p.length < 8) { toast("Pehle number likho"); status("Error: number missing"); return false }
        return true
    }

    private fun saveContact() {
        val name = inputName.text.toString().trim()
        val phone = cleanPhone()
        if (name.isEmpty() || phone.length < 8) { toast("Naam aur number dono likho"); status("Error: naam/number missing"); return }
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
            if (isDuplicate(phone)) { toast("Ye number pehle se saved hai"); status("Duplicate: $name"); return }
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
            if (results.isNotEmpty()) { toast("✅ $name phone ki contact book mein save ho gaya"); status("Saved: $name ($phone)") }
            else { toast("Save fail"); status("Save fail") }
        } catch (e: Exception) { toast("Error: ${e.message}"); status("Error: ${e.message}") }
    }

    private fun autoCall() {
        if (!validPhone()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) doCall()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
    }

    private fun doCall() {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + cleanPhone())))
            status("Calling: " + cleanPhone())
        } catch (e: Exception) { toast("Call fail: ${e.message}"); status("Call fail") }
    }

    private fun openWhatsApp() {
        if (!validPhone()) return
        val num = cleanPhone().replace("+", "")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
            status("WhatsApp: $num")
        } catch (e: Exception) { toast("WhatsApp fail: ${e.message}") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("Permission chahiye — Settings se allow karo"); status("Permission denied"); return
        }
        when (requestCode) {
            REQ_CALL -> doCall()
            REQ_CONTACTS -> saveContact()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun status(msg: String) { statusText.text = msg }
}
