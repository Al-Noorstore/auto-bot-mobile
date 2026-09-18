# Auto Bot Mobile (Native Android App)

Auto Bot ka native version — phone ka REAL access:
- 💾 Contact book mein naam + number save (phone ki asli contacts mein)
- 📞 Auto call — dialer bina khole direct call (CALL_PHONE permission)
- 💬 WhatsApp chat kholna
- 🌐 Auto Bot dashboard kholna (URL app mein save hota hai)

## APK Build
GitHub khud build karta hai (Actions). Har push par:
1. Repo ke *Actions* tab kholo
2. Latest "Build APK" run kholo
3. *Artifacts* se `AutoBot-APK` download karo (zip, andar app-debug.apk)
4. Phone mein install karo ("unknown sources" allow kar ke)

## Permissions (install par)
- CALL_PHONE: direct call ke liye
- WRITE_CONTACTS / READ_CONTACTS: contacts save aur duplicate check
- INTERNET

## Note
Ye debug APK hai (apne use ke liye). Play Store ke liye release signing chahiye hogi.
