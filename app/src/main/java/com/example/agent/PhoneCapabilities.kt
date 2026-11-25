package com.example.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

class PhoneCapabilities(private val context: Context) {

    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim() ?: pkg
                InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun openApp(appQuery: String): String {
        val normalized = normalizeAppAlias(appQuery)
        val apps = getInstalledLaunchableApps()
        if (apps.isEmpty()) {
            return "O'rnatilgan ilovalar ro'yxatini olish imkoni bo'lmadi."
        }

        val exact = apps.firstOrNull {
            it.label.equals(normalized, ignoreCase = true) ||
                it.label.equals(appQuery.trim(), ignoreCase = true)
        }
        val contains = exact ?: apps.firstOrNull {
            it.label.lowercase().contains(normalized) ||
                normalized.contains(it.label.lowercase()) ||
                it.packageName.lowercase().contains(normalized.replace(" ", ""))
        }

        if (contains == null) {
            val sample = apps.take(12).joinToString(", ") { it.label }
            return "'$appQuery' ilovasi telefonda topilmadi. Mavjud ilovalar: $sample"
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(contains.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        return if (launchIntent != null) {
            context.startActivity(launchIntent)
            "${contains.label} ilovasi ochildi."
        } else {
            "${contains.label} ilovasini ishga tushirib bo'lmadi."
        }
    }

    private fun normalizeAppAlias(raw: String): String {
        val q = raw.lowercase().trim()
        return when {
            q.contains("telegram") || q.contains("tiligram") -> "telegram"
            q.contains("yutub") || q.contains("youtube") -> "youtube"
            q.contains("whatsapp") || q.contains("vatsap") || q.contains("watsap") -> "whatsapp"
            q.contains("insta") -> "instagram"
            q.contains("sozlama") || q.contains("nastroyka") -> "settings"
            q.contains("kamera") -> "camera"
            q.contains("xabar") || q.contains("sms") -> "messages"
            q.contains("kontakt") -> "contacts"
            q.contains("soat") || q.contains("budilnik") -> "clock"
            q.contains("kalkulyator") || q.contains("hisoblagich") -> "calculator"
            q.contains("brauzer") || q.contains("xrom") || q.contains("chrome") -> "chrome"
            q.contains("galereya") || q.contains("rasmlar") -> "photos"
            q.contains("xarita") || q.contains("karta") -> "maps"
            q.contains("play market") || q.contains("playmarket") -> "play store"
            else -> q
        }
    }

    fun searchContacts(query: String): List<ContactMatch> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val candidates = buildContactSearchTerms(query)
        val results = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isEmpty() || number.isEmpty()) continue

                    val lowerName = name.lowercase()
                    val matched = candidates.any { term ->
                        lowerName == term || lowerName.contains(term) || term.contains(lowerName)
                    }
                    if (matched) {
                        results.add(ContactMatch(name, number))
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return results.distinctBy { it.phoneNumber.replace(Regex("[^+0-9]"), "") }.take(10)
    }

    private fun buildContactSearchTerms(rawQuery: String): List<String> {
        val q = rawQuery.lowercase().trim()
        val stripped = stripUzbekSuffixes(q)
        val terms = mutableSetOf(q, stripped)

        // Family & common Uzbek aliases
        val root = stripped
        if (root in listOf("oyi", "oyim", "ona", "onam", "onajon", "mama")) {
            terms.addAll(listOf("oyi", "oyim", "ona", "onam", "onajon", "mama"))
        }
        if (root in listOf("dada", "dadam", "ota", "otam", "papa")) {
            terms.addAll(listOf("dada", "dadam", "ota", "otam", "papa"))
        }
        if (root in listOf("aka", "akam", "uka", "ukam", "opa", "opam", "singil", "singlim")) {
            terms.add(root)
        }
        return terms.filter { it.length >= 2 }
    }

    private fun stripUzbekSuffixes(word: String): String {
        var w = word
        val suffixes = listOf("imga", "mga", "ning", "ga", "ka", "qa", "ni", "da", "dan", "im", "m")
        for (suffix in suffixes) {
            if (w.length > suffix.length + 2 && w.endsWith(suffix)) {
                w = w.removeSuffix(suffix)
                break
            }
        }
        return w
    }

    fun extractPhoneNumberOrNull(input: String): String? {
        val converted = convertUzbekNumberWords(input)
        val digitsOnly = converted.replace(Regex("[^+0-9]"), "")
        return if (digitsOnly.count { it.isDigit() } >= 3) digitsOnly else null
    }

    private fun convertUzbekNumberWords(text: String): String {
        val tokens = text.lowercase()
            .replace("+", " + ")
            .split(Regex("\\s+"))
        val wordMap = mapOf(
            "nol" to "0", "bir" to "1", "ikki" to "2", "uch" to "3", "to'rt" to "4", "tort" to "4",
            "besh" to "5", "olti" to "6", "yetti" to "7", "sakkiz" to "8", "to'qqiz" to "9", "toqqiz" to "9",
            "o'n" to "10", "on" to "10", "yigirma" to "20", "o'ttiz" to "30", "ottiz" to "30",
            "qirq" to "40", "ellik" to "50", "oltmish" to "60", "yetmish" to "70",
            "sakson" to "80", "to'qson" to "90", "toqson" to "90", "plyus" to "+"
        )
        val sb = StringBuilder()
        var currentTens: Int? = null

        for (token in tokens) {
            val mapped = wordMap[token]
            if (mapped != null) {
                val num = mapped.toIntOrNull()
                if (num != null && num >= 10) {
                    if (currentTens != null) sb.append(currentTens)
                    currentTens = num
                } else if (num != null && currentTens != null && num in 1..9) {
                    sb.append(currentTens + num)
                    currentTens = null
                } else {
                    if (currentTens != null) {
                        sb.append(currentTens)
                        currentTens = null
                    }
                    sb.append(mapped)
                }
            } else {
                if (currentTens != null) {
                    sb.append(currentTens)
                    currentTens = null
                }
                if (token.any { it.isDigit() || it == '+' }) {
                    sb.append(token)
                }
            }
        }
        if (currentTens != null) sb.append(currentTens)
        return sb.toString()
    }

    fun makePhoneCall(target: String): String {
        val directNumber = extractPhoneNumberOrNull(target)
        var resolvedName = target
        var resolvedNumber = directNumber

        if (resolvedNumber == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return "Kontaktlardan qidirish uchun Kontaktlar ruxsati berilmagan."
            }
            val matches = searchContacts(target)
            if (matches.isEmpty()) {
                return "'$target' nomli kontakt topilmadi."
            }
            resolvedName = matches.first().name
            resolvedNumber = matches.first().phoneNumber
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:${Uri.encode(resolvedNumber)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return if (hasCallPermission) {
            "$resolvedName ($resolvedNumber) ga qo'ng'iroq qilinmoqda."
        } else {
            "$resolvedName ($resolvedNumber) raqami terish oynasiga ochildi (to'g'ridan-to'g'ri qo'ng'iroq uchun Qo'ng'iroq ruxsatini yoqing)."
        }
    }

    fun sendSms(target: String, message: String): String {
        val directNumber = extractPhoneNumberOrNull(target)
        var resolvedName = target
        var resolvedNumber = directNumber

        if (resolvedNumber == null) {
            val matches = searchContacts(target)
            if (matches.isEmpty()) {
                return "'$target' nomli kontakt topilmadi. Agar Telegram orqali yubormoqchi bo'lsangiz, ilovani ochib yuborishim mumkin."
            }
            resolvedName = matches.first().name
            resolvedNumber = matches.first().phoneNumber
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSmsPermission) {
            return try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(resolvedNumber, null, message, null, null)
                "$resolvedName ($resolvedNumber) ga SMS yuborildi: \"$message\""
            } catch (e: Exception) {
                openSmsIntentFallback(resolvedNumber, message)
                "$resolvedName ga SMS oynasi ochildi."
            }
        } else {
            openSmsIntentFallback(resolvedNumber, message)
            return "$resolvedName ($resolvedNumber) ga \"$message\" matni bilan SMS oynasi ochildi."
        }
    }

    private fun openSmsIntentFallback(number: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun toggleFlashlight(turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "Telefonda chiroq (flesh) topilmadi."
            cameraManager.setTorchMode(cameraId, turnOn)
            if (turnOn) "Telefon chirog'i yoqildi." else "Telefon chirog'i o'chirildi."
        } catch (e: Exception) {
            "Chiroqni boshqarib bo'lmadi: ${e.localizedMessage}"
        }
    }

    fun controlVolume(action: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        return when (action.lowercase().trim()) {
            "up", "ko'tar", "baland" -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Ovoz balandligi oshirildi."
            }
            "down", "pasaytir", "past" -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Ovoz balandligi pasaytirildi."
            }
            "mute", "o'chir", "jim" -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "Ovoz o'chirildi."
            }
            "max", "maksimum" -> {
                val max = audio.getStreamMaxVolume(stream)
                audio.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                "Ovoz eng baland darajaga qo'yildi."
            }
            else -> "Noma'lum ovoz buyrug'i."
        }
    }

    fun openSystemSettings(section: String): String {
        val action = when (section.lowercase().trim()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "blutuz" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "ovoz" -> Settings.ACTION_SOUND_SETTINGS
            "display", "ekran" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery", "batareya" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "accessibility", "maxsus" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "apps", "ilovalar" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Telefon sozlamalari ($section) ochildi."
    }

    fun setAlarm(hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            String.format("Soat %02d:%02d ga budilnik o'rnatilmoqda.", hour, minute)
        } catch (_: Exception) {
            "Budilnik ilovasini ochib bo'lmadi."
        }
    }

    fun webSearch(query: String): String {
        val encoded = Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "'$query' bo'yicha internetdan qidiruv ochildi."
        } catch (_: Exception) {
            "Brauzerni ochib bo'lmadi."
        }
    }
}
