package com.example.agent

import com.example.data.CommandHistoryDao
import com.example.data.CommandHistoryEntity
import com.example.data.SettingsDataStore
import com.example.data.groq.GroqRepository
import com.example.service.AssistantRuntimeState
import com.example.service.OrbPhase
import com.example.service.PhoneControlAccessibilityService
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

data class AgentExecutionResult(
    val userCommand: String,
    val replyUzbek: String,
    val actionsSummary: String,
    val modelUsed: String,
    val isSuccess: Boolean,
    val shouldCloseOverlay: Boolean = false
)

class AgentOrchestrator(
    private val groqRepository: GroqRepository,
    private val settingsDataStore: SettingsDataStore,
    private val phoneCapabilities: PhoneCapabilities,
    private val offlineEngine: OfflineCommandEngine,
    private val historyDao: CommandHistoryDao
) {

    suspend fun handleCommand(rawCommand: String): AgentExecutionResult {
        val command = rawCommand.trim()
        if (command.isBlank()) {
            return AgentExecutionResult(
                userCommand = command,
                replyUzbek = "Buyruq eshitilmadi, iltimos qaytadan ayting.",
                actionsSummary = "",
                modelUsed = "offline",
                isSuccess = false
            )
        }

        val settings = settingsDataStore.getSnapshot()
        val isOnline = offlineEngine.isNetworkAvailable()

        AssistantRuntimeState.update {
            it.copy(
                orbPhase = OrbPhase.EXECUTING,
                liveTranscript = command,
                aiResponse = "Bajarilmoqda...",
                isOnline = isOnline
            )
        }

        // 1. Try local OfflineCommandEngine first (works 100% offline AND fast online!)
        val offlineResult = offlineEngine.tryExecuteOffline(command, settings.unlockPin)
        if (offlineResult != null) {
            historyDao.insert(
                CommandHistoryEntity(
                    userCommand = command,
                    aiResponse = offlineResult.replyUzbek,
                    executedActions = offlineResult.actionTag,
                    modelUsed = "offline-engine",
                    isSuccess = true
                )
            )
            AssistantRuntimeState.update {
                it.copy(
                    aiResponse = offlineResult.replyUzbek,
                    lastActionSummary = offlineResult.actionTag
                )
            }
            return AgentExecutionResult(
                userCommand = command,
                replyUzbek = offlineResult.replyUzbek,
                actionsSummary = offlineResult.actionTag,
                modelUsed = "offline-engine",
                isSuccess = true,
                shouldCloseOverlay = offlineResult.shouldCloseOverlay
            )
        }

        // 2. If offline and command needs AI chat or web search, ask user to turn on internet
        if (!isOnline) {
            val offlineMsg = "Buning uchun internet kerak. Iltimos internetni yoqing."
            historyDao.insert(
                CommandHistoryEntity(
                    userCommand = command,
                    aiResponse = offlineMsg,
                    executedActions = "internet_required",
                    modelUsed = "offline-engine",
                    isSuccess = false
                )
            )
            AssistantRuntimeState.update {
                it.copy(
                    aiResponse = offlineMsg,
                    lastActionSummary = "Internet talab qilinadi"
                )
            }
            return AgentExecutionResult(
                userCommand = command,
                replyUzbek = offlineMsg,
                actionsSummary = "Internet talab qilinadi",
                modelUsed = "offline-engine",
                isSuccess = false
            )
        }

        if (settings.groqApiKey.isBlank()) {
            val msg = "Groq API kaliti kiritilmagan. Iltimos ilova sozlamalariga API kalitini kiriting."
            return AgentExecutionResult(
                userCommand = command,
                replyUzbek = msg,
                actionsSummary = "API kalit yo'q",
                modelUsed = "",
                isSuccess = false
            )
        }

        AssistantRuntimeState.update {
            it.copy(
                orbPhase = OrbPhase.THINKING,
                aiResponse = "O'ylayapman..."
            )
        }

        var activeModel = settings.activeModel
        var discoveredModels = settings.discoveredModels
        if (discoveredModels.isEmpty() && settings.autoFreeModel) {
            val discovery = groqRepository.fetchAvailableFreeModels(settings.groqApiKey)
            if (discovery.isSuccess) {
                activeModel = discovery.bestModel
                discoveredModels = discovery.models
                settingsDataStore.saveActiveAndDiscoveredModels(activeModel, discoveredModels)
            }
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", buildSystemPrompt(settings.wakeWord))
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", command)
            })
        }

        val tools = buildToolsSchema()
        val executedSteps = mutableListOf<String>()
        var finalModelUsed = activeModel
        var finalReply = ""
        var isSuccess = true

        for (step in 1..6) {
            val response = groqRepository.chatWithTools(
                apiKey = settings.groqApiKey,
                preferredModel = finalModelUsed,
                candidateModels = discoveredModels,
                autoFallback = settings.autoFreeModel,
                messages = messages,
                tools = tools
            )

            if (response.errorMessage != null) {
                finalReply = "Kechirasiz, hozir AI serveriga ulanib bo'lmadi. Internet aloqasini tekshiring."
                isSuccess = false
                break
            }

            finalModelUsed = response.modelUsed
            if (finalModelUsed != activeModel && settings.autoFreeModel) {
                settingsDataStore.saveActiveAndDiscoveredModels(finalModelUsed, discoveredModels)
            }

            if (response.toolCalls.isEmpty()) {
                finalReply = response.content?.trim().orEmpty()
                    .ifBlank { "Buyruq bajarildi." }
                break
            }

            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                put("content", response.content ?: JSONObject.NULL)
                put("tool_calls", JSONArray().apply {
                    response.toolCalls.forEach { tc ->
                        put(JSONObject().apply {
                            put("id", tc.id)
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", tc.name)
                                put("arguments", tc.argumentsJson)
                            })
                        })
                    }
                })
            }
            messages.put(assistantMsg)

            AssistantRuntimeState.update {
                it.copy(orbPhase = OrbPhase.EXECUTING)
            }

            for (tc in response.toolCalls) {
                val toolOutput = executeTool(tc.name, tc.argumentsJson, settings.unlockPin)
                executedSteps.add("${tc.name}: $toolOutput")
                AssistantRuntimeState.update {
                    it.copy(lastActionSummary = toolOutput)
                }

                messages.put(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", tc.id)
                    put("name", tc.name)
                    put("content", toolOutput)
                })
            }

            if (response.toolCalls.any {
                    it.name in listOf("open_app", "click_element", "type_text", "scroll_screen", "system_navigation", "unlock_phone")
                }
            ) {
                delay(750L)
            }
        }

        if (finalReply.isBlank()) {
            finalReply = if (executedSteps.isNotEmpty()) executedSteps.last() else "Buyruq bajarildi."
        }

        val actionsText = executedSteps.joinToString(" | ")
        historyDao.insert(
            CommandHistoryEntity(
                userCommand = command,
                aiResponse = finalReply,
                executedActions = actionsText,
                modelUsed = finalModelUsed,
                isSuccess = isSuccess
            )
        )

        AssistantRuntimeState.update {
            it.copy(
                aiResponse = finalReply,
                lastActionSummary = actionsText
            )
        }

        return AgentExecutionResult(
            userCommand = command,
            replyUzbek = finalReply,
            actionsSummary = actionsText,
            modelUsed = finalModelUsed,
            isSuccess = isSuccess,
            shouldCloseOverlay = false
        )
    }

    private suspend fun executeTool(name: String, argsJson: String, savedPin: String): String {
        val args = try {
            JSONObject(argsJson)
        } catch (_: Exception) {
            JSONObject()
        }

        return when (name) {
            "get_screen_content" -> {
                PhoneControlAccessibilityService.instance?.captureScreenSummary()
                    ?: "Ekran nazorati (Accessibility) yoqilmagan."
            }

            "unlock_phone" -> {
                val pinArg = args.optString("pin", "").ifBlank { savedPin }
                val acc = PhoneControlAccessibilityService.instance
                if (acc != null) {
                    AssistantRuntimeState.update { it.copy(temporarilyYieldForUnlock = true) }
                    delay(480L)
                    val res = acc.unlockDeviceWithPin(pinArg)
                    AssistantRuntimeState.update { it.copy(temporarilyYieldForUnlock = false) }
                    res
                } else {
                    "Qulfdan ochish uchun Ekran nazoratini yoqing."
                }
            }

            "open_app" -> phoneCapabilities.openApp(args.optString("app_name"))

            "list_installed_apps" -> {
                val apps = phoneCapabilities.getInstalledLaunchableApps()
                if (apps.isEmpty()) "Ilovalar topilmadi." else apps.joinToString(", ") { it.label }
            }

            "click_element" -> {
                PhoneControlAccessibilityService.instance?.clickElement(args.optString("target"))
                    ?: "Ekranni bosish uchun Ekran nazorati yoqilmagan."
            }

            "type_text" -> {
                val text = args.optString("text")
                val targetField = args.optString("target_field", "").ifBlank { null }
                PhoneControlAccessibilityService.instance?.typeText(text, targetField)
                    ?: "Matn yozish uchun Ekran nazorati yoqilmagan."
            }

            "scroll_screen" -> {
                PhoneControlAccessibilityService.instance?.scrollScreen(args.optString("direction", "down"))
                    ?: "Ekranni aylantirish uchun Ekran nazorati yoqilmagan."
            }

            "system_navigation" -> {
                PhoneControlAccessibilityService.instance?.performSystemAction(args.optString("action"))
                    ?: "Tizim tugmasi uchun Ekran nazorati yoqilmagan."
            }

            "search_contacts" -> {
                val contacts = phoneCapabilities.searchContacts(args.optString("query"))
                if (contacts.isEmpty()) "Kontakt topilmadi."
                else contacts.joinToString("; ") { "${it.name}: ${it.phoneNumber}" }
            }

            "make_call" -> phoneCapabilities.makePhoneCall(args.optString("contact_or_number"))

            "send_sms" -> phoneCapabilities.sendSms(
                args.optString("contact_or_number"),
                args.optString("message")
            )

            "control_device" -> {
                val feature = args.optString("feature")
                val value = args.optString("value")
                when (feature.lowercase()) {
                    "flashlight", "chiroq" -> phoneCapabilities.toggleFlashlight(
                        value.lowercase() in listOf("on", "yoq", "true", "1")
                    )
                    "volume", "ovoz" -> phoneCapabilities.controlVolume(value)
                    "settings", "sozlamalar" -> phoneCapabilities.openSystemSettings(value)
                    "web_search", "qidiruv" -> phoneCapabilities.webSearch(value)
                    "alarm", "budilnik" -> {
                        val parts = value.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        phoneCapabilities.setAlarm(h, m, "Telefonim AI")
                    }
                    else -> "Noma'lum qurilma funksiyasi: $feature"
                }
            }

            else -> "Noma'lum vosita: $name"
        }
    }

    private fun buildSystemPrompt(wakeWord: String): String {
        val accEnabled = PhoneControlAccessibilityService.isConnected()
        return """
Sen "$wakeWord" (Telefonim AI) nomli o'zbek tilidagi aqlli telefon yordamchisi va avtonom agentisan.
MUHIM QOIDALAR:
1. Har doim faqat chiroyli, aniq, tabiiy va qisqa O'ZBEK TILIDA (lotin alifbosida) javob ber — javobing ovoz bilan o'qib eshittiriladi (1-3 jumla).
2. Hech qachon "Xatolik" degan qo'pol so'zni ishlatma.
3. Ilova ochish, qo'ng'iroq qilish, SMS/Telegram xabar yuborish, telefonni qulfdan ochish, chiroqni yoqish, ekranni ko'rish va boshqarish uchun DARHOL tegishli vositalarni chaqir.
4. Ko'p bosqichli ilova ichidagi amallar uchun: `open_app` -> `get_screen_content` -> `click_element` / `type_text` ketma-ketligidan foydalan.
5. Ekran Nazorati holati: ${if (accEnabled) "YONIQ" else "O'CHIQ"}.
        """.trimIndent()
    }

    private fun buildToolsSchema(): JSONArray {
        fun tool(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()): JSONObject {
            return JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", name)
                    put("description", desc)
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", props)
                        if (required.isNotEmpty()) put("required", JSONArray(required))
                    })
                })
            }
        }

        fun strProp(desc: String) = JSONObject().apply {
            put("type", "string")
            put("description", desc)
        }

        return JSONArray().apply {
            put(tool("get_screen_content", "Hozirgi telefon ekranidagi barcha matnlar, tugmalar va maydonlarni o'qiydi.", JSONObject()))
            put(tool("unlock_phone", "Blok ekranini surib, saqlangan PIN kod orqali telefonni qulfdan ochadi.", JSONObject().apply {
                put("pin", strProp("Ixtiyoriy PIN kod"))
            }))
            put(tool("open_app", "Telefonda o'rnatilgan ilovani nomi bo'yicha ochadi.", JSONObject().apply {
                put("app_name", strProp("Ochiladigan ilova nomi"))
            }, listOf("app_name")))
            put(tool("list_installed_apps", "Telefonda o'rnatilgan barcha ilovalar ro'yxatini oladi.", JSONObject()))
            put(tool("click_element", "Ekrandagi tugma yoki elementni matni yoki indeksi bo'yicha bosadi.", JSONObject().apply {
                put("target", strProp("Element matni yoki indeksi"))
            }, listOf("target")))
            put(tool("type_text", "Ekrandagi matn kiritish maydoniga matn yozadi.", JSONObject().apply {
                put("text", strProp("Yoziladigan matn"))
                put("target_field", strProp("Ixtiyoriy maydon nomi yoki indeksi"))
            }, listOf("text")))
            put(tool("scroll_screen", "Ekranni up, down, left, right yo'nalishda aylantiradi.", JSONObject().apply {
                put("direction", strProp("Yo'nalish"))
            }, listOf("direction")))
            put(tool("system_navigation", "Tizim tugmalari: back, home, recents, notifications, quick_settings, screenshot.", JSONObject().apply {
                put("action", strProp("Harakat nomi"))
            }, listOf("action")))
            put(tool("search_contacts", "Telefon kontaktlaridan ism bo'yicha raqam qidiradi.", JSONObject().apply {
                put("query", strProp("Kontakt ismi"))
            }, listOf("query")))
            put(tool("make_call", "Kontakt ismi yoki raqamga qo'ng'iroq qiladi.", JSONObject().apply {
                put("contact_or_number", strProp("Kontakt ismi yoki raqam"))
            }, listOf("contact_or_number")))
            put(tool("send_sms", "Kontakt yoki raqamga SMS xabar yuboradi.", JSONObject().apply {
                put("contact_or_number", strProp("Qabul qiluvchi"))
                put("message", strProp("Xabar matni"))
            }, listOf("contact_or_number", "message")))
            put(tool("control_device", "Qurilma funksiyalari: flashlight, volume, settings, alarm, web_search.", JSONObject().apply {
                put("feature", strProp("Funksiya"))
                put("value", strProp("Qiymat"))
            }, listOf("feature", "value")))
        }
    }
}
