package com.example.safora.features.monitoring

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.RiskLevel
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.audio.AudioMonitoringService
import com.example.safora.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI PIPELINE DEBUG & LIVE TELEMETRY CONSOLE.
 *
 * Real-time diagnostic console to inspect:
 * 1. Microphone Audio Feed (VU level dBFS, voice detection).
 * 2. Model 1 (YAMNet acoustic scream/distress detection).
 * 3. Model 2 (Vosk live recognized words and rolling transcript).
 * 4. Model 4 (DistilBERT conversation safety classification).
 * 5. Multi-Stage Risk Decision Engine decisions and escalation.
 */
@Composable
fun MonitoringScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val isMonitoring by repository.isAudioMonitoring.collectAsState()
    val diagnostics by repository.audioDiagnostics.collectAsState()
    val sensitivity by repository.audioSensitivity.collectAsState()
    val audioHistory by repository.audioHistory.collectAsState()

    var hasAudioPermission by remember { mutableStateOf(PermissionHelper.hasAudioPermission(context)) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            AudioMonitoringService.start(context)
        }
    }

    // Auto-prompt permission if not granted on launch, or auto-start monitoring if granted
    LaunchedEffect(Unit) {
        hasAudioPermission = PermissionHelper.hasAudioPermission(context)
        if (!hasAudioPermission) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        } else {
            AudioMonitoringService.start(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CharcoalSurfaceElevated)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AI PIPELINE DEBUG CONSOLE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Real-Time Audio, Model & Decision Telemetry",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            // Mic Power Toggle Pill
            Surface(
                modifier = Modifier.clickable {
                    if (isMonitoring) {
                        AudioMonitoringService.stop(context)
                    } else {
                        if (hasAudioPermission) {
                            AudioMonitoringService.start(context)
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                color = if (isMonitoring) EmeraldSafe.copy(alpha = 0.15f) else CharcoalSurfaceElevated,
                border = BorderStroke(1.dp, if (isMonitoring) EmeraldSafe else CharcoalBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isMonitoring) EmeraldSafe else TextTertiary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMonitoring) "MIC LIVE" else "MIC PAUSED",
                        color = if (isMonitoring) EmeraldSafe else TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. Microphone Permission Alert Banner (if permission is missing)
        if (!hasAudioPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CrimsonEmergency.copy(alpha = 0.15f)),
                border = BorderStroke(1.5.dp, CrimsonEmergency),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = "Mic Denied",
                            tint = CrimsonEmergency,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MICROPHONE ACCESS DENIED",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = CrimsonEmergency
                            )
                            Text(
                                text = "SAFORA cannot listen or detect screams because microphone permission is missing.",
                                fontSize = 10.sp,
                                color = TextPrimary
                            )
                        }
                    }
                    Button(
                        onClick = { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonEmergency),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("ALLOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Live listening active confirmation banner
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = EmeraldSafe.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(EmeraldSafe)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎙️ LIVE AUDIO LISTENING ACTIVE • Notification running on status bar",
                        color = EmeraldSafe,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // 1. HARDWARE AUDIO RECORD & VAD INPUT INSPECTION
        // =========================================================================
        DebugSectionHeader(title = "1. MICROPHONE AUDIO INPUT & VAD GATE")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(1.dp, CharcoalBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Audio Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isMonitoring) EmeraldSafe else AmberWarning)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMonitoring) "AudioRecord: 16,000 Hz Mono PCM" else "Microphone Standby",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // VAD Speech Gate Pill
                    Surface(
                        color = if (diagnostics.isVadSpeechActive) EmeraldSafe.copy(alpha = 0.2f) else CharcoalSurfaceElevated,
                        border = BorderStroke(1.dp, if (diagnostics.isVadSpeechActive) EmeraldSafe else CharcoalBorder),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (diagnostics.isVadSpeechActive) "SPEECH DETECTED" else "AMBIENT / CALM",
                            color = if (diagnostics.isVadSpeechActive) EmeraldSafe else TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time VU Meter Bar
                val normalizedDbfs = ((diagnostics.currentRmsDbfs + 80f) / 80f).coerceIn(0.01f, 1.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE VU:",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(55.dp)
                    )
                    LinearProgressIndicator(
                        progress = { normalizedDbfs },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            diagnostics.currentRmsDbfs > -20f -> CrimsonEmergency
                            diagnostics.currentRmsDbfs > -40f -> AmberWarning
                            else -> EmeraldSafe
                        },
                        trackColor = CharcoalBorder
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${"%.1f".format(diagnostics.currentRmsDbfs)} dBFS",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(65.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Telemetry Sub-metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Noise Floor: ${"%.1f".format(diagnostics.noiseFloorDbfs)} dBFS",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Sensitivity: $sensitivity",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Buffer: 10s Ring RAM",
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // 2. MODEL 1: YAMNET ACOUSTIC DISTRESS & SCREAM DETECTION
        // =========================================================================
        DebugSectionHeader(title = "2. MODEL 1: YAMNET ACOUSTIC CLASSIFIER")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(
                1.dp,
                if (diagnostics.isScreamFastTrackFired) CrimsonEmergency else CharcoalBorder
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Detected Acoustic Event:",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = diagnostics.yamnetDominantClass.ifBlank { "Analyzing audio window..." },
                            color = if (diagnostics.yamnetScreamConfidence >= 0.72f) CrimsonEmergency else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Surface(
                        color = CharcoalSurfaceElevated,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${(diagnostics.yamnetDominantConfidence * 100).toInt()}% Conf",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scream Distress Score Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SCREAM SCORE:",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(90.dp)
                    )
                    LinearProgressIndicator(
                        progress = { diagnostics.yamnetScreamConfidence },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (diagnostics.yamnetScreamConfidence >= 0.72f) CrimsonEmergency else AmberWarning,
                        trackColor = CharcoalBorder
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(diagnostics.yamnetScreamConfidence * 100).toInt()}%",
                        color = if (diagnostics.yamnetScreamConfidence >= 0.72f) CrimsonEmergency else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fast-Track Escalation Status
                Surface(
                    color = if (diagnostics.isScreamFastTrackFired) CrimsonEmergency.copy(alpha = 0.2f) else CharcoalSurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (diagnostics.isScreamFastTrackFired) Icons.Default.Warning else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (diagnostics.isScreamFastTrackFired) CrimsonEmergency else CyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (diagnostics.isScreamFastTrackFired) {
                                "FAST-TRACK SCREAM EMERGENCY TRIGGERED (>72%)"
                            } else {
                                "Fast-track scream escalation standby (Triggers at >= 72%)"
                            },
                            color = if (diagnostics.isScreamFastTrackFired) CrimsonEmergency else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Total Windows Analyzed: ${diagnostics.totalInferencesCount} • Window: 0.975s • Step: 0.5s",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // 3. MODEL 2 & 3: VOSK DUAL-LANGUAGE SPEECH RECOGNITION (EN / HI)
        // =========================================================================
        DebugSectionHeader(title = "3. MODEL 2 & 3: VOSK DUAL SPEECH-TO-TEXT (EN / HI)")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(1.dp, CharcoalBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Latest Speech Transcript:",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = if (diagnostics.detectedLanguage == "HI") AmberWarning.copy(alpha = 0.2f) else EmeraldSafe.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (diagnostics.detectedLanguage == "HI") "KALDI [HI] HINDI" else "KALDI [EN] ENGLISH",
                            color = if (diagnostics.detectedLanguage == "HI") AmberWarning else EmeraldSafe,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Words container
                Surface(
                    color = CharcoalDark,
                    border = BorderStroke(1.dp, CharcoalBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (diagnostics.latestTranscriptWord.isNotBlank()) {
                            "[${diagnostics.detectedLanguage}] \"${diagnostics.latestTranscriptWord}\""
                        } else {
                            "(Listening for speech... Speak clearly in English or Hindi to test)"
                        },
                        color = if (diagnostics.latestTranscriptWord.isNotBlank()) TextPrimary else TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = if (diagnostics.latestTranscriptWord.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Rolling Context Window (45s Horizon):",
                    color = TextSecondary,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = diagnostics.rollingTranscriptBuffer.ifBlank { "No recent conversation context accumulated." },
                    color = TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // 4. MODEL 4: CONVERSATION SAFETY / THREAT CLASSIFIER
        // =========================================================================
        DebugSectionHeader(title = "4. MODEL 4: CONVERSATION SAFETY CLASSIFIER")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(
                1.dp,
                if (diagnostics.safetyRiskScore >= diagnostics.safetyThreshold) CrimsonEmergency else CharcoalBorder
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Safety Classification:",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = diagnostics.safetyLabel,
                            color = if (diagnostics.safetyLabel == "HARMFUL") CrimsonEmergency else EmeraldSafe,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Score pill
                    Surface(
                        color = if (diagnostics.safetyRiskScore >= diagnostics.safetyThreshold) CrimsonEmergency.copy(alpha = 0.2f) else CharcoalSurfaceElevated,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Score: ${"%.3f".format(diagnostics.safetyRiskScore)} / ${diagnostics.safetyThreshold}",
                            color = if (diagnostics.safetyRiskScore >= diagnostics.safetyThreshold) CrimsonEmergency else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Score Progress Bar
                LinearProgressIndicator(
                    progress = { (diagnostics.safetyRiskScore / 0.5f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (diagnostics.safetyRiskScore >= diagnostics.safetyThreshold) CrimsonEmergency else EmeraldSafe,
                    trackColor = CharcoalBorder
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Decision Threshold: ${diagnostics.safetyThreshold} (from safora_metadata.json) • Tracks threats, kidnapping, stalking and coercion",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // 5. RISK DECISION ENGINE (OVERALL DECISION & ACTION)
        // =========================================================================
        DebugSectionHeader(title = "5. RISK DECISION ENGINE (FINAL OUTPUT)")

        val riskColor = when (diagnostics.currentRiskLevel) {
            RiskLevel.HIGH -> CrimsonEmergency
            RiskLevel.MEDIUM -> AmberWarning
            RiskLevel.LOW -> EmeraldSafe
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.08f)),
            border = BorderStroke(1.5.dp, riskColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CURRENT SYSTEM RISK TIER:",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${diagnostics.currentRiskLevel} RISK",
                            color = riskColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }

                    Surface(
                        color = riskColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when (diagnostics.currentRiskLevel) {
                                RiskLevel.HIGH -> "EMERGENCY ACTIVE"
                                RiskLevel.MEDIUM -> "CHECK-IN REQUIRED"
                                RiskLevel.LOW -> "MONITORING"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Decision Reason:",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = diagnostics.riskDecisionReason,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // 6. REAL-TIME ACOUSTIC EVENT LOG STREAM
        // =========================================================================
        DebugSectionHeader(title = "6. REAL-TIME EVENT STREAM LOG")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(1.dp, CharcoalBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (audioHistory.isEmpty()) {
                    Text(
                        text = "No detection events logged yet. Speak or make sounds into the microphone.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    audioHistory.take(8).forEach { event ->
                        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(event.timestamp))
                        val evColor = when (event.riskLevel) {
                            RiskLevel.HIGH -> CrimsonEmergency
                            RiskLevel.MEDIUM -> AmberWarning
                            RiskLevel.LOW -> EmeraldSafe
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.detectedClass,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (event.details.isNotBlank()) {
                                    Text(event.details, color = TextTertiary, fontSize = 9.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${event.riskLevel} (${(event.confidence * 100).toInt()}%)",
                                    color = evColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = time,
                                    color = TextTertiary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun DebugSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(12.dp)
                .background(CyanAccent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = CyanAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}
