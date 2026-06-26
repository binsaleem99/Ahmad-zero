package com.zero.crm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zero.crm.data.Lead
import com.zero.crm.data.TimelineEvent
import com.zero.crm.data.OfferTemplateEntity
import com.zero.crm.data.LicenseState
import com.zero.crm.ui.theme.*
import com.zero.crm.util.CallLogEntry
import com.zero.crm.util.ReportExporter
import com.zero.crm.viewmodel.CrmViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    CrmDashboardScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Multilingual translations helper
class Trans(val isAr: Boolean) {
    fun get(en: String, ar: String): String = if (isAr) ar else en
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrmDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: CrmViewModel = viewModel()
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isAr = appLanguage == "ar"
    val t = remember(isAr) { Trans(isAr) }

    val leads by viewModel.allLeads.collectAsStateWithLifecycle()
    val events by viewModel.allEvents.collectAsStateWithLifecycle()
    val templates by viewModel.allTemplates.collectAsStateWithLifecycle()
    val activeLead by viewModel.selectedLead.collectAsStateWithLifecycle()
    val recentCalls by viewModel.recentCalls.collectAsStateWithLifecycle()
    val activeTemplateId by viewModel.selectedTemplateId.collectAsStateWithLifecycle()
    val currentPrice by viewModel.currentOfferPrice.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val transcription by viewModel.transcription.collectAsStateWithLifecycle()

    var showAddLeadDialog by remember { mutableStateOf(false) }
    var showPaywallDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("TEMPLATES") } // TEMPLATES, LEADS, CALENDAR, ARCHIVE, SETTINGS

    // Permissions State
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.all { it.value }
        viewModel.refreshRecentCalls()
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            val permissions = mutableListOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECORD_AUDIO
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Language Selection First Run Screen
    if (appLanguage == null) {
        LanguageSelectionScreen(onSelect = { viewModel.setLanguage(it) })
        return
    }

    // Main App Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BauhausBlack)
    ) {
        // Aesthetic abstract gradient background glow matching designhill modern mockup
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(BauhausRed.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.width * 0.6f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(BauhausRed.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.8f),
                    radius = size.width * 0.5f
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Banner / Header (Always includes Add a Lead button)
            TopBannerSector(
                isAr = isAr,
                t = t,
                onAddLeadClick = { showAddLeadDialog = true },
                onLanguageToggle = { viewModel.setLanguage(if (isAr) "en" else "ar") },
                permissionsGranted = permissionsGranted,
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.RECORD_AUDIO)
                    )
                }
            )

            // 2. Active Screen Content Block
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (activeTab) {
                    "TEMPLATES" -> {
                        TemplatesTabScreen(
                            isAr = isAr,
                            t = t,
                            templates = templates,
                            activeId = activeTemplateId,
                            allEvents = events,
                            allLeads = leads,
                            recentCalls = recentCalls,
                            isPremium = viewModel.isSubscribedSaaS.value || viewModel.isOrgUnlocked.value,
                            onTriggerPaywall = { showPaywallDialog = true },
                            onSelect = { viewModel.selectTemplate(it) },
                            onSaveTemplate = { viewModel.saveTemplate(it) },
                            onCallFollowUp = { phone -> viewModel.triggerQuickCall(context, phone) },
                            onCreateTemplate = { name, arabicName, messageTemplate, defaultPrice ->
                                viewModel.createNewTemplate(name, arabicName, messageTemplate, defaultPrice)
                            },
                            onSendWhatsApp = { lead, template, price ->
                                viewModel.sendWhatsAppOffer(context, lead, template, price)
                            },
                            onSendToNewNumber = { phone, name ->
                                viewModel.shareOfferToUnsavedNumber(context, phone, name)
                            },
                            onArchiveTemplate = { id, archive ->
                                viewModel.archiveTemplate(id, archive)
                            },
                            onDuplicateTemplate = { id ->
                                viewModel.duplicateTemplateWithLeads(id, context)
                            }
                        )
                    }
                    "LEADS" -> {
                        LeadsTabScreen(
                            isAr = isAr,
                            t = t,
                            leads = leads,
                            activeLead = activeLead,
                            events = events,
                            recentCalls = recentCalls,
                            activeTemplateId = activeTemplateId,
                            templates = templates,
                            currentPrice = currentPrice,
                            isRecording = isRecording,
                            recordDuration = recordDuration,
                            transcription = transcription,
                            isPremium = viewModel.isSubscribedSaaS.value || viewModel.isOrgUnlocked.value,
                            onTriggerPaywall = { showPaywallDialog = true },
                            onSelectLead = { viewModel.selectLead(it) },
                            onRefreshCalls = { viewModel.refreshRecentCalls() },
                            onRatingChange = { viewModel.updateSelectedLeadRating(it) },
                            onStatusChange = { viewModel.updateSelectedLeadStatus(it) },
                            onTemplateChange = { viewModel.selectTemplate(it) },
                            onPriceChange = { viewModel.updateOfferPrice(it) },
                            onSendWhatsApp = { lead, temp, pr -> viewModel.sendWhatsAppOffer(context, lead, temp, pr) },
                            onStartRecording = { viewModel.startVoiceRecording() },
                            onStopRecording = { viewModel.stopVoiceRecording() },
                            onAddNote = { viewModel.addManualNote(it) },
                            onExportDossier = { viewModel.exportDossierAndShare(context) },
                            onAddUnsavedLead = { num, name -> viewModel.shareOfferToUnsavedNumber(context, num, name) },
                            onScheduleMeeting = { delay, label -> viewModel.scheduleMeetingCall(context, delay, label) },
                            onRequestPermissions = {
                                val permissions = mutableListOf(
                                    Manifest.permission.READ_CALL_LOG,
                                    Manifest.permission.RECORD_AUDIO
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(permissions.toTypedArray())
                            },
                            onArchiveLead = { viewModel.archiveLead(it, true) }
                        )
                    }
                    "CALENDAR" -> {
                        CalendarTabScreen(
                            isAr = isAr,
                            t = t,
                            activeLead = activeLead,
                            leads = leads.filter { !it.isArchived },
                            onScheduleAlarm = { type -> viewModel.registerAlarm(context, type) },
                            onCallLead = { phone -> viewModel.triggerQuickCall(context, phone) }
                        )
                    }
                    "ARCHIVE" -> {
                        ArchiveTabScreen(
                            isAr = isAr,
                            t = t,
                            leads = leads,
                            templates = templates,
                            onRestoreLead = { viewModel.archiveLead(it, false) },
                            onRestoreTemplate = { id, archive -> viewModel.archiveTemplate(id, archive) },
                            onCopyLeadDetails = { lead, ctx ->
                                val details = "Name: ${lead.name}\nPhone: ${lead.phone}\nStatus: ${lead.status}\nRating: ${lead.rating} Stars"
                                copyToClipboard(ctx, details, "Client Details")
                            },
                            onCopyTemplateDetails = { template, ctx ->
                                copyToClipboard(ctx, template.messageTemplate, "Offer Template Message")
                            }
                        )
                    }
                    "SETTINGS" -> {
                        SettingsTabScreen(
                            isAr = isAr,
                            t = t,
                            leadsCount = leads.size,
                            templatesCount = templates.size,
                            isSubscribedSaaS = viewModel.isSubscribedSaaS.value,
                            isOrgUnlocked = viewModel.isOrgUnlocked.value,
                            onActivateOrgCode = { viewModel.activateOrganizationCode(it) },
                            onDeactivateOrg = { viewModel.deactivateOrganization() },
                            onPurchaseSaaS = { viewModel.purchaseSaaS() },
                            onCancelSaaS = { viewModel.cancelSaaS() },
                            onLanguageChange = { viewModel.setLanguage(it) },
                            onResetDB = { viewModel.resetDatabase() }
                        )
                    }
                }
            }

            // 3. Ultra-modern Rounded Bottom Navigation Bar (Sleek minimalist tray)
            BottomNavigationTray(
                isAr = isAr,
                t = t,
                activeTab = activeTab,
                onTabSelect = { activeTab = it }
            )
        }
    }

    // Always Available Add Lead Dialog popup
    if (showAddLeadDialog) {
        AddLeadDialog(
            isAr = isAr,
            t = t,
            templates = templates,
            recentCalls = recentCalls,
            onDismiss = { showAddLeadDialog = false },
            onConfirm = { name, phone, templateId ->
                val currentCount = leads.size
                if (viewModel.determineLicenseState(currentCount) == LicenseState.FREE_OVER_LIMIT_LOCKED) {
                    showPaywallDialog = true
                } else {
                    viewModel.addNewLead(name, phone, templateId)
                    showAddLeadDialog = false
                    activeTab = "LEADS" // Switch to leads tab to see it
                }
            }
        )
    }

    if (showPaywallDialog) {
        val context = LocalContext.current
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPaywallDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BauhausDarkGray)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ZERO CRM PRO",
                            color = BauhausWhite,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showPaywallDialog = false }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Close",
                                tint = BauhausWhite
                            )
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "👑 ZERO CRM PRO",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BauhausWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = t.get("UNLIMITED RELATIONSHIPS & DEEP INSIGHTS", "علاقات غير محدودة وتقارير متكاملة"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BauhausRed,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = t.get(
                                "You have reached the local limit of 50 client contacts on the free plan.\n\nSubscribe to ZERO CRM PRO for only 5 KWD/month to unlock unlimited clients, secure offline vaults, and instant PDF/CSV reporting.",
                                "لقد وصلت للحد الأقصى (٥٠ عميل) في النسخة المجانية.\n\nاشترك في ZERO CRM برو مقابل ٥ د.ك/شهرياً لفتح كامل المميزات والعملاء بلا حدود وتصدير التقارير بصيغ PDF/CSV فوراً."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BauhausWhite,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Benefits Card list
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BauhausGlassBorder, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = t.get("✓ Unlimited client contacts & templates", "✓ إدارة غير محدودة للعملاء وقوالب العروض"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = t.get("✓ High-fidelity PDF & XLS/CSV export", "✓ تصدير التقارير والبيانات بصيغ PDF و Excel"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = t.get("✓ Enterprise Decentralized offline license validation", "✓ التحقق اللامركزي الآمن من تراخيص الشركات والمؤسسات"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = {
                                viewModel.purchaseSaaS()
                                showPaywallDialog = false
                                showAddLeadDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = t.get("SUBSCRIBE NOW — 5 KWD/Month", "اشترك الآن — ٥ د.ك/شهرياً"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(onClick = { showPaywallDialog = false }) {
                            Text(text = t.get("NOT NOW", "ليس الآن"), color = BauhausMediumGray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// FIRST LAUNCH SCREEN: GORGEOUS LANGUAGE SELECTION
@Composable
fun CrmVisualHero() {
    Box(
        modifier = Modifier
            .size(180.dp)
            .neumorphic(shapeRadius = 24.dp, elevation = 8.dp)
            .background(BauhausDarkGray, RoundedCornerShape(24.dp))
            .border(1.dp, BauhausGlassBorder, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val width = size.width
            val height = size.height

            // Background grid pattern representing system charts
            val gridColor = BauhausWhite.copy(alpha = 0.05f)
            val gridSpacing = 20.dp.toPx()
            var x = 0f
            while (x < width) {
                drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
                x += gridSpacing
            }
            var y = 0f
            while (y < height) {
                drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f)
                y += gridSpacing
            }

            // Draw connecting customer network lines
            val node1 = Offset(width * 0.18f, height * 0.28f)
            val node2 = Offset(width * 0.82f, height * 0.22f)
            val node3 = Offset(width * 0.5f, height * 0.52f) // central
            val node4 = Offset(width * 0.22f, height * 0.78f)
            val node5 = Offset(width * 0.78f, height * 0.72f)

            // Connection lines with beautiful gradient/colors
            val lineBrush = Brush.linearGradient(listOf(BauhausRed.copy(alpha = 0.6f), BauhausWhite.copy(alpha = 0.15f)))
            drawLine(brush = lineBrush, start = node1, end = node3, strokeWidth = 2.dp.toPx())
            drawLine(brush = lineBrush, start = node2, end = node3, strokeWidth = 2.dp.toPx())
            drawLine(brush = lineBrush, start = node4, end = node3, strokeWidth = 2.dp.toPx())
            drawLine(brush = lineBrush, start = node5, end = node3, strokeWidth = 2.dp.toPx())
            drawLine(brush = lineBrush, start = node1, end = node4, strokeWidth = 1.dp.toPx())
            drawLine(brush = lineBrush, start = node2, end = node5, strokeWidth = 1.dp.toPx())

            // Draw customer node dots
            drawCircle(color = BauhausWhite, radius = 5.dp.toPx(), center = node1)
            drawCircle(color = BauhausWhite, radius = 5.dp.toPx(), center = node2)
            drawCircle(color = BauhausWhite, radius = 5.dp.toPx(), center = node4)
            drawCircle(color = BauhausWhite, radius = 5.dp.toPx(), center = node5)

            // Pulse ring around central active node
            drawCircle(
                color = BauhausRed.copy(alpha = 0.35f),
                radius = 24.dp.toPx(),
                center = node3,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
        }

        // Beautiful centered active customer icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BauhausRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Active Client CRM",
                tint = BauhausWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun LanguageSelectionScreen(onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BauhausBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            // CRM Dashboard Visual Hero Image (Requirement)
            CrmVisualHero()

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "ZERO",
                style = MaterialTheme.typography.headlineLarge,
                color = BauhausWhite,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                text = "SALES AUTOMATION",
                style = MaterialTheme.typography.labelLarge,
                color = BauhausRed,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Please select your system interface language\nالرجاء اختيار لغة النظام للبدء والتشغيل",
                style = MaterialTheme.typography.bodyMedium,
                color = BauhausMediumGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // English Selection Card
            Card(
                onClick = { onSelect("en") },
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 20.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "ENGLISH MODE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BauhausWhite
                        )
                        Text(
                            text = "Minimalist slate layout & statistics",
                            style = MaterialTheme.typography.bodySmall,
                            color = BauhausMediumGray
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start English",
                        tint = BauhausRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Arabic Selection Card
            Card(
                onClick = { onSelect("ar") },
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 20.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "بدء باللغة العربية",
                        tint = BauhausRed,
                        modifier = Modifier.offset(x = (-4).dp)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "اللغة العربية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BauhausWhite
                        )
                        Text(
                            text = "التصميم الفاخر مع أتمتة الواتساب",
                            style = MaterialTheme.typography.bodySmall,
                            color = BauhausMediumGray
                        )
                    }
                }
            }
        }
    }
}

// 1. TOP BANNER SECTOR (Always visible, contains Add Lead + and Language/Sync state)
@Composable
fun TopBannerSector(
    isAr: Boolean,
    t: Trans,
    onAddLeadClick: () -> Unit,
    onLanguageToggle: () -> Unit,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BauhausBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Title & Brand
            Column {
                Text(
                    text = "ZERO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = BauhausWhite,
                    letterSpacing = 1.sp
                )
            }

            // Quick actions tray: Lang Switch + Always Visible Add Lead Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Language Switcher Toggle Card
                // Language Switcher Toggle Card
                Box(
                    modifier = Modifier
                        .neumorphic(shapeRadius = 12.dp, elevation = 4.dp)
                        .clickable { onLanguageToggle() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isAr) "EN" else "عربي",
                        color = BauhausWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // The Neumorphic Add Lead Button (Custom raised box button)
                Box(
                    modifier = Modifier
                        .neumorphic(shapeRadius = 14.dp, elevation = 4.dp, backgroundColor = BauhausRed)
                        .clickable { onAddLeadClick() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("add_lead_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Lead",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = t.get("Add Lead", "عميل جديد"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Action Warning bar if offline logs aren't synced
        if (!permissionsGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                onClick = onRequestPermissions,
                colors = CardDefaults.cardColors(containerColor = BauhausRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BauhausRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = BauhausRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = t.get("Call Sync Disconnected. Tap to Grant Permissions.", "مزامنة المكالمات متوقفة. اضغط لتفعيل الصلاحيات."),
                            color = BauhausWhite,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = t.get("[ CONNECT ]", "[ اتصال ]"),
                        color = BauhausRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// 2. BOTTOM NAVIGATION TRAY (High polished Bauhaus floating pill shape)
@Composable
fun BottomNavigationTray(
    isAr: Boolean,
    t: Trans,
    activeTab: String,
    onTabSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .neumorphic(shapeRadius = 24.dp, elevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                NavigationItem("TEMPLATES", Icons.Default.Edit, "Templates", "القوالب"),
                NavigationItem("LEADS", Icons.Default.List, "Leads", "العملاء"),
                NavigationItem("CALENDAR", Icons.Default.DateRange, "Calendar", "التقويم"),
                NavigationItem("ARCHIVE", Icons.Default.Delete, "Archive", "الأرشيف"),
                NavigationItem("SETTINGS", Icons.Default.Settings, "Settings", "الإعدادات")
            )

            // Re-order if Arabic to align nicely with RTL habits
            val orderedTabs = if (isAr) tabs.reversed() else tabs

            for (tab in orderedTabs) {
                val isSelected = activeTab == tab.id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) BauhausRed else Color.Transparent)
                        .clickable { onTabSelect(tab.id) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.titleEn,
                            tint = if (isSelected) BauhausWhite else BauhausMediumGray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isAr) tab.titleAr else tab.titleEn,
                            color = if (isSelected) BauhausWhite else BauhausMediumGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

data class NavigationItem(val id: String, val icon: ImageVector, val titleEn: String, val titleAr: String)

// ==================== TAB 1: TEMPLATES TAB SCREEN ====================
@Composable
fun TemplatesTabScreen(
    isAr: Boolean,
    t: Trans,
    templates: List<OfferTemplateEntity>,
    activeId: Int,
    allEvents: List<TimelineEvent>,
    allLeads: List<Lead>,
    recentCalls: List<CallLogEntry>,
    isPremium: Boolean,
    onTriggerPaywall: () -> Unit,
    onSelect: (Int) -> Unit,
    onSaveTemplate: (OfferTemplateEntity) -> Unit,
    onCallFollowUp: (String) -> Unit,
    onCreateTemplate: (String, String, String, Int) -> Unit,
    onSendWhatsApp: (Lead, OfferTemplateEntity, Int) -> Unit,
    onSendToNewNumber: (String, String?) -> Unit,
    onArchiveTemplate: (Int, Boolean) -> Unit,
    onDuplicateTemplate: (Int) -> Unit
) {
    val context = LocalContext.current
    
    // Segmented selection state: "active" or "archived"
    var currentFilterTab by remember { mutableStateOf("active") }
    var showBulkSendDialog by remember { mutableStateOf(false) }

    // Filter templates
    val filteredTemplates = templates.filter { 
        if (currentFilterTab == "archived") it.isArchived else !it.isArchived 
    }

    var selectedTemplate = filteredTemplates.find { it.id == activeId } ?: filteredTemplates.firstOrNull() ?: templates.find { if (currentFilterTab == "archived") it.isArchived else !it.isArchived } ?: templates.firstOrNull()

    // Real-time text edit fields
    var messageText by remember(selectedTemplate) { mutableStateOf(selectedTemplate?.messageTemplate ?: "") }
    var engNameText by remember(selectedTemplate) { mutableStateOf(selectedTemplate?.name ?: "") }
    var arbNameText by remember(selectedTemplate) { mutableStateOf(selectedTemplate?.arabicName ?: "") }
    var defPriceVal by remember(selectedTemplate) { mutableStateOf(selectedTemplate?.defaultPrice?.toString() ?: "200000") }

    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    var showSendToSavedDialog by remember { mutableStateOf(false) }
    var showSendToLogDialog by remember { mutableStateOf(false) }
    var showSendToManualDialog by remember { mutableStateOf(false) }

    // Multi-media picker launcher supporting unlimited items (images and videos)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty() && selectedTemplate != null) {
            val currentList = selectedTemplate.mediaUri?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val newList = (currentList + uris.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // ignore if not possible
                }
                uri.toString()
            }).distinct()
            val updated = selectedTemplate.copy(mediaUri = newList.joinToString(","))
            onSaveTemplate(updated)
            Toast.makeText(context, t.get("Media attached successfully!", "تم إرفاق الوسائط بنجاح!"), Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ReportDownloadBar(
                title = t.get("ALL LEADS REPORT (FROM TEMPLATES)", "تقرير العملاء الشامل (من القوالب)"),
                clients = allLeads,
                isPremium = isPremium,
                onTriggerPaywall = onTriggerPaywall
            )
        }
        // Horizontal list of the templates + Create Template trigger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t.get("SELECT & CUSTOMIZE OFFER TEMPLATES", "اختر وخصص قوالب العروض"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BauhausRed,
                    letterSpacing = 1.sp
                )
                
                // Add Template Button
                IconButton(
                    onClick = { showCreateTemplateDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(BauhausRed, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Template",
                        tint = BauhausWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Tab Selection for Active vs Archived Offers (Requirement 1, 3, 4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BauhausBlack, RoundedCornerShape(12.dp))
                    .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { currentFilterTab = "active" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentFilterTab == "active") BauhausDarkGray else Color.Transparent,
                        contentColor = if (currentFilterTab == "active") BauhausWhite else BauhausMediumGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(
                        text = t.get("Active Offers (${templates.count { !it.isArchived }})", "العروض النشطة (${templates.count { !it.isArchived }})"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                
                Button(
                    onClick = { currentFilterTab = "archived" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentFilterTab == "archived") BauhausDarkGray else Color.Transparent,
                        contentColor = if (currentFilterTab == "archived") BauhausWhite else BauhausMediumGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(
                        text = t.get("Archived Offers (${templates.count { it.isArchived }})", "العروض المؤرشفة (${templates.count { it.isArchived }})"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            if (filteredTemplates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(BauhausDarkGray, RoundedCornerShape(16.dp))
                        .border(1.dp, BauhausGlassBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentFilterTab == "active") t.get("No active templates found.", "لا توجد قوالب نشطة حالياً.") else t.get("No archived templates found.", "لا توجد قوالب مؤرشفة حالياً."),
                        color = BauhausMediumGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredTemplates) { temp ->
                        val isActive = selectedTemplate != null && temp.id == selectedTemplate.id
                        val count = allEvents.filter { it.type == "whatsapp_offer" && it.offerType == "Template ${temp.id}" }.map { it.leadId }.distinct().size
                        
                        Card(
                            onClick = { onSelect(temp.id) },
                            modifier = Modifier
                                .width(220.dp)
                                .height(140.dp)
                                .neumorphic(
                                    shapeRadius = 16.dp,
                                    elevation = if (isActive) 3.dp else 6.dp,
                                    lightShadowColor = NeuLightShadow,
                                    darkShadowColor = if (isActive) BauhausRed.copy(alpha = 0.3f) else NeuDarkShadow
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) BauhausDarkGray else BauhausBlack
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "0${temp.id}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) BauhausRed else BauhausMediumGray
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!temp.mediaUri.isNullOrBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Has Media",
                                                tint = BauhausRed,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        
                                        // Quick Archive / Restore Side Button (Requirement 3, 4)
                                        IconButton(
                                            onClick = { 
                                                onArchiveTemplate(temp.id, !temp.isArchived)
                                                val actionMsg = if (temp.isArchived) {
                                                    t.get("Offer retrieved!", "تم استرجاع العرض بنجاح!")
                                                } else {
                                                    t.get("Offer archived!", "تم نقل العرض للأرشيف!")
                                                }
                                                Toast.makeText(context, actionMsg, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (temp.isArchived) Icons.Default.Refresh else Icons.Default.Delete,
                                                contentDescription = if (temp.isArchived) "Unarchive" else "Archive",
                                                tint = if (temp.isArchived) NeuGreen else BauhausRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = if (isAr) temp.arabicName else temp.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BauhausWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${String.format("%,d", temp.defaultPrice)} ${temp.currency}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BauhausMediumGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = t.get("Sent to: $count leads", "أرسل لـ: $count عملاء"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isActive) BauhausRed else BauhausMediumGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Customization Block for Selected Template
        if (selectedTemplate != null) {
            val targetTemplateTag = "Template ${selectedTemplate.id}"
            val historyEvents = allEvents.filter { it.type == "whatsapp_offer" && it.offerType == targetTemplateTag }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(shapeRadius = 24.dp, elevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = t.get("CUSTOMIZE TEMPLATE MESSAGE", "تعديل نص الرسالة الترويجية"),
                            style = MaterialTheme.typography.titleSmall,
                            color = BauhausWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = t.get("Change offer labels and custom WhatsApp variables dynamically", "تخصيص البيانات والنص التلقائي للواتساب"),
                            style = MaterialTheme.typography.bodySmall,
                            color = BauhausMediumGray
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // English Name Field
                        Text(
                            text = t.get("Offer English Name", "اسم العرض بالإنجليزي"),
                            color = BauhausLightGray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = engNameText,
                            onValueChange = { engNameText = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Arabic Name Field
                        Text(
                            text = t.get("Offer Arabic Name", "اسم العرض بالعربي"),
                            color = BauhausLightGray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = arbNameText,
                            onValueChange = { arbNameText = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Default Price Field
                        Text(
                            text = t.get("Default Price", "السعر الافتراضي (د.ك)"),
                            color = BauhausLightGray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = defPriceVal,
                            onValueChange = { defPriceVal = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Full message text template editor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = t.get("WhatsApp Template Text", "محتوى رسالة الواتساب"),
                                color = BauhausLightGray,
                                fontSize = 11.sp
                            )
                            // Guide keywords chips
                            Row {
                                Text(
                                    text = "{name}",
                                    color = BauhausRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { messageText += " {name}" }
                                        .padding(horizontal = 4.dp)
                                )
                                Text(
                                    text = "{price}",
                                    color = BauhausRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { messageText += " {price}" }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Media section (unlimited media)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = t.get("TEMPLATE MEDIA (IMAGES & VIDEOS)", "وسائط القالب المرفقة (صور وفيديوهات)"),
                            color = BauhausLightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val mediaList = selectedTemplate.mediaUri?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BauhausBlack, RoundedCornerShape(12.dp))
                                .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (mediaList.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(mediaList) { mediaItem ->
                                        val isVideo = mediaItem.contains("video") || mediaItem.contains(".mp4")
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(BauhausDarkGray)
                                                .border(1.dp, BauhausGlassBorder, RoundedCornerShape(8.dp))
                                        ) {
                                            if (isVideo) {
                                                // Video Icon Placeholder
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Video file",
                                                        tint = BauhausRed,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            } else {
                                                // Image Async Preview
                                                AsyncImage(
                                                    model = mediaItem,
                                                    contentDescription = "Attached Image",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            // Delete overlay button (Requirement 2 & 6)
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(BauhausRed)
                                                    .clickable {
                                                        val updatedList = mediaList.toMutableList().apply { remove(mediaItem) }
                                                        val updatedStr = if (updatedList.isEmpty()) null else updatedList.joinToString(",")
                                                        onSaveTemplate(selectedTemplate.copy(mediaUri = updatedStr))
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove item",
                                                    tint = BauhausWhite,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = t.get("No Media. Add images/videos.", "لا توجد وسائط. أضف صور/فيديوهات."),
                                    color = BauhausMediumGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Add Media Button (unlimited media - Requirement 2 & 6)
                            Button(
                                onClick = { mediaPickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Media",
                                    tint = BauhausWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = t.get("Add", "إضافة"),
                                    fontSize = 11.sp,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Save, Duplicate, Archive, Bulk Send
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Archive/Restore button (Requirement 3 & 4)
                            Button(
                                onClick = {
                                    onArchiveTemplate(selectedTemplate.id, !selectedTemplate.isArchived)
                                    val actMsg = if (selectedTemplate.isArchived) {
                                        t.get("Offer retrieved!", "تم استرجاع العرض!")
                                    } else {
                                        t.get("Offer archived!", "تم نقل العرض للأرشيف!")
                                    }
                                    Toast.makeText(context, actMsg, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedTemplate.isArchived) NeuGreen else BauhausRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedTemplate.isArchived) Icons.Default.Refresh else Icons.Default.Delete,
                                    contentDescription = "Archive state",
                                    tint = BauhausWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedTemplate.isArchived) t.get("Retrieve", "استرجاع") else t.get("Archive", "أرشفة"),
                                    fontSize = 10.sp,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Duplicate button (Requirement 5)
                            Button(
                                onClick = {
                                    onDuplicateTemplate(selectedTemplate.id)
                                    Toast.makeText(context, t.get("Offer and its leads copied successfully!", "تم نسخ العرض وعملائه بنجاح!"), Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausDarkGray),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp)),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share, // Duplicate/Copy
                                    contentDescription = "Duplicate",
                                    tint = BauhausWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = t.get("Copy Offer", "نسخ العرض"),
                                    fontSize = 10.sp,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Bulk Message button (Requirement 7)
                            Button(
                                onClick = { showBulkSendDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2980B9)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Bulk send",
                                    tint = BauhausWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = t.get("Bulk Send", "إرسال جماعي"),
                                    fontSize = 10.sp,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Save modifications button
                        Button(
                            onClick = {
                                val updated = selectedTemplate.copy(
                                    name = engNameText,
                                    arabicName = arbNameText,
                                    defaultPrice = defPriceVal.toIntOrNull() ?: selectedTemplate.defaultPrice,
                                    messageTemplate = messageText,
                                    mediaUri = selectedTemplate.mediaUri
                                )
                                onSaveTemplate(updated)
                                Toast.makeText(
                                    context,
                                    t.get("Template updated successfully!", "تم تحديث القالب بنجاح!"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = t.get("Save Template Modifications", "حفظ تعديلات القالب"),
                                color = BauhausWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // --- Associated Clients List Section (Requirement 5) ---
                        Spacer(modifier = Modifier.height(16.dp))
                        val associatedLeads = allLeads.filter { lead ->
                            historyEvents.any { it.leadId == lead.id }
                        }
                        
                        Text(
                            text = t.get("SENT CLIENTS (${associatedLeads.size})", "العملاء المستلمين للعرض (${associatedLeads.size})"),
                            color = BauhausLightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (associatedLeads.isEmpty()) {
                            Text(
                                text = t.get("This offer has not been sent to any clients yet.", "لم يتم إرسال هذا العرض لأي عملاء بعد."),
                                color = BauhausMediumGray,
                                fontSize = 11.sp,
                                style = TextStyle(fontStyle = FontStyle.Italic)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BauhausBlack, RoundedCornerShape(12.dp))
                                    .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                associatedLeads.forEach { lead ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = lead.name,
                                                color = BauhausWhite,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = lead.phone,
                                                color = BauhausMediumGray,
                                                fontSize = 10.sp
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Status tag
                                            Text(
                                                text = lead.status,
                                                color = if (lead.status == "Hot Lead") Color(0xFFEF4444) else BauhausMediumGray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(
                                                        if (lead.status == "Hot Lead") Color(0x22EF4444) else BauhausDarkGray,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )

                                            // Fast resend button
                                            IconButton(
                                                onClick = {
                                                    onSendWhatsApp(lead, selectedTemplate, selectedTemplate.defaultPrice)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Send,
                                                    contentDescription = "Resend",
                                                    tint = BauhausRed,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // D. SEND THIS TEMPLATE TO CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(shapeRadius = 24.dp, elevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = t.get("SEND THIS TEMPLATE TO:", "إرسال هذا العرض إلى:"),
                            fontSize = 11.sp,
                            color = BauhausRed,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = t.get("Select target to instantly send this offer via WhatsApp", "اختر العميل المستهدف لإرسال العرض له عبر الواتساب فوراً"),
                            fontSize = 11.sp,
                            color = BauhausMediumGray
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showSendToSavedDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = "Saved Lead",
                                    tint = BauhausWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = t.get("Saved", "عميل مسجل"),
                                    fontSize = 10.sp,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { showSendToLogDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Recent Call",
                                    tint = BauhausWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = t.get("Log", "من السجل"),
                                    fontSize = 10.sp,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { showSendToManualDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Direct Number",
                                    tint = BauhausWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = t.get("Direct", "رقم يدوي"),
                                    fontSize = 10.sp,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // TEMPLATE SEND HISTORY FOR FOLLOW-UP CALLS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t.get("CLIENTS OF THIS TEMPLATE / OFFER", "العملاء الذين تلقوا هذا العرض"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BauhausRed,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    
                    val distinctLeads = historyEvents.map { it.leadId }.distinct().mapNotNull { id ->
                        allLeads.find { it.id == id }
                    }
                    val count = distinctLeads.size
                    Box(
                        modifier = Modifier
                            .background(BauhausRed, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = t.get("$count Clients", "$count عملاء"),
                            color = BauhausWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = t.get("Below are the clients who received this offer. Tap to Call or Send WhatsApp again.", "العملاء الذين تلقوا هذا العرض مسبقاً. اضغط للاتصال المباشر أو المتابعة عبر الواتساب."),
                    style = MaterialTheme.typography.bodySmall,
                    color = BauhausMediumGray
                )
            }

            val distinctLeads = historyEvents.map { it.leadId }.distinct().mapNotNull { id ->
                allLeads.find { it.id == id }
            }

            if (distinctLeads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t.get("No clients have received this offer yet.", "لم يتم إرسال هذا العرض لأي عميل بعد."),
                            style = MaterialTheme.typography.bodySmall,
                            color = BauhausMediumGray
                        )
                    }
                }
            } else {
                items(distinctLeads) { lead ->
                    val clientEvents = historyEvents.filter { it.leadId == lead.id }
                    val lastSentTime = clientEvents.maxOfOrNull { it.timestamp } ?: 0L
                    val sentCount = clientEvents.size
                    val formattedDate = if (lastSentTime > 0) {
                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(lastSentTime))
                    } else ""

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(shapeRadius = 18.dp, elevation = 5.dp),
                        colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lead.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BauhausWhite
                                )
                                Text(
                                    text = lead.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BauhausMediumGray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(BauhausRed.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = t.get("Sent $sentCount times", "تم الإرسال $sentCount مرات"),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BauhausRed
                                        )
                                    }
                                    if (formattedDate.isNotEmpty()) {
                                        Text(
                                            text = t.get("Last: $formattedDate", "آخر إرسال: $formattedDate"),
                                            fontSize = 9.sp,
                                            color = BauhausMediumGray
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Resend WhatsApp Button
                                IconButton(
                                    onClick = { onSendWhatsApp(lead, selectedTemplate, selectedTemplate.defaultPrice) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(BauhausRed, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Resend WhatsApp Offer",
                                        tint = BauhausWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // CALL Follow up Button! Call each person directly!
                                IconButton(
                                    onClick = { onCallFollowUp(lead.phone) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF2ECC71), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Follow Up Call",
                                        tint = BauhausWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- CREATE NEW TEMPLATE DIALOG ---
    if (showCreateTemplateDialog) {
        var newEngName by remember { mutableStateOf("") }
        var newArbName by remember { mutableStateOf("") }
        var newDefPrice by remember { mutableStateOf("") }
        var newMessageText by remember { mutableStateOf("السلام عليكم {name}، بخصوص عرض {offer} الفاخر. السعر النهائي هو {price} د.ك. يرجى تأكيد الموعد للمعاينة والشراء.") }

        AlertDialog(
            onDismissRequest = { showCreateTemplateDialog = false },
            modifier = Modifier.border(1.dp, BauhausRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            containerColor = BauhausDarkGray,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = t.get("CREATE NEW OFFER TEMPLATE", "إنشاء قالب عرض جديد"),
                    color = BauhausWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(text = t.get("Offer English Name", "اسم العرض بالإنجليزي"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newEngName,
                            onValueChange = { newEngName = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Text(text = t.get("Offer Arabic Name", "اسم العرض بالعربي"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newArbName,
                            onValueChange = { newArbName = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Text(text = t.get("Default Price", "السعر الافتراضي"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newDefPrice,
                            onValueChange = { newDefPrice = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Text(text = t.get("WhatsApp Template Text", "محتوى رسالة الواتساب"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newMessageText,
                            onValueChange = { newMessageText = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val priceInt = newDefPrice.toIntOrNull() ?: 100000
                        if (newEngName.isNotBlank() && newArbName.isNotBlank()) {
                            onCreateTemplate(newEngName, newArbName, newMessageText, priceInt)
                            showCreateTemplateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = t.get("CREATE", "إنشاء"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTemplateDialog = false }) {
                    Text(text = t.get("CANCEL", "إلغاء"), color = BauhausMediumGray)
                }
            }
        )
    }

    // --- SEND TO SAVED CLIENT DIALOG ---
    if (showSendToSavedDialog && selectedTemplate != null) {
        AlertDialog(
            onDismissRequest = { showSendToSavedDialog = false },
            modifier = Modifier.border(1.dp, BauhausRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            containerColor = BauhausDarkGray,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = t.get("SEND TO SAVED CLIENT", "إرسال إلى عميل مسجل"),
                    color = BauhausWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = t.get("Select a registered client from list below:", "اختر أحد العملاء المسجلين من القائمة أدناه:"),
                        color = BauhausMediumGray,
                        fontSize = 12.sp
                    )
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (allLeads.isEmpty()) {
                                item {
                                    Text(
                                        text = t.get("No clients registered yet.", "لا يوجد عملاء مسجلين حالياً."),
                                        color = BauhausMediumGray,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                items(allLeads) { lead ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSendWhatsApp(lead, selectedTemplate, selectedTemplate.defaultPrice)
                                                showSendToSavedDialog = false
                                            }
                                            .neumorphic(shapeRadius = 12.dp, elevation = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = lead.name,
                                                    color = BauhausWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = lead.phone,
                                                    color = BauhausMediumGray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "Send",
                                                tint = BauhausRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSendToSavedDialog = false }) {
                    Text(text = t.get("CLOSE", "إغلاق"), color = BauhausMediumGray)
                }
            }
        )
    }

    // --- SEND TO LOG DIALOG ---
    if (showSendToLogDialog && selectedTemplate != null) {
        AlertDialog(
            onDismissRequest = { showSendToLogDialog = false },
            modifier = Modifier.border(1.dp, BauhausRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            containerColor = BauhausDarkGray,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = t.get("SEND TO PHONE LOG NUMBER", "إرسال لجهة من سجل المكالمات"),
                    color = BauhausWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = t.get("Select a recent call log entry to register and send:", "اختر مكالمة حديثة لتسجيلها كعميل وإرسال العرض له:"),
                        color = BauhausMediumGray,
                        fontSize = 12.sp
                    )
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (recentCalls.isEmpty()) {
                                item {
                                    Text(
                                        text = t.get("No recent call logs available.", "لا يوجد مكالمات في السجل."),
                                        color = BauhausMediumGray,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                items(recentCalls) { call ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSendToNewNumber(call.number, call.name)
                                                showSendToLogDialog = false
                                            }
                                            .neumorphic(shapeRadius = 12.dp, elevation = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = call.name ?: t.get("Unsaved Call", "رقم غير مسجل"),
                                                    color = BauhausWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = call.number,
                                                    color = BauhausMediumGray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "Send",
                                                tint = BauhausRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSendToLogDialog = false }) {
                    Text(text = t.get("CLOSE", "إغلاق"), color = BauhausMediumGray)
                }
            }
        )
    }

    // --- SEND TO MANUAL CUSTOM NUMBER DIALOG ---
    if (showSendToManualDialog && selectedTemplate != null) {
        var manualPhone by remember { mutableStateOf("") }
        var manualName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSendToManualDialog = false },
            modifier = Modifier.border(1.dp, BauhausRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            containerColor = BauhausDarkGray,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = t.get("SEND TO DIRECT NUMBER", "إرسال إلى رقم مباشر"),
                    color = BauhausWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = t.get("Enter the target phone number and name (optional) to instantly send the offer:", "أدخل رقم الهاتف المستهدف والاسم (اختياري) لإرسال العرض فوراً:"),
                        color = BauhausMediumGray,
                        fontSize = 12.sp
                    )

                    Column {
                        Text(text = t.get("Phone Number", "رقم الهاتف"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualPhone,
                            onValueChange = { manualPhone = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Text(text = t.get("Name (Optional)", "الاسم (اختياري)"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualPhone.isNotBlank()) {
                            onSendToNewNumber(manualPhone, manualName.ifBlank { null })
                            showSendToManualDialog = false
                            Toast.makeText(context, t.get("Sending WhatsApp Offer...", "جاري إرسال عرض الواتساب..."), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, t.get("Please enter a phone number", "الرجاء إدخال رقم الهاتف"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = t.get("SEND", "إرسال"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendToManualDialog = false }) {
                    Text(text = t.get("CANCEL", "إلغاء"), color = BauhausMediumGray)
                }
            }
        )
    }

    // --- BULK SEND DIALOG (Requirement 7) ---
    if (showBulkSendDialog && selectedTemplate != null) {
        var selectedLeadIds by remember { mutableStateOf(allLeads.map { it.id }.toSet()) }
        var currentCampaignIndex by remember { mutableStateOf(-1) } // -1 means preparation, >= 0 means active sending step
        val chosenLeads = allLeads.filter { selectedLeadIds.contains(it.id) }

        AlertDialog(
            onDismissRequest = { 
                showBulkSendDialog = false 
                currentCampaignIndex = -1
            },
            modifier = Modifier.border(1.dp, BauhausRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            containerColor = BauhausDarkGray,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = if (currentCampaignIndex == -1) {
                        t.get("BULK SEND CAMPAIGN", "حملة الإرسال الجماعي")
                    } else {
                        t.get("SENDING CAMPAIGN STEP", "خطوة الإرسال الجارية")
                    },
                    color = BauhausWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (currentCampaignIndex == -1) {
                        Text(
                            text = t.get("Select leads to send \"${if (isAr) selectedTemplate.arabicName else selectedTemplate.name}\" with its media attachments:", "اختر العملاء لإرسال عرض \"${if (isAr) selectedTemplate.arabicName else selectedTemplate.name}\" مع مرفقات الوسائط:"),
                            color = BauhausLightGray,
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { selectedLeadIds = allLeads.map { it.id }.toSet() }) {
                                Text(text = t.get("SELECT ALL", "تحديد الكل"), color = BauhausRed, fontSize = 11.sp)
                            }
                            TextButton(onClick = { selectedLeadIds = emptySet() }) {
                                Text(text = t.get("DESELECT ALL", "إلغاء التحديد"), color = BauhausMediumGray, fontSize = 11.sp)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .background(BauhausBlack, RoundedCornerShape(12.dp))
                                .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(allLeads) { lead ->
                                val isChecked = selectedLeadIds.contains(lead.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLeadIds = if (isChecked) {
                                                selectedLeadIds - lead.id
                                            } else {
                                                selectedLeadIds + lead.id
                                            }
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = lead.name, color = BauhausWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = lead.phone, color = BauhausMediumGray, fontSize = 11.sp)
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedLeadIds = if (checked == true) {
                                                selectedLeadIds + lead.id
                                            } else {
                                                selectedLeadIds - lead.id
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = BauhausRed,
                                            uncheckedColor = BauhausMediumGray,
                                            checkmarkColor = BauhausWhite
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // Campaign sending step active
                        if (currentCampaignIndex < chosenLeads.size) {
                            val activeLead = chosenLeads[currentCampaignIndex]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BauhausBlack, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = t.get("Step ${currentCampaignIndex + 1} of ${chosenLeads.size}", "الخطوة ${currentCampaignIndex + 1} من ${chosenLeads.size}"),
                                    color = BauhausRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = activeLead.name,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = activeLead.phone,
                                    color = BauhausMediumGray,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = t.get("Click \"LAUNCH WHATSAPP\" to open chat with this lead. When you return, click \"NEXT\" to send to the next lead.", "اضغط \"فتح واتساب\" لإرسال العرض للعميل الحالي. عند عودتك، اضغط \"التالي\" للانتقال للعميل القادم."),
                                    color = BauhausLightGray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Finished
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BauhausBlack, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Success",
                                    tint = BauhausRed,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = t.get("Campaign Completed!", "اكتملت الحملة بنجاح!"),
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = t.get("Successfully initiated sharing with all selected leads.", "تمت تهيئة مشاركة الرسائل والوسائط مع جميع العملاء المحددين."),
                                    color = BauhausMediumGray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val chosenLeads = allLeads.filter { selectedLeadIds.contains(it.id) }
                if (currentCampaignIndex == -1) {
                    Button(
                        onClick = {
                            if (chosenLeads.isNotEmpty()) {
                                currentCampaignIndex = 0
                            } else {
                                Toast.makeText(context, t.get("Please select at least one lead.", "يرجى تحديد عميل واحد على الأقل."), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = t.get("START CAMPAIGN", "بدء الحملة"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                    }
                } else if (currentCampaignIndex < chosenLeads.size) {
                    val activeLead = chosenLeads[currentCampaignIndex]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onSendWhatsApp(activeLead, selectedTemplate, selectedTemplate.defaultPrice)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = t.get("LAUNCH WHATSAPP", "فتح واتساب"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                currentCampaignIndex++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausDarkGray),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                        ) {
                            Text(text = t.get("NEXT", "التالي"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            showBulkSendDialog = false
                            currentCampaignIndex = -1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = t.get("CLOSE", "إغلاق"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (currentCampaignIndex == -1) {
                    TextButton(onClick = { showBulkSendDialog = false }) {
                        Text(text = t.get("CANCEL", "إلغاء"), color = BauhausMediumGray)
                    }
                } else if (currentCampaignIndex < chosenLeads.size) {
                    TextButton(onClick = { currentCampaignIndex = -1 }) {
                        Text(text = t.get("BACK TO LIST", "الرجوع للقائمة"), color = BauhausMediumGray)
                    }
                }
            }
        )
    }
}

// ==================== TAB 2: LEADS TAB SCREEN ====================
@Composable
fun LeadsTabScreen(
    isAr: Boolean,
    t: Trans,
    leads: List<Lead>,
    activeLead: Lead?,
    events: List<TimelineEvent>,
    recentCalls: List<CallLogEntry>,
    activeTemplateId: Int,
    templates: List<OfferTemplateEntity>,
    currentPrice: Int,
    isRecording: Boolean,
    recordDuration: Int, // Note: Int used in viewmodel state representing duration in seconds
    transcription: String,
    isPremium: Boolean,
    onTriggerPaywall: () -> Unit,
    onSelectLead: (Lead) -> Unit,
    onRefreshCalls: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onStatusChange: (String) -> Unit,
    onTemplateChange: (Int) -> Unit,
    onPriceChange: (Int) -> Unit,
    onSendWhatsApp: (Lead, OfferTemplateEntity, Int) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAddNote: (String) -> Unit,
    onExportDossier: () -> Unit,
    onAddUnsavedLead: (String, String?) -> Unit,
    onScheduleMeeting: (Long, String) -> Unit,
    onRequestPermissions: () -> Unit,
    onArchiveLead: (Lead) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCallLogImportSearchDialog by remember { mutableStateOf(false) }
    
    // Filters State
    var selectedOfferFilter by remember { mutableStateOf<Int?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<String?>(null) }
    var selectedRatingFilter by remember { mutableStateOf<Int?>(null) }

    val filteredLeads = leads.filter {
        val matchesSearch = it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
        val matchesOffer = selectedOfferFilter == null || it.offerType == "Template $selectedOfferFilter"
        val matchesStatus = selectedStatusFilter == null || it.status == selectedStatusFilter
        val matchesRating = selectedRatingFilter == null || it.rating == selectedRatingFilter
        val matchesArchive = !it.isArchived
        matchesSearch && matchesOffer && matchesStatus && matchesRating && matchesArchive
    }

    // Scroll container
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. RECENT CALL LOGS (Top quick actions)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t.get("CARRIER UNREGISTERED CALLS LOG", "مكالمات الخط الهاتفي الأخيرة"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BauhausRed,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onRefreshCalls, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Logs",
                        tint = BauhausWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            val context = androidx.compose.ui.platform.LocalContext.current
            val hasLogPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALL_LOG
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasLogPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onRequestPermissions() }
                        .border(1.dp, BauhausGlassBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = BauhausDarkGray.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠️ " + t.get("Using offline simulated logs (No system permission). Click here to authorize live logs sync.", "⚠️ نستخدم سجل مكالمات تجريبي لعدم وجود صلاحيات. اضغط هنا لتفعيل المزامنة المباشرة للمكالمات."),
                            style = MaterialTheme.typography.bodySmall,
                            color = BauhausMediumGray,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (recentCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(shapeRadius = 16.dp, elevation = 4.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.get("No offline carrier call logs detected yet.", "لا توجد مكالمات هاتفية مسجلة حالياً."),
                        color = BauhausMediumGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentCalls.take(5)) { call ->
                        val leadExists = leads.any { it.phone.replace(" ", "") == call.number.replace(" ", "") }
                        Card(
                            modifier = Modifier
                                .width(220.dp)
                                .neumorphic(shapeRadius = 16.dp, elevation = 5.dp),
                            colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = call.name ?: t.get("Unsaved Call", "متصل مجهول"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BauhausWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (leadExists) Color(0xFF2ECC71).copy(alpha = 0.2f) else BauhausRed.copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (leadExists) t.get("SAVED", "مسجل") else t.get("NEW", "جديد"),
                                            color = if (leadExists) Color(0xFF2ECC71) else BauhausRed,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = call.number,
                                    fontSize = 11.sp,
                                    color = BauhausMediumGray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${call.duration} // ${call.type}",
                                    fontSize = 9.sp,
                                    color = BauhausMediumGray
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (!leadExists) {
                                    Button(
                                        onClick = { onAddUnsavedLead(call.number, call.name) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = t.get("Add & WhatsApp", "تسجيل وإرسال واتساب"),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BauhausWhite
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = t.get("Lead Account Active", "الحساب مفعل ومسجل"),
                                            color = Color(0xFF2ECC71),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // B. SEARCH & DIRECTORY PANEL
        item {
            Text(
                text = t.get("ACTIVE CLIENT LEADS", "قائمة العملاء النشطين"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BauhausRed,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search text field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(t.get("Search by client name or phone...", "البحث باسم العميل أو رقم الهاتف..."), color = BauhausMediumGray) },
                    textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BauhausRed,
                        unfocusedBorderColor = BauhausGridLine,
                        focusedContainerColor = BauhausDarkGray,
                        unfocusedContainerColor = BauhausDarkGray
                    ),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = BauhausMediumGray) }
                )

                // IMPORT FROM CALL LOG TO SEARCH BUTTON
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .neumorphic(shapeRadius = 16.dp, elevation = 4.dp, backgroundColor = BauhausRed)
                        .clickable { showCallLogImportSearchDialog = true }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Import from logs to search",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = t.get("Log Search", "بحث السجل"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            // Status Filters
            Text(
                text = t.get("FILTER BY STATUS:", "تصفية حسب الحالة:"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BauhausMediumGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAll = selectedStatusFilter == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAll) BauhausRed else Color.White.copy(alpha = 0.05f))
                            .clickable { selectedStatusFilter = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = t.get("All Status", "كل الحالات"), color = BauhausWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                listOf("Hot Lead", "Follow-Up", "Not Interested").forEach { statusVal ->
                    item {
                        val isSelected = selectedStatusFilter == statusVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        when (statusVal) {
                                            "Hot Lead" -> BauhausRed
                                            "Follow-Up" -> Color(0xFFF39C12)
                                            else -> BauhausMediumGray
                                        }
                                    } else Color.White.copy(alpha = 0.05f)
                                )
                                .clickable { selectedStatusFilter = statusVal }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (statusVal) {
                                    "Hot Lead" -> t.get("Hot Lead", "مهتم جداً")
                                    "Follow-Up" -> t.get("Follow-Up", "متابعة")
                                    else -> t.get("Not Interested", "غير مهتم")
                                },
                                color = BauhausWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Offer Filters
            Text(
                text = t.get("FILTER BY TEMPLATE OFFER:", "تصفية حسب العرض:"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BauhausMediumGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAll = selectedOfferFilter == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAll) BauhausRed else Color.White.copy(alpha = 0.05f))
                            .clickable { selectedOfferFilter = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = t.get("All Offers", "جميع العروض"), color = BauhausWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                templates.forEach { temp ->
                    item {
                        val isSelected = selectedOfferFilter == temp.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BauhausRed else Color.White.copy(alpha = 0.05f))
                                .clickable { selectedOfferFilter = temp.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isAr) temp.arabicName else temp.name,
                                color = BauhausWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rating Filters
            Text(
                text = t.get("FILTER BY RATING:", "تصفية حسب التقييم:"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BauhausMediumGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAll = selectedRatingFilter == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAll) BauhausRed else Color.White.copy(alpha = 0.05f))
                            .clickable { selectedRatingFilter = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = t.get("All Ratings", "كل التقييمات"), color = BauhausWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                (1..5).forEach { ratingVal ->
                    item {
                        val isSelected = selectedRatingFilter == ratingVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BauhausRed else Color.White.copy(alpha = 0.05f))
                                .clickable { selectedRatingFilter = ratingVal }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "★".repeat(ratingVal) + "☆".repeat(5 - ratingVal),
                                color = BauhausYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (showCallLogImportSearchDialog) {
                AlertDialog(
                    onDismissRequest = { showCallLogImportSearchDialog = false },
                    modifier = Modifier.border(1.dp, BauhausRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                    containerColor = BauhausDarkGray,
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text(
                            text = t.get("SELECT NUMBER TO SEARCH", "اختر رقماً للبحث عنه"),
                            color = BauhausWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = t.get("Select a number from recent logs to search:", "اختر رقماً من سجل المكالمات لملء حقل البحث:"),
                                color = BauhausMediumGray,
                                fontSize = 12.sp
                            )
                            Box(modifier = Modifier.heightIn(max = 300.dp)) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (recentCalls.isEmpty()) {
                                        item {
                                            Text(
                                                text = t.get("No recent call logs available.", "لا يوجد مكالمات هاتفية حديثة."),
                                                color = BauhausMediumGray,
                                                fontSize = 13.sp
                                            )
                                        }
                                    } else {
                                        items(recentCalls) { call ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        searchQuery = call.number
                                                        showCallLogImportSearchDialog = false
                                                    }
                                                    .border(1.dp, BauhausGlassBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                                colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = call.name ?: t.get("Unknown Number", "رقم غير مسجل"),
                                                            color = BauhausWhite,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = call.number,
                                                            color = BauhausMediumGray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.Search,
                                                        contentDescription = "Search",
                                                        tint = BauhausRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showCallLogImportSearchDialog = false }) {
                            Text(text = t.get("CLOSE", "إغلاق"), color = BauhausMediumGray)
                        }
                    }
                )
            }
        }

        item {
            ReportDownloadBar(
                title = t.get("CLIENT RECONCILIATION REPORT", "تقرير تصفية ومطابقة العملاء"),
                clients = filteredLeads,
                isPremium = isPremium,
                onTriggerPaywall = onTriggerPaywall
            )
        }

        // Leads list items
        if (filteredLeads.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.get("No clients match your search criteria.", "لا يوجد عملاء يطابقون البحث."),
                        color = BauhausMediumGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            items(filteredLeads) { lead ->
                val isSelected = activeLead?.id == lead.id
                Card(
                    onClick = { onSelectLead(lead) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(
                            shapeRadius = 18.dp,
                            elevation = if (isSelected) 3.dp else 6.dp,
                            lightShadowColor = NeuLightShadow,
                            darkShadowColor = if (isSelected) BauhausRed.copy(alpha = 0.3f) else NeuDarkShadow
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = BauhausDarkGray
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = lead.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BauhausWhite
                            )
                            Text(
                                text = lead.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = BauhausMediumGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Status badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (lead.status) {
                                                "Hot Lead" -> BauhausRed.copy(alpha = 0.2f)
                                                "Follow-Up" -> Color(0xFFF39C12).copy(alpha = 0.2f)
                                                else -> BauhausMediumGray.copy(alpha = 0.2f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = when (lead.status) {
                                            "Hot Lead" -> t.get("Hot Lead", "مهتم جداً")
                                            "Follow-Up" -> t.get("Follow-Up", "متابعة")
                                            else -> t.get("Not Interested", "غير مهتم")
                                        },
                                        color = when (lead.status) {
                                            "Hot Lead" -> BauhausRed
                                            "Follow-Up" -> Color(0xFFF39C12)
                                            else -> BauhausMediumGray
                                        },
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // Stars rating representation
                                Text(
                                    text = "★".repeat(lead.rating) + "☆".repeat(5 - lead.rating),
                                    color = BauhausYellow,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Details",
                            tint = if (isSelected) BauhausRed else BauhausMediumGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // C. SELECTED ACTIVE LEAD EXPANDABLE DETAILS
        if (activeLead != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = t.get("SELECTED CLIENT DETAILS", "تفاصيل العميل المفتوح حالياً"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BauhausRed,
                    letterSpacing = 1.sp
                )
            }

            item {
                val currentTemplate = templates.find { "Template ${it.id}" == activeLead.offerType } ?: templates.firstOrNull()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(shapeRadius = 24.dp, elevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header with Name & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = activeLead.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BauhausWhite
                                )
                                Text(
                                    text = activeLead.phone,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BauhausMediumGray
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onArchiveLead(activeLead) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Archive lead",
                                        tint = BauhausRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onExportDossier,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Export dossier",
                                        tint = BauhausWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status and Score selectors
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Clickable rating stars
                            Column {
                                Text(text = t.get("Client Rating Score", "تقييم العميل"), fontSize = 11.sp, color = BauhausMediumGray)
                                Row {
                                    for (i in 1..5) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Star $i",
                                            tint = if (i <= activeLead.rating) BauhausYellow else Color.White.copy(alpha = 0.2f),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable { onRatingChange(i) }
                                        )
                                    }
                                }
                            }

                            // Status Tag toggler
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = t.get("Interest Status", "مستوى الاهتمام"), fontSize = 11.sp, color = BauhausMediumGray)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Hot Lead", "Follow-Up", "Not Interested").forEach { tag ->
                                        val isTagActive = activeLead.status == tag
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isTagActive) {
                                                        when (tag) {
                                                            "Hot Lead" -> BauhausRed
                                                            "Follow-Up" -> Color(0xFFF39C12)
                                                            else -> BauhausMediumGray
                                                        }
                                                    } else Color.White.copy(alpha = 0.05f)
                                                )
                                                .clickable { onStatusChange(tag) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = when (tag) {
                                                    "Hot Lead" -> t.get("Hot", "مهتم")
                                                    "Follow-Up" -> t.get("Follow", "متابعة")
                                                    else -> t.get("Not", "مستبعد")
                                                },
                                                color = BauhausWhite,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // UPCOMING MEETING / FOLLOW-UP CALL CARD (Requirement 3)
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Meeting icon",
                                        tint = BauhausRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = t.get("UPCOMING MEETING / FOLLOW-UP CALL:", "الموعد القادم / مكالمة المتابعة:"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BauhausRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = activeLead.nextMeeting ?: t.get("No upcoming meetings or follow-up calls scheduled.", "لا يوجد مواعيد أو مكالمات متابعة مجدولة حالياً."),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeLead.nextMeeting != null) BauhausWhite else BauhausMediumGray
                                )
                            }
                        }

                        // SCHEDULER ACTION ITEM WITH ALARM PRESETS (Requirement 3)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = t.get("ACTION ITEM: SCHEDULE CALL / SET MEETING WITH ALARM:", "إجراء: جدولة اتصال أو موعد جديد مع تنبيه:"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BauhausWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        var customMeetingLabel by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = customMeetingLabel,
                            onValueChange = { customMeetingLabel = it },
                            placeholder = { Text(t.get("e.g. Follow up call, Project discussion, Deal closing", "مثال: مكالمة متابعة، مناقشة المشروع، إغلاق الصفقة"), fontSize = 11.sp, color = BauhausMediumGray) },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val presets = listOf(
                                Triple(10 * 1000L, t.get("10 Sec (Test)", "١٠ ث (تجربة)"), "INSTANT"),
                                Triple(10 * 60 * 1000L, t.get("10 Min", "١٠ دقائق"), "10M"),
                                Triple(60 * 60 * 1000L, t.get("1 Hour", "ساعة"), "1H"),
                                Triple(12 * 60 * 60 * 1000L, t.get("Tomorrow", "غداً"), "TOMORROW"),
                                Triple(7 * 24 * 60 * 60 * 1000L, t.get("Next Week", "الأسبوع القادم"), "WEEK")
                            )
                            presets.forEach { (delayMs, presetLabel, typeCode) ->
                                Button(
                                    onClick = {
                                        val meetingLabel = if (customMeetingLabel.isNotBlank()) customMeetingLabel else t.get("Follow-up Call", "مكالمة متابعة")
                                        onScheduleMeeting(delayMs, meetingLabel)
                                        customMeetingLabel = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BauhausRed.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(text = presetLabel, fontSize = 9.sp, color = BauhausWhite, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = BauhausGlassBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Template Quick Dropdown/Selector
                        Text(
                            text = t.get("Select Offer Template for WhatsApp", "العرض المخصص للواتساب"),
                            fontSize = 11.sp,
                            color = BauhausMediumGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            templates.forEach { temp ->
                                val isSelected = temp.id == activeTemplateId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) BauhausRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, if (isSelected) BauhausRed else Color.Transparent, RoundedCornerShape(10.dp))
                                        .clickable { onTemplateChange(temp.id) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isAr) temp.arabicName.take(12) + ".." else temp.name.take(12) + "..",
                                        color = if (isSelected) BauhausWhite else BauhausMediumGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // SLIDER: REAL-TIME ADJUST OFFER PRICE (Bauhaus Dial design matching designhill style)
                        if (currentTemplate != null) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = t.get("Set Price (KWD)", "تحديد السعر النهائي (د.ك)"),
                                    fontSize = 11.sp,
                                    color = BauhausMediumGray
                                )
                                Text(
                                    text = "${String.format("%,d", currentPrice)} ${currentTemplate.currency}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BauhausRed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = currentPrice.toFloat(),
                                onValueChange = { onPriceChange(it.toInt()) },
                                valueRange = currentTemplate.minPrice.toFloat()..currentTemplate.maxPrice.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = BauhausRed,
                                    activeTrackColor = BauhausRed,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "${String.format("%,d", currentTemplate.minPrice)}", fontSize = 9.sp, color = BauhausMediumGray)
                                Text(text = "${String.format("%,d", currentTemplate.maxPrice)}", fontSize = 9.sp, color = BauhausMediumGray)
                            }

                            // Dynamic Live WhatsApp Compiled Message Preview
                            Spacer(modifier = Modifier.height(14.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                            ) {
                                val previewMsg = currentTemplate.messageTemplate
                                    .replace("{name}", activeLead.name)
                                    .replace("{offer}", if (isAr) currentTemplate.arabicName else currentTemplate.name)
                                    .replace("{price}", String.format("%,d", currentPrice))
                                    .replace("{currency}", currentTemplate.currency)
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = t.get("LIVE SECURE PREVIEW:", "معاينة الرسالة قبل الإرسال:"),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BauhausRed
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = previewMsg,
                                        fontSize = 11.sp,
                                        color = BauhausLightGray,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // SEND WHATSAPP PRIMARY BUTTON
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onSendWhatsApp(activeLead, currentTemplate, currentPrice) },
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = t.get("SEND SECURE WHATSAPP OFFER", "إرسال العرض الترويجي عبر واتساب"),
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = BauhausGlassBorder)
                        Spacer(modifier = Modifier.height(16.dp))

                        // OFFLINE VOICE TRANSCRIPTION RECORDER
                        Text(
                            text = t.get("OFFLINE VOICE TRANSCRIPT NOTES", "الملاحظات الصوتية المباشرة (دون إنترنت)"),
                            style = MaterialTheme.typography.titleSmall,
                            color = BauhausWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = t.get("Record client call negotiations offline. Automatically processed on-device.", "سجل تفاصيل المفاوضات الهاتفية مباشرة وسيتم تحويل الصوت إلى نص تلقائياً."),
                            style = MaterialTheme.typography.bodySmall,
                            color = BauhausMediumGray
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing voice recording action circle
                            IconButton(
                                onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(if (isRecording) BauhausRed else Color.White.copy(alpha = 0.05f), CircleShape)
                                    .border(1.dp, if (isRecording) BauhausWhite else BauhausGlassBorder, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Lock else Icons.Default.PlayArrow,
                                    contentDescription = "Mic Recorder",
                                    tint = BauhausWhite
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = if (isRecording) t.get("RECORDING VOICE ON-DEVICE...", "جاري تسجيل الصوت محلياً...") else t.get("TAP TO RECORD DIALOGUE", "اضغط لبدء التسجيل والمزامنة"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRecording) BauhausRed else BauhausWhite
                                )
                                Text(
                                    text = if (isRecording) t.get("Whisper Engine processing stream...", "محرك معالجة الصوت يعمل بالخلفية...") else t.get("Offline on-device secure voice ledger", "نظام أمني لتسجيل المحاضر الصوتية"),
                                    fontSize = 10.sp,
                                    color = BauhausMediumGray
                                )
                            }
                        }

                        if (isRecording || transcription.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BauhausBlack, RoundedCornerShape(12.dp))
                                    .border(1.dp, BauhausGlassBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = transcription,
                                    color = BauhausLightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // MANUAL NOTE FIELD
                        Spacer(modifier = Modifier.height(16.dp))
                        var manualNoteText by remember { mutableStateOf("") }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = manualNoteText,
                                onValueChange = { manualNoteText = it },
                                placeholder = { Text(t.get("Write manual ledger note...", "اكتب ملاحظة نصية يدوية..."), fontSize = 11.sp, color = BauhausMediumGray) },
                                textStyle = TextStyle(color = BauhausWhite, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BauhausRed,
                                    unfocusedBorderColor = BauhausGlassBorder,
                                    focusedContainerColor = BauhausBlack,
                                    unfocusedContainerColor = BauhausBlack
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    onAddNote(manualNoteText)
                                    manualNoteText = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(text = t.get("Add", "إضافة"), fontSize = 11.sp, color = BauhausWhite)
                            }
                        }

                        // CHRONOLOGICAL ENCRYPTED LEDGER TIMELINE (Requirement 3)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = t.get("INTERACTION HISTORIES & LEDGERS:", "سجل التفاعلات الزمني المحمي:"),
                            style = MaterialTheme.typography.titleSmall,
                            color = BauhausWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var ledgerFilter by remember { mutableStateOf("ALL") }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            val filters = listOf(
                                "ALL" to t.get("All", "الكل"),
                                "OFFERS" to t.get("Offers", "العروض"),
                                "CALLS" to t.get("Calls", "اتصالات"),
                                "MEETINGS" to t.get("Meetings", "المواعيد"),
                                "NOTES" to t.get("Notes", "ملاحظات")
                            )
                            filters.forEach { (key, label) ->
                                val isSelected = ledgerFilter == key
                                Button(
                                    onClick = { ledgerFilter = key },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) BauhausRed else Color.White.copy(alpha = 0.05f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        color = if (isSelected) BauhausWhite else BauhausMediumGray,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val leadEvents = events.filter { it.leadId == activeLead.id }
                        val filteredLeadEvents = leadEvents.filter { ev ->
                            when (ledgerFilter) {
                                "ALL" -> true
                                "OFFERS" -> ev.type == "whatsapp_offer"
                                "CALLS" -> ev.type == "call_log" || ev.content.contains("🎙️") || ev.content.contains("Voice report") || ev.content.contains("Call duration")
                                "MEETINGS" -> ev.type == "meeting"
                                "NOTES" -> ev.type == "manual_note" && !ev.content.contains("🎙️") && !ev.content.contains("Voice report")
                                else -> true
                            }
                        }

                        if (filteredLeadEvents.isEmpty()) {
                            Text(
                                text = t.get("No matching interactions recorded yet.", "لا توجد تفاعلات مطابقة مسجلة لهذا العميل بعد."),
                                fontSize = 11.sp,
                                color = BauhausMediumGray,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            filteredLeadEvents.forEach { ev ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = when (ev.type) {
                                                    "whatsapp_offer" -> t.get("OFFER SENT 📤", "تم إرسال عرض 📤")
                                                    "call_log" -> t.get("CALL RECORD 📞", "سجل مكالمة 📞")
                                                    "meeting" -> t.get("MEETING SET 📅", "موعد مجدول 📅")
                                                    else -> t.get("MANUAL NOTE 📝", "ملاحظة يدوية 📝")
                                                },
                                                color = when (ev.type) {
                                                    "whatsapp_offer" -> BauhausRed
                                                    "call_log" -> Color(0xFF3498DB)
                                                    "meeting" -> Color(0xFF2ECC71)
                                                    else -> BauhausYellow
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault()).format(Date(ev.timestamp)),
                                                color = BauhausMediumGray,
                                                fontSize = 8.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ev.content,
                                            color = BauhausWhite,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== TAB 3: CALENDAR TAB SCREEN ====================
@Composable
fun CalendarTabScreen(
    isAr: Boolean,
    t: Trans,
    activeLead: Lead?,
    leads: List<Lead>,
    onScheduleAlarm: (String) -> Unit,
    onCallLead: (String) -> Unit
) {
    var calendarState by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDayState by remember { mutableStateOf(Calendar.getInstance()) }

    val daysOfWeek = if (isAr) {
        listOf("أحد", "إثن", "ثلا", "أرب", "خمي", "جمع", "سبت")
    } else {
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    }

    val monthYearString = remember(calendarState, isAr) {
        val sdf = if (isAr) SimpleDateFormat("MMMM yyyy", Locale("ar")) else SimpleDateFormat("MMMM yyyy", Locale.US)
        sdf.format(calendarState.time).uppercase()
    }

    val daysInMonth = calendarState.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    val firstDayOfMonthCalendar = (calendarState.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val startDayOffset = firstDayOfMonthCalendar.get(Calendar.DAY_OF_WEEK) - 1

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. THE CALENDAR CARD ON TOP
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 24.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val prev = (calendarState.clone() as Calendar).apply {
                                    add(Calendar.MONTH, -1)
                                }
                                calendarState = prev
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Previous Month",
                                tint = BauhausWhite
                            )
                        }

                        Text(
                            text = monthYearString,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BauhausWhite,
                            textAlign = TextAlign.Center
                        )

                        IconButton(
                            onClick = {
                                val next = (calendarState.clone() as Calendar).apply {
                                    add(Calendar.MONTH, 1)
                                }
                                calendarState = next
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Next Month",
                                tint = BauhausWhite
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        daysOfWeek.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = BauhausMediumGray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val totalCells = daysInMonth + startDayOffset
                    val rowsCount = (totalCells + 6) / 7

                    for (row in 0 until rowsCount) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - startDayOffset + 1
                                val isValidDay = dayNum in 1..daysInMonth

                                if (isValidDay) {
                                    val isSelected = selectedDayState.get(Calendar.DAY_OF_MONTH) == dayNum &&
                                            selectedDayState.get(Calendar.MONTH) == calendarState.get(Calendar.MONTH) &&
                                            selectedDayState.get(Calendar.YEAR) == calendarState.get(Calendar.YEAR)
                                    
                                    val todayCal = Calendar.getInstance()
                                    val isToday = todayCal.get(Calendar.DAY_OF_MONTH) == dayNum &&
                                            todayCal.get(Calendar.MONTH) == calendarState.get(Calendar.MONTH) &&
                                            todayCal.get(Calendar.YEAR) == calendarState.get(Calendar.YEAR)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) BauhausRed else if (isToday) BauhausRed.copy(alpha = 0.15f) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) BauhausWhite else if (isToday) BauhausRed else Color.Transparent,
                                                CircleShape
                                            )
                                            .clickable {
                                                val newSel = (calendarState.clone() as Calendar).apply {
                                                    set(Calendar.DAY_OF_MONTH, dayNum)
                                                }
                                                selectedDayState = newSel
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            color = if (isSelected) BauhausWhite else if (isToday) BauhausRed else BauhausWhite,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // B. TODAYS ACTIONS BELOW THE CALENDAR
        item {
            val selectedDateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(selectedDayState.time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = t.get("TODAY'S ACTIONS & TASKS", "أعمال ومتابعات اليوم"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BauhausRed,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = t.get("Reminders for $selectedDateStr", "تذكيرات ليوم $selectedDateStr"),
                        style = MaterialTheme.typography.bodySmall,
                        color = BauhausMediumGray
                    )
                }
            }
        }

        if (leads.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(shapeRadius = 20.dp, elevation = 4.dp)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.get("No clients found. Add a client first.", "لا يوجد عملاء مسجلين حالياً للجدولة."),
                        color = BauhausMediumGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            items(leads) { lead ->
                val isTargetLead = activeLead?.id == lead.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(
                            shapeRadius = 18.dp,
                            elevation = if (isTargetLead) 3.dp else 6.dp,
                            lightShadowColor = NeuLightShadow,
                            darkShadowColor = if (isTargetLead) BauhausRed.copy(alpha = 0.3f) else NeuDarkShadow
                        ),
                    colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lead.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = lead.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BauhausMediumGray
                                )
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        onCallLead(lead.phone)
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF2ECC71), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call Client",
                                        tint = BauhausWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        onScheduleAlarm("INSTANT")
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(BauhausRed, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Schedule Reminder",
                                        tint = BauhausWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (lead.status == "Hot Lead") BauhausRed.copy(alpha = 0.15f) else BauhausWhite.copy(alpha = 0.05f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = lead.status,
                                    color = if (lead.status == "Hot Lead") BauhausRed else BauhausLightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = t.get("Action scheduled for selected day", "مجدول للمتابعة في هذا اليوم"),
                                fontSize = 10.sp,
                                color = BauhausMediumGray
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== TAB 4: SETTINGS TAB SCREEN ====================
@Composable
fun SettingsTabScreen(
    isAr: Boolean,
    t: Trans,
    leadsCount: Int,
    templatesCount: Int,
    isSubscribedSaaS: Boolean,
    isOrgUnlocked: Boolean,
    onActivateOrgCode: (String) -> Boolean,
    onDeactivateOrg: () -> Unit,
    onPurchaseSaaS: () -> Unit,
    onCancelSaaS: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onResetDB: () -> Unit
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = t.get("SYSTEM PREFERENCES & HARDWARE STATISTICS", "تفضيلات النظام وإحصائيات الذاكرة"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BauhausRed,
                letterSpacing = 1.sp
            )
        }

        // Statistics Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 20.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t.get("LOCAL SYSTEM HARDWARE DIAGNOSTICS", "فحص سلامة البيانات المحلية"),
                        fontSize = 10.sp,
                        color = BauhausRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "$leadsCount", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = BauhausWhite)
                            Text(text = t.get("Registered Leads", "العملاء المسجلين"), fontSize = 11.sp, color = BauhausMediumGray)
                        }
                        Column {
                            Text(text = "$templatesCount", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = BauhausWhite)
                            Text(text = t.get("Active Templates", "القوالب النشطة"), fontSize = 11.sp, color = BauhausMediumGray)
                        }
                        Column {
                            Text(text = "100%", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2ECC71))
                            Text(text = t.get("System Speed", "سرعة النظام"), fontSize = 11.sp, color = BauhausMediumGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = BauhausGlassBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = t.get("LOCAL DATABASE SPEED: OPTIMIZED FOR ULTRA-FAST INTERFACE RENDERING.", "سرعة قاعدة البيانات المحلية: مهيأة لأقصى سرعة استجابة وتصفح فوري."),
                        fontSize = 10.sp,
                        color = BauhausMediumGray,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 1. Subscription & SaaS Monetization Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 20.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t.get("COMMERCIAL SAAS SUBSCRIPTION & LICENSING", "الاشتراك التجاري وترخيص الاستخدام"),
                        style = MaterialTheme.typography.titleSmall,
                        color = BauhausWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = t.get("Access unlimited client contacts, secure offline vaults, and native report exports.", "افتح حد الاتصالات غير المحدود وميزة تصدير التقارير الكاملة للعملاء."),
                        fontSize = 11.sp,
                        color = BauhausMediumGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t.get("Subscription Status:", "حالة الاشتراك:"),
                            fontSize = 13.sp,
                            color = BauhausWhite
                        )
                        Text(
                            text = if (isSubscribedSaaS) t.get("SUBSCRIBED (PRO) 👑", "مشترك (برو) 👑") else t.get("FREE PLAN (Limit 50)", "الخطة المجانية (حد ٥٠ عميل)"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSubscribedSaaS) Color(0xFF2ECC71) else BauhausYellow
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isSubscribedSaaS) {
                        Button(
                            onClick = onPurchaseSaaS,
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = t.get("SUBSCRIBE NOW — 5 KWD/Month", "اشترك الآن — ٥ د.ك/شهرياً"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open Subscriptions link.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = t.get("MANAGE SUBSCRIPTION", "إدارة الاشتراك"), color = BauhausWhite, fontSize = 11.sp)
                            }
                            Button(
                                onClick = onCancelSaaS,
                                colors = ButtonDefaults.buttonColors(containerColor = BauhausRed.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = t.get("CANCEL (SIMULATION)", "إلغاء الاشتراك"), color = BauhausRed, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = t.get("Terms: 5 KWD/month. Auto-renews monthly unless canceled. Cancel anytime in Google Play Store Settings.", "الشروط: ٥ د.ك شهرياً. يتجدد تلقائياً ما لم يتم الإلغاء. يمكنك الإلغاء في أي وقت عبر متجر جوجل بلاي."),
                        fontSize = 9.sp,
                        color = BauhausMediumGray,
                        lineHeight = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = t.get("Privacy Policy", "سياسة الخصوصية"),
                        fontSize = 10.sp,
                        color = BauhausRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://zero-crm.notion.site/Privacy-Policy-Offline-CRM"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Privacy Policy link.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        // 2. Organization Secure Offline Activation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 20.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t.get("OFFLINE ORGANIZATION ACTIVATION", "تفعيل المؤسسات دون اتصال بالإنترنت"),
                        style = MaterialTheme.typography.titleSmall,
                        color = BauhausWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = t.get("Unlock your organization's custom enterprise vault using a mathematical validation checksum code.", "قم بتفعيل الترخيص اللامركزي لمؤسستك عبر خوارزمية فحص الرموز الأمنية المباشرة."),
                        fontSize = 11.sp,
                        color = BauhausMediumGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t.get("Organization Status:", "حالة تفعيل المؤسسة:"),
                            fontSize = 13.sp,
                            color = BauhausWhite
                        )
                        Text(
                            text = if (isOrgUnlocked) t.get("ORGANIZATION UNLOCKED 🏢", "مفعل للمؤسسة 🏢") else t.get("LOCKED", "غير مفعل"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOrgUnlocked) Color(0xFF2ECC71) else BauhausMediumGray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isOrgUnlocked) {
                        var orgCodeInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = orgCodeInput,
                            onValueChange = { orgCodeInput = it },
                            placeholder = { Text(t.get("e.g. ZR-84A9-B2X1", "مثال: ZR-84A9-B2X1"), fontSize = 12.sp, color = BauhausMediumGray) },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (onActivateOrgCode(orgCodeInput)) {
                                    Toast.makeText(context, t.get("Organization successfully activated!", "تم تفعيل ترخيص المؤسسة بنجاح!"), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, t.get("Invalid organization code.", "رمز تفعيل غير صحيح."), Toast.LENGTH_SHORT).show()
                                }
                                orgCodeInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = t.get("ACTIVATE LICENSE CODE", "تحقق وتفعيل رمز الترخيص"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onDeactivateOrg,
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausRed.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = t.get("DEACTIVATE LICENSE", "إلغاء تفعيل الترخيص الحالي"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Language toggle
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 20.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t.get("SYSTEM RE-INTERFACE LANGUAGE", "تغيير لغة الواجهة والتشغيل"),
                        style = MaterialTheme.typography.titleSmall,
                        color = BauhausWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onLanguageChange("en") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isAr) BauhausRed else Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "ENGLISH", color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onLanguageChange("ar") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAr) BauhausRed else Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "العربية", color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Factory Reset DB Zone
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(shapeRadius = 20.dp, elevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = t.get("HARDWARE SYSTEM FACTORY RESET", "منطقة الحذف الكامل وضبط المصنع"),
                        style = MaterialTheme.typography.titleSmall,
                        color = BauhausRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = t.get("Wipes the internal secure database and restores default seed samples.", "سيقوم هذا بمسح كامل الذاكرة وإعادة تحميل العينات التلقائية."),
                        style = MaterialTheme.typography.bodySmall,
                        color = BauhausMediumGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (!showResetConfirmation) {
                        Button(
                            onClick = { showResetConfirmation = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = t.get("TRIGGER FACTORY RESET", "بدء ضبط المصنع الشامل"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = t.get("ARE YOU ABSOLUTELY SURE? THIS OPERATION IS IRREVERSIBLE.", "هل أنت متأكد تماماً؟ سيتم مسح كامل قاعدة البيانات المسجلة بالخلفية!"),
                                color = BauhausRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onResetDB()
                                        showResetConfirmation = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(text = t.get("YES, WIPE", "نعم، احذف الكل"), color = BauhausWhite)
                                }
                                Button(
                                    onClick = { showResetConfirmation = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(text = t.get("CANCEL", "إلغاء"), color = BauhausWhite)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== REUSABLE DIALOGS & POPUPS ====================

// ADD NEW LEAD DIALOG
@Composable
fun AddLeadDialog(
    isAr: Boolean,
    t: Trans,
    templates: List<OfferTemplateEntity>,
    recentCalls: List<CallLogEntry>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var selectionMode by remember { mutableStateOf<String?>(null) } // "LOG", "MANUAL", "CONFIRMATION" or null
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedTemplateId by remember { mutableStateOf(templates.firstOrNull()?.id ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, BauhausRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
        containerColor = BauhausDarkGray,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = if (selectionMode == null) {
                    t.get("ADD NEW CLIENT - CHOOSE OPTION", "إضافة عميل جديد - اختر طريقة")
                } else if (selectionMode == "LOG") {
                    t.get("IMPORT FROM PHONE CALL LOG", "استيراد من سجل المكالمات")
                } else {
                    t.get("REGISTER NEW CLIENT LEAD", "تسجيل ملف عميل جديد")
                },
                color = BauhausWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectionMode == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectionMode = "LOG" }
                            .border(1.dp, BauhausGlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Call Log",
                                tint = BauhausRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = t.get("Import From Call Log", "استيراد من سجل المكالمات"),
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = t.get("Choose from recently called numbers", "اختر من قائمة الأرقام التي اتصلت بك مؤخراً"),
                                    color = BauhausMediumGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectionMode = "MANUAL" }
                            .border(1.dp, BauhausGlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Manual Entry",
                                tint = BauhausRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = t.get("Add a Number Myself", "إدخال الرقم يدوياً بنفسي"),
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = t.get("Type phone and name manually", "اكتب اسم العميل ورقم هاتفه بنفسك"),
                                    color = BauhausMediumGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else if (selectionMode == "LOG") {
                    Text(
                        text = t.get("Select a recent call entry to register:", "اختر مكالمة لتسجيلها كعميل جديد:"),
                        color = BauhausMediumGray,
                        fontSize = 12.sp
                    )
                    
                    Box(modifier = Modifier.heightIn(max = 250.dp)) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (recentCalls.isEmpty()) {
                                item {
                                    Text(
                                        text = t.get("No recent calls found in log.", "لا يوجد مكالمات حديثة في السجل."),
                                        color = BauhausMediumGray,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            } else {
                                items(recentCalls) { entry ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                phone = entry.number
                                                name = entry.name ?: t.get("New Client", "عميل جديد")
                                                selectionMode = "CONFIRMATION"
                                            }
                                            .border(1.dp, BauhausGlassBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = BauhausBlack),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = entry.name ?: entry.number,
                                                    color = BauhausWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                if (entry.name != null) {
                                                    Text(
                                                        text = entry.number,
                                                        color = BauhausMediumGray,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                text = entry.duration,
                                                color = BauhausRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { selectionMode = null }) {
                        Text(text = t.get("← BACK", "← رجوع"), color = BauhausRed, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column {
                        Text(text = t.get("Client Full Name", "اسم العميل بالكامل"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth().testTag("add_lead_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Text(text = t.get("Client Phone (WhatsApp ready)", "رقم الهاتف للعميل (واتساب جاهز)"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            textStyle = TextStyle(color = BauhausWhite, fontSize = 14.sp),
                            placeholder = { Text(text = "+965...", color = BauhausMediumGray) },
                            modifier = Modifier.fillMaxWidth().testTag("add_lead_phone_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BauhausRed,
                                unfocusedBorderColor = BauhausGlassBorder,
                                focusedContainerColor = BauhausBlack,
                                unfocusedContainerColor = BauhausBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Text(text = t.get("Assign Initial Offer Template", "تعيين قالب العرض المبدئي"), fontSize = 11.sp, color = BauhausMediumGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        templates.forEach { temp ->
                            val isChosen = temp.id == selectedTemplateId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTemplateId = temp.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isChosen,
                                    onClick = { selectedTemplateId = temp.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = BauhausRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAr) temp.arabicName else temp.name,
                                    color = BauhausWhite,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { selectionMode = null }) {
                        Text(text = t.get("← BACK TO OPTIONS", "← العودة للخيارات"), color = BauhausRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (selectionMode == "MANUAL" || selectionMode == "CONFIRMATION") {
                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            onConfirm(name, phone, selectedTemplateId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BauhausRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = t.get("CONFIRM REGISTER", "تأكيد التسجيل"), color = BauhausWhite, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = t.get("CANCEL", "إلغاء"), color = BauhausMediumGray)
            }
        }
    )
}

@Composable
fun ReportDownloadBar(
    title: String,
    clients: List<Lead>,
    isPremium: Boolean,
    onTriggerPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, BauhausGlassBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BauhausWhite
                )
                Text(
                    text = "${clients.size} clients found",
                    fontSize = 11.sp,
                    color = BauhausMediumGray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isPremium) {
                            ReportExporter.exportToExcelCSV(context, "${title.replace(" ", "_")}_Export", clients)
                        } else {
                            onTriggerPaywall()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("XLS / CSV", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (isPremium) {
                            ReportExporter.exportToPdf(context, "${title.replace(" ", "_")}_Doc", clients)
                        } else {
                            onTriggerPaywall()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE02424)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("PDF", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Zero CRM") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
}

// ==================== TAB 5: ARCHIVE TAB SCREEN ====================
@Composable
fun ArchiveTabScreen(
    isAr: Boolean,
    t: Trans,
    leads: List<Lead>,
    templates: List<OfferTemplateEntity>,
    onRestoreLead: (Lead) -> Unit,
    onRestoreTemplate: (Int, Boolean) -> Unit,
    onCopyLeadDetails: (Lead, Context) -> Unit,
    onCopyTemplateDetails: (OfferTemplateEntity, Context) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableStateOf("LEADS") } // "LEADS" or "TEMPLATES"
    
    val archivedLeads = leads.filter { it.isArchived }
    val archivedTemplates = templates.filter { it.isArchived }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Segmented sub-tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BauhausDarkGray, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (subTab == "LEADS") BauhausRed else Color.Transparent)
                        .clickable { subTab = "LEADS" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.get("Archived Clients (${archivedLeads.size})", "العملاء المؤرشفون (${archivedLeads.size})"),
                        color = if (subTab == "LEADS") BauhausWhite else BauhausMediumGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (subTab == "TEMPLATES") BauhausRed else Color.Transparent)
                        .clickable { subTab = "TEMPLATES" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.get("Archived Templates (${archivedTemplates.size})", "القوالب المؤرشفة (${archivedTemplates.size})"),
                        color = if (subTab == "TEMPLATES") BauhausWhite else BauhausMediumGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        if (subTab == "LEADS") {
            if (archivedLeads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t.get("No archived clients.", "لا يوجد عملاء مؤرشفون."),
                            color = BauhausMediumGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(archivedLeads) { lead ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(shapeRadius = 18.dp, elevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = lead.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BauhausWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = lead.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BauhausMediumGray
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { onCopyLeadDetails(lead, context) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Copy details",
                                        tint = BauhausWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onRestoreLead(lead)
                                        Toast.makeText(context, t.get("Client restored!", "تم استرجاع العميل!"), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Restore",
                                        tint = NeuGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (archivedTemplates.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t.get("No archived offer templates.", "لا يوجد قوالب عروض مؤرشفة."),
                            color = BauhausMediumGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(archivedTemplates) { temp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(shapeRadius = 18.dp, elevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = BauhausDarkGray),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isAr) temp.arabicName else temp.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = BauhausWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = t.get("Default price: ${temp.defaultPrice} ${temp.currency}", "السعر الافتراضي: ${temp.defaultPrice} ${temp.currency}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BauhausMediumGray
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { onCopyTemplateDetails(temp, context) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Copy message",
                                            tint = BauhausWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onRestoreTemplate(temp.id, false)
                                            Toast.makeText(context, t.get("Template restored!", "تم استرجاع القالب!"), Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Restore",
                                            tint = NeuGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = temp.messageTemplate,
                                style = MaterialTheme.typography.bodySmall,
                                color = BauhausMediumGray,
                                maxLines = 2,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
