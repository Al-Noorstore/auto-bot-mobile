# Auto Bot Mobile (Native Android App)

## v3.7 — GitHub + Powers port (engine ke bina)

- 🐙 **GitHub power** — token save github ghp_xxx ke baad: github push, github status, github repo banao, github apk <repo>, apk download / aab / exe / ipa banao (GitHub Actions se build), github push zip
- 📇 **Phonebook** — device contacts: phonebook, contacts <naam> (READ_CONTACTS permission)
- 👥 **Clients CRM** — client add Ali | phone +92... | email | country | niche, client list, client search Ali, message client Ali | text (WhatsApp), supplier add, duplicate par haan/nahi confirm
- 🖼️ **ImageBrain** — image generate <prompt> (Gemini/OpenAI key), image padho <path> | sawal (vision), wa image, last image
- 🐍 **dep store** — dep list, dep install <id> (PRO python wheels)
- 📁 **Project memory** — project new/open/note, remember key | value (project-scoped), project memory
- 🤝 **Smart call router** — naam se call ab Phonebook + clients se bhi resolve (sim-aware)
- ☎️ **Roz client report** — mera number +92... save karo → roz 9 baje WhatsApp pe client report

## v3.6 — Dual-SIM + WhatsApp calls + App Lock (GGUF wale features ka port, engine ke bina)

- 📱 **SIM Dialer page** (menu bar) — default call SIM choose karo: SIM 1 / SIM 2 / har baar poochho
- 🧠 **SIM ka order:** chat mein bola ("call Amir sim 2") > contact ki aadat (bot note karta hai) > default setting
- 🔊 **Speaker se poochta hai** — dual-SIM par bot bolta hai "kaun si SIM se call karni hai?" (chat buttons / mic / text: sim 1, sim 2, cancel)
- 📊 **Aadat note + default suggestion** — zyada-tar jis SIM se calls, bot offer karta hai "SIM 2 default banaun?" (user approval se)
- 💾 **Pehla save → SIM sawal** — first contact save par: call kis SIM se karni hogi?
- ✅ **Exact naam match** — "Rizwan Bai ko call" sirf Rizwan Bai ko; "amir" akele → sab Amir list (naam+number); "bai" likho → sab Bai wale
- 💬 **WhatsApp calls** — "wa call Amir" (voice) / "wa video Amir" (video) / "X ko whatsapp pr call karo" — data/Wi-Fi se
- 🔓 **App Lock auto-unlock** — "app lock ka password <pw>" save karo; Accessibility ON to bot khud type kar ke lock khol dega

**DO versions hain** (dono Actions se build hoti hain, "Build APK" ke Artifacts mein):

| | AutoBot LITE (default) | AutoBot PRO |
|---|---|---|
| Python 3.11 (py/pip) | ❌ | ✅ |
| Shell/terminal commands | ✅ | ✅ |
| Contacts save, auto call, WhatsApp | ✅ | ✅ |
| Chat commands (open/search/call/wa...) | ✅ | ✅ |
| Size | ~5MB | ~70MB |
| Low-RAM phones (Redmi 9C etc) | ✅ stable | ⚠️ OOM risk |

- **LITE** = kam RAM wale phone ke liye (purani app ki jagah yehi lagegi — same app id)
- **PRO** = "Auto Bot Pro" naam se alag app (zyada RAM wale phone ke liye, Python built-in)

## Features (dono mein)
- 💾 Contact book mein naam + number save (phone ki asli contacts mein)
- 📞 Auto call — dialer bina khole direct call (CALL_PHONE permission)
- 💬 WhatsApp chat kholna
- 🖥 Built-in terminal (real Android shell: ls, mkdir, cat, ps, df...) — 'py' sirf PRO mein
- 🌐 Auto Bot dashboard kholna (URL app mein save hota hai)
- ⚠️ Crash report: crash hone par agli baar app khulte hi log dikhta hai + server pe jata hai

## APK Build
GitHub khud build karta hai (Actions). Har push par:
1. Repo ke *Actions* tab kholo
2. Latest "Build APK" run kholo
3. *Artifacts* se download karo:
   - `AutoBot-LITE-APK` → AutoBot-Lite-v2.1.apk (pehle ye try karo)
   - `AutoBot-PRO-APK` → AutoBot-Pro-v2.1.apk (Python chahiye ho to)
4. Phone mein install karo ("unknown sources" allow kar ke)

## Permissions (install par)
- CALL_PHONE: direct call ke liye
- WRITE_CONTACTS / READ_CONTACTS: contacts save aur duplicate check
- INTERNET

## Note
Release-signed APK hai (dono same key se). Play Store upload ke liye bhi yehi key use hogi.
