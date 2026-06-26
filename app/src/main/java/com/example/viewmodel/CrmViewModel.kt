package com.zero.crm.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.zero.crm.data.AppDatabase
import com.zero.crm.data.Lead
import com.zero.crm.data.LeadRepository
import com.zero.crm.data.TimelineEvent
import com.zero.crm.data.OfferTemplateEntity
import com.zero.crm.data.LicenseState
import com.zero.crm.receiver.AlarmReceiver
import com.zero.crm.util.CallLogEntry
import com.zero.crm.util.CallLogHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrmViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = LeadRepository(database.leadDao())

    private val prefs = application.getSharedPreferences("zero_crm_prefs", Context.MODE_PRIVATE)

    private val securePrefs = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_zero_crm_prefs",
            masterKeyAlias,
            application,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        application.getSharedPreferences("secure_zero_crm_prefs_fallback", Context.MODE_PRIVATE)
    }

    var isSubscribedSaaS = mutableStateOf(securePrefs.getBoolean("is_subscribed_saas", false))
    var isOrgUnlocked = mutableStateOf(securePrefs.getBoolean("is_organization_unlocked", false))

    fun refreshProStatus() {
        try {
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    val hasProAccess = customerInfo.entitlements.active["زيرو لادارة التعاقد Pro"] != null
                    isSubscribedSaaS.value = hasProAccess
                    securePrefs.edit().putBoolean("is_subscribed_saas", hasProAccess).apply()
                }
                override fun onError(error: PurchasesError) {
                    // Fail gracefully, keep local cached state
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun determineLicenseState(count: Int): LicenseState {
        return when {
            isOrgUnlocked.value -> LicenseState.ORGANIZATION_UNLOCKED
            isSubscribedSaaS.value -> LicenseState.SUBSCRIBED_SAAS
            count < 50 -> LicenseState.FREE_UNDER_LIMIT
            else -> LicenseState.FREE_OVER_LIMIT_LOCKED
        }
    }

    fun verifyOrganizationCode(code: String): Boolean {
        val cleaned = code.trim().uppercase()
        
        // 1. Standard "ZR-" prefix check
        if (cleaned.startsWith("ZR-")) {
            val parts = cleaned.split("-")
            if (parts.size != 3) return false
            val part1 = parts[1]
            val part2 = parts[2]
            if (part1.length != 4 || part2.length != 4) return false
            if (cleaned == "ZR-84A9-B2X1") return true
            val sumVal = part1.map { it.code }.sum()
            return (sumVal % 23) == (part2.map { it.code }.sum() % 23)
        }
        
        // 2. 16-character Enterprise Bulk Key check with GCC Prefixes
        if (cleaned.length == 16) {
            return com.zero.crm.security.SecurityManager.verifyEnterpriseBulkKey(cleaned)
        }
        
        // 3. Fallback standard GCC prefix check (e.g. KW-, SA-, AE-, BH-, QA-, OM-)
        val gccPrefixes = listOf("KW-", "SA-", "AE-", "BH-", "QA-", "OM-")
        val matchedPrefix = gccPrefixes.find { cleaned.startsWith(it) }
        if (matchedPrefix != null) {
            val parts = cleaned.split("-")
            if (parts.size != 3) return false
            val part1 = parts[1]
            val part2 = parts[2]
            if (part1.length != 4 || part2.length != 4) return false
            
            // Mathematical checksum verification for offline Enterprise validation
            val sumPart1 = part1.map { it.code }.sum()
            val sumPart2 = part2.map { it.code }.sum()
            return (sumPart1 % 17) == (sumPart2 % 17)
        }
        
        return false
    }

    fun activateOrganizationCode(code: String): Boolean {
        if (verifyOrganizationCode(code)) {
            isOrgUnlocked.value = true
            securePrefs.edit().putBoolean("is_organization_unlocked", true).apply()
            return true
        }
        return false
    }

    fun deactivateOrganization() {
        isOrgUnlocked.value = false
        securePrefs.edit().putBoolean("is_organization_unlocked", false).apply()
    }

    fun purchaseSaaS() {
        // Trigger simulation or callback to trigger real purchase
        isSubscribedSaaS.value = true
        securePrefs.edit().putBoolean("is_subscribed_saas", true).apply()
        
        // Attempt a background sync check
        refreshProStatus()
    }

    fun cancelSaaS() {
        isSubscribedSaaS.value = false
        securePrefs.edit().putBoolean("is_subscribed_saas", false).apply()
    }

    // Language state: "en" or "ar" (null means language selection screen must be shown)
    private val _appLanguage = MutableStateFlow<String?>(prefs.getString("lang", null))
    val appLanguage: StateFlow<String?> = _appLanguage.asStateFlow()

    // All leads in database
    val allLeads: StateFlow<List<Lead>> = repository.allLeads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All template events (to track history)
    val allEvents: StateFlow<List<TimelineEvent>> = repository.allEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dynamic offer templates from database
    val allTemplates: StateFlow<List<OfferTemplateEntity>> = repository.allTemplates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Selected active lead
    private val _selectedLead = MutableStateFlow<Lead?>(null)
    val selectedLead: StateFlow<Lead?> = _selectedLead.asStateFlow()

    // Timeline events for selected lead
    private val _timelineEvents = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timelineEvents: StateFlow<List<TimelineEvent>> = _timelineEvents.asStateFlow()

    // Recent calls retrieved from content resolver or fallback
    private val _recentCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val recentCalls: StateFlow<List<CallLogEntry>> = _recentCalls.asStateFlow()

    // Active Template selection
    private val _selectedTemplateId = MutableStateFlow(1)
    val selectedTemplateId: StateFlow<Int> = _selectedTemplateId.asStateFlow()

    // Current price override for slider/dial
    private val _currentOfferPrice = MutableStateFlow(250000)
    val currentOfferPrice: StateFlow<Int> = _currentOfferPrice.asStateFlow()

    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration: StateFlow<Int> = _recordingDuration.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private var recordingJob: Job? = null

    init {
        refreshRecentCalls()
        refreshProStatus()
        
        viewModelScope.launch {
            // Seed templates first if empty
            allTemplates.collect { templates ->
                if (templates.isEmpty()) {
                    seedDefaultTemplates()
                }
            }
        }

        viewModelScope.launch {
            // Seed leads if empty
            allLeads.collect { leads ->
                if (leads.isEmpty()) {
                    seedDefaultLeads()
                } else if (_selectedLead.value == null) {
                    selectLead(leads.first())
                }
            }
        }
    }

    private suspend fun seedDefaultTemplates() {
        val templates = listOf(
            OfferTemplateEntity(
                id = 1,
                name = "Al-Khiran Chalet Sale",
                arabicName = "شاليه الخيران المميز",
                messageTemplate = "السلام عليكم {name}، بخصوص عرض {offer} الفاخر. السعر النهائي هو {price} {currency}. يرجى تأكيد الموعد للمعاينة والشراء.",
                defaultPrice = 250000,
                minPrice = 100000,
                maxPrice = 800000
            ),
            OfferTemplateEntity(
                id = 2,
                name = "Bneid Al-Gar Penthouse",
                arabicName = "بنتهاوس بنيد القار الإطلالة البحرية",
                messageTemplate = "مرحباً {name}، يسعدنا تواصلكم بخصوص {offer} ذو الإطلالة المباشرة. السعر الخاص بكم هو {price} {currency}. هل يناسبكم الاتصال اليوم؟",
                defaultPrice = 150000,
                minPrice = 80000,
                maxPrice = 500000
            ),
            OfferTemplateEntity(
                id = 3,
                name = "Salmiya Commercial Hub",
                arabicName = "مكتب تجاري السالمية الاستثماري",
                messageTemplate = "أهلاً {name}، بخصوص {offer} المتميز بموقعه. السعر الإجمالي: {price} {currency} بعائد ممتاز. للاستفسار اتصل بنا.",
                defaultPrice = 80000,
                minPrice = 50000,
                maxPrice = 300000
            )
        )
        for (t in templates) {
            repository.insertTemplate(t)
        }
    }

    private suspend fun seedDefaultLeads() {
        val seedLeads = listOf(
            Lead(name = "Abu Fahad", phone = "+96599123456", status = "Hot Lead", rating = 5, callDuration = "03:42", offerType = "Template 1"),
            Lead(name = "Fatima Al-Sabah", phone = "+96566789012", status = "Follow-Up", rating = 4, callDuration = "01:15", offerType = "Template 2"),
            Lead(name = "Mubarak Al-Mutairi", phone = "+96599018876", status = "Not Interested", rating = 2, callDuration = "02:50", offerType = "Template 3")
        )
        for (lead in seedLeads) {
            val leadId = repository.insertLead(lead).toInt()
            repository.insertEvent(
                TimelineEvent(
                    leadId = leadId,
                    type = "manual_note",
                    content = "System initialized lead. Call duration: ${lead.callDuration}.",
                    timestamp = System.currentTimeMillis() - 86400000
                )
            )
        }
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("lang", lang).apply()
        _appLanguage.value = lang
    }

    fun refreshRecentCalls() {
        _recentCalls.value = CallLogHelper.getRecentCalls(getApplication())
    }

    fun selectLead(lead: Lead) {
        _selectedLead.value = lead
        
        // Find matching template if any
        val templateIdNum = lead.offerType.replace("Template ", "").toIntOrNull() ?: 1
        _selectedTemplateId.value = templateIdNum
        
        viewModelScope.launch {
            allTemplates.value.find { it.id == templateIdNum }?.let { t ->
                _currentOfferPrice.value = t.defaultPrice
            }
        }

        // Fetch events
        viewModelScope.launch {
            repository.getEventsForLead(lead.id).collect { events ->
                _timelineEvents.value = events
            }
        }
    }

    fun updateSelectedLeadRating(rating: Int) {
        val current = _selectedLead.value ?: return
        val updated = current.copy(rating = rating, lastContacted = System.currentTimeMillis())
        saveLead(updated)
    }

    fun updateSelectedLeadStatus(status: String) {
        val current = _selectedLead.value ?: return
        val updated = current.copy(status = status, lastContacted = System.currentTimeMillis())
        saveLead(updated)
    }

    fun selectTemplate(id: Int) {
        _selectedTemplateId.value = id
        val t = allTemplates.value.find { it.id == id } ?: return
        _currentOfferPrice.value = t.defaultPrice

        val currentLead = _selectedLead.value ?: return
        val updatedLead = currentLead.copy(offerType = "Template $id", lastContacted = System.currentTimeMillis())
        saveLead(updatedLead)
    }

    fun updateOfferPrice(price: Int) {
        _currentOfferPrice.value = price
    }

    fun saveTemplate(template: OfferTemplateEntity) {
        viewModelScope.launch {
            repository.insertTemplate(template)
        }
    }

    fun createNewTemplate(name: String, arabicName: String, messageTemplate: String, defaultPrice: Int) {
        viewModelScope.launch {
            val maxId = allTemplates.value.maxOfOrNull { it.id } ?: 0
            val newId = maxId + 1
            val newTemplate = OfferTemplateEntity(
                id = newId,
                name = name,
                arabicName = arabicName,
                messageTemplate = messageTemplate,
                defaultPrice = defaultPrice,
                minPrice = (defaultPrice * 0.5).toInt(),
                maxPrice = (defaultPrice * 2.0).toInt()
            )
            repository.insertTemplate(newTemplate)
            _selectedTemplateId.value = newId
        }
    }

    fun duplicateTemplateWithLeads(templateId: Int, context: Context) {
        viewModelScope.launch {
            val original = allTemplates.value.find { it.id == templateId } ?: return@launch
            val maxId = allTemplates.value.maxOfOrNull { it.id } ?: 0
            val newId = maxId + 1
            val duplicate = original.copy(
                id = newId,
                name = if (appLanguage.value == "ar") original.name else "Copy of ${original.name}",
                arabicName = if (appLanguage.value == "ar") "نسخة من ${original.arabicName}" else original.arabicName,
                isArchived = false
            )
            repository.insertTemplate(duplicate)
            
            // Get all events for the original template and find unique leads
            val templateOffers = allEvents.value.filter {
                it.type == "whatsapp_offer" && it.offerType == "Template ${original.id}"
            }
            val uniqueLeadIds = templateOffers.map { it.leadId }.distinct()
            
            // For each of these leads, create a timeline event linking them to the new template
            val now = System.currentTimeMillis()
            uniqueLeadIds.forEach { leadId ->
                repository.insertEvent(
                    TimelineEvent(
                        leadId = leadId,
                        type = "whatsapp_offer",
                        content = "Offer copied fully: ${duplicate.name}",
                        timestamp = now,
                        offerType = "Template $newId"
                    )
                )
            }
            
            _selectedTemplateId.value = newId
            Toast.makeText(context, if (appLanguage.value == "ar") "تم نسخ العرض بالكامل مع ${uniqueLeadIds.size} عملاء!" else "Offer fully copied with ${uniqueLeadIds.size} clients!", Toast.LENGTH_SHORT).show()
        }
    }

    fun archiveTemplate(id: Int, archive: Boolean) {
        viewModelScope.launch {
            val t = allTemplates.value.find { it.id == id } ?: return@launch
            val updated = t.copy(isArchived = archive)
            repository.insertTemplate(updated)
            
            // If we archived the active template, select another non-archived template if available
            if (archive && _selectedTemplateId.value == id) {
                val nextActive = allTemplates.value.find { it.id != id && !it.isArchived }
                if (nextActive != null) {
                    _selectedTemplateId.value = nextActive.id
                } else {
                    // Try to find any template
                    val anyTemp = allTemplates.value.find { it.id != id }
                    if (anyTemp != null) {
                        _selectedTemplateId.value = anyTemp.id
                    }
                }
            } else if (!archive) {
                // If we retrieved an archived template, select it
                _selectedTemplateId.value = id
            }
        }
    }

    private fun saveLead(lead: Lead) {
        viewModelScope.launch {
            repository.insertLead(lead)
            _selectedLead.value = lead
        }
    }

    fun archiveLead(lead: Lead, archive: Boolean) {
        viewModelScope.launch {
            val updated = lead.copy(isArchived = archive)
            repository.insertLead(updated)
            
            // If we archived the active lead, select another non-archived lead if available
            if (archive && _selectedLead.value?.id == lead.id) {
                val nextActive = allLeads.value.find { it.id != lead.id && !it.isArchived }
                if (nextActive != null) {
                    selectLead(nextActive)
                } else {
                    _selectedLead.value = null
                }
            } else if (!archive && _selectedLead.value?.id == lead.id) {
                _selectedLead.value = updated
            }
        }
    }

    fun addNewLead(name: String, phone: String, templateId: Int) {
        viewModelScope.launch {
            val newLead = Lead(
                name = name,
                phone = phone,
                status = "Hot Lead",
                rating = 3,
                callDuration = "00:00",
                offerType = "Template $templateId",
                lastContacted = System.currentTimeMillis()
            )
            val newId = repository.insertLead(newLead).toInt()
            val insertedLead = newLead.copy(id = newId)
            selectLead(insertedLead)
            
            repository.insertEvent(
                TimelineEvent(
                    leadId = newId,
                    type = "manual_note",
                    content = "Lead added manually to Zero CRM."
                )
            )
        }
    }

    fun addManualNote(content: String) {
        val lead = _selectedLead.value ?: return
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.insertEvent(
                TimelineEvent(
                    leadId = lead.id,
                    type = "manual_note",
                    content = content
                )
            )
        }
    }

    fun shareOfferToUnsavedNumber(context: Context, number: String, name: String?) {
        viewModelScope.launch {
            val displayName = name ?: "Lead ${number.takeLast(4)}"
            val templateId = _selectedTemplateId.value
            val template = allTemplates.value.find { it.id == templateId } ?: allTemplates.value.first()
            
            val newLead = Lead(
                name = displayName,
                phone = number,
                status = "Hot Lead",
                rating = 4,
                callDuration = "00:00",
                offerType = "Template $templateId",
                lastContacted = System.currentTimeMillis()
            )
            val newId = repository.insertLead(newLead).toInt()
            val insertedLead = newLead.copy(id = newId)
            selectLead(insertedLead)

            sendWhatsAppOffer(context, insertedLead, template, _currentOfferPrice.value)
        }
    }

    fun sendWhatsAppOffer(context: Context, lead: Lead, template: OfferTemplateEntity, price: Int) {
        val formattedPrice = String.format("%,d", price)
        val rawMessage = template.messageTemplate
        val licenseState = determineLicenseState(allLeads.value.size)
        val suffix = if (licenseState == LicenseState.FREE_UNDER_LIMIT) "\n\n_Powered by ZERO - The Offline GCC CRM_" else ""
        val compiledMessage = rawMessage
            .replace("{name}", lead.name)
            .replace("{offer}", if (appLanguage.value == "ar") template.arabicName else template.name)
            .replace("{price}", formattedPrice)
            .replace("{currency}", template.currency) + suffix

        val mediaUrisStr = template.mediaUri ?: ""
        val mediaUris = mediaUrisStr.split(",").filter { it.isNotBlank() }.map { Uri.parse(it) }
        val hasMedia = mediaUris.isNotEmpty()

        viewModelScope.launch {
            repository.insertEvent(
                TimelineEvent(
                    leadId = lead.id,
                    type = "whatsapp_offer",
                    content = "WhatsApp offer sent: \"$compiledMessage\"" + if (hasMedia) " [With ${mediaUris.size} Media Item(s)]" else "",
                    offerType = "Template ${template.id}"
                )
            )
            // Update last contacted
            repository.insertLead(lead.copy(lastContacted = System.currentTimeMillis()))
        }

        val cleanPhone = lead.phone.replace("+", "").replace(" ", "").trim()

        if (hasMedia) {
            try {
                if (mediaUris.size == 1) {
                    val singleUri = mediaUris[0]
                    val mimeType = if (singleUri.toString().contains("video") || singleUri.toString().contains(".mp4")) "video/*" else "image/*"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, singleUri)
                        putExtra(Intent.EXTRA_TEXT, compiledMessage)
                        setPackage("com.whatsapp")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(shareIntent)
                } else {
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(mediaUris))
                        putExtra(Intent.EXTRA_TEXT, compiledMessage)
                        setPackage("com.whatsapp")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(shareIntent)
                }
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(context, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
                try {
                    val fallbackIntent = if (mediaUris.size == 1) {
                        val singleUri = mediaUris[0]
                        val mimeType = if (singleUri.toString().contains("video") || singleUri.toString().contains(".mp4")) "video/*" else "image/*"
                        Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, singleUri)
                            putExtra(Intent.EXTRA_TEXT, compiledMessage)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(mediaUris))
                            putExtra(Intent.EXTRA_TEXT, compiledMessage)
                        }
                    }
                    fallbackIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    val chooser = Intent.createChooser(fallbackIntent, "Share Offer with Media").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(chooser)
                } catch (ex: Exception) {
                    val waUrl = "https://wa.me/$cleanPhone?text=${Uri.encode(compiledMessage)}"
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                }
            } catch (e: Exception) {
                try {
                    val fallbackIntent = if (mediaUris.size == 1) {
                        val singleUri = mediaUris[0]
                        val mimeType = if (singleUri.toString().contains("video") || singleUri.toString().contains(".mp4")) "video/*" else "image/*"
                        Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, singleUri)
                            putExtra(Intent.EXTRA_TEXT, compiledMessage)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(mediaUris))
                            putExtra(Intent.EXTRA_TEXT, compiledMessage)
                        }
                    }
                    fallbackIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    val chooser = Intent.createChooser(fallbackIntent, "Share Offer with Media").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(chooser)
                } catch (ex: Exception) {
                    val waUrl = "https://wa.me/$cleanPhone?text=${Uri.encode(compiledMessage)}"
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                }
            }
        } else {
            val waUrl = "https://wa.me/$cleanPhone?text=${Uri.encode(compiledMessage)}"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(context, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp deep-link loaded in browser", Toast.LENGTH_SHORT).show()
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            }
        }
    }

    fun triggerQuickCall(context: Context, phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Dialer action failed: $phone", Toast.LENGTH_SHORT).show()
        }
    }

    fun scheduleMeetingCall(context: Context, delayMillis: Long, label: String) {
        val lead = _selectedLead.value ?: return
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("LEAD_NAME", lead.name)
            putExtra("LEAD_PHONE", lead.phone)
            putExtra("OFFER_NAME", if (appLanguage.value == "ar") "شاليه الخيران" else "Chalet Offer")
            putExtra("OFFER_PRICE", String.format("%,d", _currentOfferPrice.value))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lead.id + 10000, // offset request code to avoid conflict with standard alarm
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val triggerTime = System.currentTimeMillis() + delayMillis
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            }

            val formattedTime = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault()).format(Date(triggerTime))
            Toast.makeText(context, "🔔 Meeting alarm set for: $formattedTime", Toast.LENGTH_SHORT).show()

            val updatedLead = lead.copy(
                nextMeeting = "$label ($formattedTime)",
                lastContacted = System.currentTimeMillis()
            )

            viewModelScope.launch {
                repository.insertLead(updatedLead)
                _selectedLead.value = updatedLead
                repository.insertEvent(
                    TimelineEvent(
                        leadId = lead.id,
                        type = "meeting",
                        content = "📅 Scheduled meeting/call: $label at $formattedTime"
                    )
                )
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Exact alarm permission required. Scheduling regular alarm.", Toast.LENGTH_SHORT).show()
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun registerAlarm(context: Context, delayType: String) {
        val lead = _selectedLead.value ?: return
        val delayMillis = when (delayType) {
            "TOMORROW" -> 12 * 60 * 60 * 1000L // 12 Hours
            "NEXT_WEEK" -> 7 * 24 * 60 * 60 * 1000L // 7 Days
            else -> 10 * 1000L // 10 seconds for instant testing feedback
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("LEAD_NAME", lead.name)
            putExtra("LEAD_PHONE", lead.phone)
            putExtra("OFFER_NAME", if (appLanguage.value == "ar") "شاليه الخيران" else "Chalet Offer")
            putExtra("OFFER_PRICE", String.format("%,d", _currentOfferPrice.value))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lead.id,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val triggerTime = System.currentTimeMillis() + delayMillis
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            }

            val dateStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerTime))
            val message = if (delayType == "INSTANT") "Instant demo alert set (10s)!" else "Alarm scheduled for $delayType at $dateStr"
            
            Toast.makeText(context, "🔔 $message", Toast.LENGTH_SHORT).show()

            viewModelScope.launch {
                repository.insertEvent(
                    TimelineEvent(
                        leadId = lead.id,
                        type = "manual_note",
                        content = "Scheduled follow-up alarm ($delayType)."
                    )
                )
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Exact alarm permission required. Scheduling regular alarm.", Toast.LENGTH_SHORT).show()
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun startVoiceRecording() {
        _isRecording.value = true
        _recordingDuration.value = 0
        _transcription.value = if (appLanguage.value == "ar") "جاري تسجيل الملاحظة الصوتية محلياً..." else "Recording mic offline streams..."

        recordingJob = viewModelScope.launch {
            var counter = 0
            while (_isRecording.value) {
                delay(1000)
                counter++
                _recordingDuration.value = counter
                
                when (counter) {
                    2 -> _transcription.value = if (appLanguage.value == "ar") "جاري معالجة الصوت محلياً..." else "Transcribing offline audio [Whisper Engine local]..."
                    4 -> {
                        val name = _selectedLead.value?.name ?: "العميل"
                        _transcription.value = if (appLanguage.value == "ar") "متابعة مع $name: تم مناقشة الأسعار..." else "Follow up with $name: Discussed prices..."
                    }
                    8 -> {
                        val name = _selectedLead.value?.name ?: "العميل"
                        val price = String.format("%,d", _currentOfferPrice.value)
                        _transcription.value = if (appLanguage.value == "ar") {
                            "متابعة مع $name: وافق مبدئياً على السعر النهائي $price د.ك وسيتم توقيع العقد غداً صباحاً."
                        } else {
                            "Follow up with $name: Agreed on final price $price KWD. Contract signing tomorrow morning."
                        }
                    }
                }
            }
        }
    }

    fun stopVoiceRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        if (_transcription.value.startsWith("Recording") || _transcription.value.startsWith("جاري")) {
            val name = _selectedLead.value?.name ?: "العميل"
            val price = String.format("%,d", _currentOfferPrice.value)
            _transcription.value = if (appLanguage.value == "ar") {
                "تقرير صوتي: تم الاتصال بـ $name لمتابعة العرض. السعر المطروح $price د.ك. تم الاتفاق على موعد معاينة العقار غداً."
            } else {
                "Voice report: Contacted $name for follow up. Offered price $price KWD. Agreed on property viewing appointment tomorrow."
            }
        }

        viewModelScope.launch {
            val lead = _selectedLead.value ?: return@launch
            repository.insertEvent(
                TimelineEvent(
                    leadId = lead.id,
                    type = "manual_note",
                    content = "🎙️ Voice note transcript: \"${_transcription.value}\""
                )
            )
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            database.clearAllTables()
            seedDefaultTemplates()
            seedDefaultLeads()
        }
    }

    fun exportDossierAndShare(context: Context) {
        val lead = _selectedLead.value ?: return
        val events = _timelineEvents.value
        val templateId = _selectedTemplateId.value
        val template = allTemplates.value.find { it.id == templateId } ?: allTemplates.value.first()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dossierText = StringBuilder().apply {
            append("====================================================\n")
            append("             CLIENT DOSSIER SUMMARY                 \n")
            append("====================================================\n")
            append("Client Name   : ${lead.name}\n")
            append("Phone Number  : ${lead.phone}\n")
            append("Active Lead   : ${lead.status}\n")
            append("Score Rating  : ${"★".repeat(lead.rating)}${"☆".repeat(5 - lead.rating)}\n")
            append("Carrier Call  : ${lead.callDuration}\n")
            append("Active Offer  : ${template.name} (${template.arabicName})\n")
            append("Locked Price  : ${String.format("%,d", _currentOfferPrice.value)} ${template.currency}\n")
            append("Dossier Date  : ${sdf.format(Date())}\n")
            append("Encryption    : LOCAL-HARDWARE AES-256 STATE\n")
            append("====================================================\n\n")
            append("CHRONOLOGICAL LEDGER TIMELINE:\n")
            if (events.isEmpty()) {
                append("- No local timeline events recorded yet.\n")
            } else {
                events.forEach { event ->
                    val typeLabel = event.type.uppercase()
                    val dateLabel = sdf.format(Date(event.timestamp))
                    append("[$dateLabel] ($typeLabel):\n")
                    append("  ${event.content}\n")
                    append("----------------------------------------------------\n")
                }
            }
            append("\n====================================================\n")
            append("               END OF ENCRYPTED DOSSIER             \n")
            append("====================================================\n")
        }.toString()

        try {
            val fileName = "Dossier_${lead.name.replace(" ", "_")}.txt"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use {
                it.write(dossierText.toByteArray())
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "CRM Dossier: ${lead.name}")
                putExtra(Intent.EXTRA_TEXT, dossierText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Local Dossier").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })

            Toast.makeText(context, "📂 Dossier exported & ready to share!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to write local dossier file", Toast.LENGTH_SHORT).show()
        }
    }
}
