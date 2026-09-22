package com.alnoor.autobot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * OFFLINE BRAIN — bina API key / bina Ollama model ke bhi ye commands samajhta hai:
 *  - contact save (naam + number + relation: bhai/behen/mamo/khala/...)
 *  - call <naam ya relation>, multiple match ho to confirm karta hai
 *  - call end / drop / kat do
 *  - <naam> ka number batao (+ chat mein call button)
 *  - lock my phone (device admin)
 *  - app lock / koi bhi app kholna (password vault ke saath)
 *  - <kisi> ka password save / batao (private, sirf isi phone mein rehta hai)
 * Relations Roman Urdu + English dono samajhta hai.
 */
object OfflineBrain {

    // ---------- relation dictionary ----------
    val REL_WORDS = arrayOf(
        "bhai", "bhaijaan", "bhaijan", "brother", "behen", "behn", "sister", "chhoti", "choti", "chhotibehen",
        "bari", "baribehen", "chhotibhai", "barabhai", "mamo", "mamoo", "mamu", "mamun",
        "khala", "khalaji", "khalu", "chacha", "chachaji", "chachu", "taya", "tayaji",
        "phupho", "phuphaji", "phopo", "abu", "abbu", "dad", "daddy", "father", "papa", "baba", "walid",
        "ammi", "ummi", "mom", "mummy", "mother", "amma", "walida", "nana", "nani", "dada", "dadi",
        "uncle", "aunty", "aunt", "cousin", "cousinbhai", "cousinsister", "dost", "friend",
        "bhanja", "bhanji", "bhatije", "bhatiji", "beta", "beti", "biwi", "wife", "shohar", "husband",
        "bhaiyya", "bhaiya", "apa", "aapa"
    )

    val STOP_WORDS = setOf(
        "ka", "ki", "ke", "ko", "hai", "ha", "hain", "ye", "yeh", "is", "mera", "meri", "mere", "my", "the",
        "save", "karo", "kro", "karna", "krdo", "kardo", "kr", "do", "dijiye", "please", "number",
        "nomber", "num", "no", "no.", "contact", "contacts", "add", "ek", "aik", "hi", "wale", "wali",
        "phone", "mobile", "par", "pe", "per", "in", "on", "of", "and", "aur", "bhi", "to", "toh", "se",
        "vala", "wala", "relation", "call", "calling", "dial", "saved", "name", "naam"
    )

    val END_CALL_WORDS = arrayOf("end", "cut", "kat", "kato", "kaat", "drop", "band", "close", "rok")
    val TELL_WORDS = arrayOf("batao", "bata", "bataa", "tell", "show", "dikha", "dikhaao", "dikhao", "kya", "what", "kaunsa", "which")
    val SAVE_WORDS = arrayOf("save", "add", "store", "yaad", "rakh", "rakho", "rakho", "yaad", "sambhal")
    val OPEN_WORDS = arrayOf("open", "khol", "khola", "kholo", "launch", "chalu", "start")

    class BrainContact(val name: String, val phone: String, val relation: String?)

    class BrainButton(val label: String, val action: String, val phone: String = "")
    class BrainAction(val action: String, val arg: String = "", val arg2: String = "")

    class BrainReply {
        var text: String = ""
        val buttons = ArrayList<BrainButton>()
        val actions = ArrayList<BrainAction>()
        fun buttonsJson(): String {
            val arr = JSONArray()
            for (b in buttons) {
                val o = JSONObject()
                o.put("label", b.label); o.put("action", b.action); o.put("phone", b.phone)
                arr.put(o)
            }
            return arr.toString()
        }
    }

    // ---------- storage (private, sirf isi phone mein) ----------
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("autobot", Context.MODE_PRIVATE)

    fun loadContacts(ctx: Context): MutableList<BrainContact> {
        val list = ArrayList<BrainContact>()
        try {
            val arr = JSONArray(prefs(ctx).getString("brain_contacts", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val rel = if (o.has("relation") && !o.isNull("relation")) o.optString("relation") else null
                list.add(BrainContact(o.optString("name"), o.optString("phone"), rel))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun storeContact(ctx: Context, c: BrainContact) {
        val list = loadContacts(ctx)
        val tail = c.phone.replace(Regex("[^\\d]"), "").takeLast(8)
        list.removeAll { it.phone.replace(Regex("[^\\d]"), "").takeLast(8) == tail }
        list.add(c)
        val arr = JSONArray()
        for (x in list) {
            val o = JSONObject()
            o.put("name", x.name); o.put("phone", x.phone)
            if (x.relation != null) o.put("relation", x.relation) else o.put("relation", JSONObject.NULL)
            arr.put(o)
        }
        prefs(ctx).edit().putString("brain_contacts", arr.toString()).apply()
    }

    fun vaultSave(ctx: Context, label: String, value: String) {
        try {
            val o = JSONObject(prefs(ctx).getString("brain_vault", "{}"))
            o.put(label.trim().lowercase(), value)
            prefs(ctx).edit().putString("brain_vault", o.toString()).apply()
        } catch (_: Exception) {}
    }

    fun vaultGet(ctx: Context, label: String): String? {
        return try {
            val o = JSONObject(prefs(ctx).getString("brain_vault", "{}"))
            val key = label.trim().lowercase()
            if (o.has(key)) o.getString(key)
            else {
                for (k in o.keys()) if (k.contains(key) || key.contains(k)) return o.getString(k)
                null
            }
        } catch (_: Exception) { null }
    }

    fun vaultAll(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val o = JSONObject(prefs(ctx).getString("brain_vault", "{}"))
            for (k in o.keys()) out.add(k)
        } catch (_: Exception) {}
        return out
    }

    // ---------- parsing ----------
    fun extractPhone(text: String): String? {
        val m = Regex("(\\+?\\d[\\d\\s\\-]{7,14}\\d)").find(text) ?: return null
        val clean = m.groupValues[1].replace(Regex("[\\s\\-]"), "")
        return if (clean.replace("+", "").length >= 10) clean else null
    }

    private fun findRel(words: List<String>): String? = words.firstOrNull { REL_WORDS.contains(it) }

    private fun extractName(words: List<String>, relWord: String?): String {
        val junk = HashSet(STOP_WORDS)
        val rels = HashSet(REL_WORDS.toList())
        val out = ArrayList<String>()
        for (w in words) {
            if (w.length < 2) continue
            if (w.any { it.isDigit() || it == '+' }) continue
            if (w in junk) continue
            if (rels.contains(w)) continue
            out.add(w)
        }
        return out.take(2).joinToString(" ")
    }

    private fun callTarget(low: String): String? {
        val m1 = Regex("^\\s*(?:call|dial|phone)\\s+(.+)$").find(low)?.groupValues?.get(1)
        if (m1 != null) {
            var t = m1.trim()
            // trailing verbs hatao
            t = Regex("\\s+(?:ko|karo|kro|krdo|kardo|kariye|please|abhi|now|jaldi)+\\s*$").replace(t, "")
            t = Regex("\\s+(?:ko|karo|kro|krdo|kardo|kariye|please|abhi|now|jaldi)$").replace(t, "")
            if (t.isNotBlank()) return t.trim()
        }
        val m2 = Regex("^(.+?)\\s+(?:ko|ka)\\s+(?:call|phone|dial)").find(low)?.groupValues?.get(1)
        if (m2 != null) return m2.trim()
        return null
    }

    fun resolve(contacts: List<BrainContact>, target: String): List<BrainContact> {
        val base = target.split(Regex("[\\s]+")).filter { it.length > 1 && it !in STOP_WORDS && it !in listOf("mera", "meri", "my", "ko", "karo") }
        if (base.isEmpty()) return emptyList()
        val relWords = base.filter { REL_WORDS.contains(it) }
        val nameWords = base.filter { it !in relWords }
        val nameMatch = { c: BrainContact -> nameWords.any { c.name.lowercase().contains(it) } }
        val relMatch = { c: BrainContact -> c.relation != null && relWords.any { c.relation!!.lowercase().contains(it) || it.contains(c.relation!!.lowercase()) } }
        val both = contacts.filter { nameMatch(it) && relMatch(it) }
        if (both.isNotEmpty()) return both
        val nm = contacts.filter { nameMatch(it) }
        if (nm.isNotEmpty() && nameWords.isNotEmpty()) return nm
        val rm = contacts.filter { relMatch(it) }
        if (rm.isNotEmpty()) return rm
        return emptyList()
    }

    /**
     * Main parser. null return = brain ne handle nahi kiya → AI/normal chat flow chalega.
     */
    fun parse(ctx: Context, low: String, msg: String): BrainReply? {
        val words = low.split(Regex("[\\s]+")).filter { it.isNotBlank() }
        val r = BrainReply()

        // ---------- 1) UNLOCK phone: honest limitation ----------
        val unlockIntent = low.contains("unlock") || ((low.contains("khol") || low.contains("open")) && low.contains("lock") && (low.contains("phone") || low.contains("mobile") || low.contains("screen")) && !low.contains("app"))
        if (unlockIntent) {
            r.text = "🔒 Unlock Android security ki wajah se koi bhi app khud nahi kar sakta — PIN/password/fingerprint aap khud daloge. Main phone LOCK kar sakta hoon, unlock aap khud karoge.\n\nApp-lock app kholni ho to: \"app lock kholo\" bolo (saved password bhi bata dunga)."
            return r
        }

        // ---------- 2) LOCK phone ----------
        if (low.contains("lock") && (low.contains("phone") || low.contains("mobile") || low.contains("screen") || low == "lock") && !low.contains("app") && !low.contains("unlock") && !low.contains("khol") && !low.contains("open")) {
            r.text = "🔒 Phone lock kar raha hoon."
            r.actions.add(BrainAction("lock"))
            return r
        }

        // ---------- 3) END call ----------
        val hasCallWord = low.contains("call") || low.contains("phone") || low.contains("calling")
        val endHit = END_CALL_WORDS.any { low.contains(it) }
        val bareEnd = low in setOf("drop", "drop do", "drop karo", "kat", "kat do", "kato", "kaat do", "cut", "cut do", "call kato", "phone kat do")
        if (endHit && (hasCallWord || bareEnd)) {
            r.text = "🔴 Call khatam kar raha hoon..."
            r.actions.add(BrainAction("endcall"))
            r.buttons.add(BrainButton("🔴 End Call", "endcall"))
            return r
        }

        // ---------- 4) CALL by name/relation (ya seedha number) ----------
        val target = callTarget(low)
        if (target != null) {
            val phone = extractPhone(target)
            if (phone != null) {
                r.text = "📞 Number mil gaya: $phone — call laga raha hoon."
                r.actions.add(BrainAction("call", phone))
                r.buttons.add(BrainButton("🔴 End Call", "endcall"))
                return r
            }
            val contacts = loadContacts(ctx)
            val hits = resolve(contacts, target)
            when {
                hits.isEmpty() -> {
                    r.text = "❓ \"$target\" saved nahi mila.\nSave karo: \"save <naam> <number> <relation>\" — e.g. \"save Hameed 03001234567 mamo\"\nSaved list: \"contacts\""
                }
                hits.size == 1 -> {
                    val c = hits[0]
                    val rel = if (c.relation != null) " (${c.relation})" else ""
                    r.text = "📞 ${c.name}$rel ko call laga raha hoon: ${c.phone}"
                    r.actions.add(BrainAction("call", c.phone))
                    r.buttons.add(BrainButton("🔴 End Call", "endcall"))
                }
                else -> {
                    val names = hits.joinToString("\n") { "• ${it.name}" + (if (it.relation != null) " (${it.relation})" else "") + " — ${it.phone}" }
                    r.text = "🤔 ${hits.size} log mile — kisko call karun? Pura naam bolo (e.g. \"${hits[0].name} ko call karo\"):\n$names"
                }
            }
            return r
        }

        // ---------- 5) SAVE contact (naam + number + relation) ----------
        val phone = extractPhone(msg)
        if (phone != null) {
            val relWord = findRel(words)
            val name = extractName(words, relWord)
            val wantsSave = SAVE_WORDS.any { low.contains(it) } || relWord != null
            if (wantsSave) {
                if (name.isBlank()) {
                    r.text = "📞 Number mila ($phone) lekin naam samajh nahi aaya.\nLikhho: \"save Ali $phone\" (relation optional: bhai/behen/mamo...)"
                    return r
                }
                val c = BrainContact(name, phone, relWord)
                storeContact(ctx, c)
                val relTxt = if (relWord != null) " | Relation: $relWord" else " | Relation: (optional — bataya nahi)"
                r.text = "✅ Contact save ho gaya!\n👤 $name — $phone$relTxt\nAb \"${if (relWord != null) relWord + " " else ""}$name ko call karo\" bol kar call kar sakta ho.\n(Number phone ki contact book mein bhi save ho raha hai.)"
                r.actions.add(BrainAction("phonebook", name, phone))
                return r
            }
        }

        // ---------- 6) TELL number ----------
        if ((low.contains("number") || low.contains("nomber")) && TELL_WORDS.any { low.contains(it) }) {
            val m1 = Regex("^(.+?)\\s+(?:ka|ki)\\s+(?:number|nomber)").find(low)?.groupValues?.get(1)
            val m2 = Regex("(?:tell|show|batao|bata|dikha|dikhaao)\\s+(?:mere|meri|my)?\\s*(.+?)\\s+(?:ka|ki)?\\s*(?:number|nomber)").find(low)?.groupValues?.get(1)
            val m3 = Regex("(.+?)\\s+(?:number|nomber)").find(low)?.groupValues?.get(1)
            val t0 = m1 ?: m2 ?: m3
            val contacts = loadContacts(ctx)
            val t = (t0 ?: "").trim()
            if (t.isBlank()) {
                r.text = if (contacts.isEmpty()) "📭 Abhi koi contact save nahi. Bolo: \"save Ali 03001234567 bhai\""
                else "📇 Saved contacts:\n" + contacts.joinToString("\n") { "• ${it.name}" + (if (it.relation != null) " (${it.relation})" else "") + " — ${it.phone}" }
                return r
            }
            val hits = resolve(contacts, t)
            when {
                hits.isEmpty() -> r.text = "❓ \"$t\" ka number saved nahi hai. Save karo: \"save $t <number> <relation>\""
                hits.size == 1 -> {
                    val c = hits[0]
                    val rel = if (c.relation != null) " (${c.relation})" else ""
                    r.text = "📱 ${c.name}$rel ka number: ${c.phone}"
                    r.buttons.add(BrainButton("📞 Call ${c.name}", "call", c.phone))
                    r.buttons.add(BrainButton("💬 WhatsApp", "wa", c.phone))
                }
                else -> {
                    val names = hits.joinToString("\n") { "• ${it.name}" + (if (it.relation != null) " (${it.relation})" else "") + " — ${it.phone}" }
                    r.text = "🤔 ${hits.size} log mile — kis ka number chahiye?\n$names"
                }
            }
            return r
        }

        // ---------- 7) APP LOCK open ----------
        if (low.contains("app") && low.contains("lock") && OPEN_WORDS.any { low.contains(it) } && !low.contains("phone") && !low.contains("screen")) {
            val pw = vaultGet(ctx, "app lock")
            r.actions.add(BrainAction("openapp", "app lock"))
            r.text = if (pw != null) "🔓 App Lock app khol raha hoon.\n🔑 Password (aapne save karwaya tha): $pw\n⚠️ Note: dusri app ke password box mein main khud type nahi kar sakta (Android security) — password yahan se copy kar lo."
            else "🔓 App Lock app khol raha hoon.\n⚠️ App lock ka password abhi save nahi. Bolo: \"app lock ka password <password>\" — private save ho jayega (sirf isi phone pe)."
            return r
        }

        // ---------- 8) PASSWORD save ("<label> ka password <value>") ----------
        val savePw = Regex("^\\s*(.{2,30}?)\\s+(?:ka|ki)\\s+(?:password|pass|pin|passcode)[\\s:]+(\\S.{0,49}?)(?:\\s+(?:hai|ha|hain|he))*\\s*$").find(low)
        if (savePw != null) {
            val label = savePw.groupValues[1].trim()
            val value = savePw.groupValues[2].trim()
            val isAsk = TELL_WORDS.any { low.contains(it) } || low.contains("kya hai")
            if (!isAsk && label.isNotBlank() && value.length >= 3) {
                vaultSave(ctx, label, value)
                r.text = "🔒 Password save ho gaya: \"$label\" — private storage mein (sirf isi phone, kisi ko nahi diya jayega).\nBatana ho to bolo: \"$label ka password batao\""
                return r
            }
        }

        // ---------- 9) PASSWORD tell ----------
        if ((low.contains("password") || low.contains("passcode") || low.contains(" pin")) && (TELL_WORDS.any { low.contains(it) } || low.contains("kya hai") || low.contains("what"))) {
            val lm = Regex("(.{2,30}?)\\s+(?:ka|ki)\\s+(?:password|pass|pin|passcode)").find(low)?.groupValues?.get(1)
                ?: (if (low.contains("app lock")) "app lock" else null)
            val label = lm?.trim()
            if (!label.isNullOrBlank()) {
                val pw = vaultGet(ctx, label)
                r.text = if (pw != null) "🔑 $label ka password: $pw\n(Private — sirf aapke phone mein.)"
                else "❓ $label ka password saved nahi hai. Pehle bolo: \"$label ka password <password>\""
            } else {
                val all = vaultAll(ctx)
                r.text = if (all.isEmpty()) "❓ Koi password save nahi hua. Bolo: \"<kis cheez> ka password <password>\" — private save ho jayega."
                else "🔒 Saved passwords (naam): ${all.joinToString(", ")}\nKis ka chahiye? Naam bolo."
            }
            return r
        }

        // ---------- 10) CONTACTS list ----------
        if (low == "contacts" || low == "contact list" || low == "my contacts" || low == "meray contacts" || low == "mere contacts" || (low == "saved contacts")) {
            val contacts = loadContacts(ctx)
            r.text = if (contacts.isEmpty()) "📭 Abhi koi contact save nahi. Bolo: \"save Ali 03001234567 bhai\""
            else "📇 Saved contacts (${contacts.size}):\n" + contacts.joinToString("\n") { "• ${it.name}" + (if (it.relation != null) " (${it.relation})" else "") + " — ${it.phone}" }
            return r
        }

        return null // handle nahi hua → AI/normal flow
    }
}
