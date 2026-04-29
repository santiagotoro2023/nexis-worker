package ch.toroag.nexis.worker.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.*
import ch.toroag.nexis.worker.util.SpeechRecognizerHelper

@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    vm: VoiceViewModel = viewModel(),
) {
    val state      by vm.state.collectAsState()
    val transcript by vm.transcript.collectAsState()
    val response   by vm.response.collectAsState()
    val error      by vm.errorMessage.collectAsState()

    val context = LocalContext.current
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPerm = granted
        if (granted) vm.startListening()
    }

    val helper = remember { SpeechRecognizerHelper(context) }
    DisposableEffect(Unit) { onDispose { helper.destroy() } }

    LaunchedEffect(state) {
        if (state == VoiceState.Listening) {
            helper.startListening(
                onResult = { text -> if (text.isBlank()) vm.onListeningCancelled() else vm.onTranscript(text) },
                onError  = { vm.onListeningCancelled() },
            )
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.20f,
        animationSpec = infiniteRepeatable(
            animation  = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(3000); vm.clearError() }
    }

    Column(
        Modifier.fillMaxSize().background(NxBg).systemBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.stopSpeaking(); onBack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("VOICE", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2)
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                when (state) {
                    VoiceState.Idle      -> "TAP TO SPEAK"
                    VoiceState.Listening -> "LISTENING..."
                    VoiceState.Thinking  -> "THINKING... TAP TO CANCEL"
                    VoiceState.Speaking  -> "SPEAKING — TAP TO STOP"
                },
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.15.sp,
                color = NxFg2, textAlign = TextAlign.Center,
            )

            Box(contentAlignment = Alignment.Center) {
                if (state == VoiceState.Listening) {
                    Box(
                        Modifier.size(136.dp).scale(pulseScale)
                            .background(NxOrange.copy(alpha = 0.12f), CircleShape)
                    )
                }
                Box(
                    Modifier
                        .size(100.dp)
                        .background(
                            color = when (state) {
                                VoiceState.Listening -> NxOrange
                                VoiceState.Thinking  -> NxOrangeDim
                                VoiceState.Speaking  -> NxOrangeDim
                                VoiceState.Idle      -> NxBg3
                            },
                            shape = CircleShape,
                        )
                        .border(1.5.dp, NxBorder, CircleShape)
                        .clickable(enabled = state != VoiceState.Listening) {
                            when (state) {
                                VoiceState.Speaking -> vm.stopSpeaking()
                                VoiceState.Thinking -> vm.abort()
                                VoiceState.Idle     -> {
                                    if (!hasAudioPerm) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    else vm.startListening()
                                }
                                else -> {}
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (state) {
                            VoiceState.Speaking -> Icons.Default.Stop
                            VoiceState.Thinking -> Icons.Default.Close
                            else                -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint     = if (state == VoiceState.Idle) NxFg2 else NxBg,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (transcript.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(NxDim, RoundedCornerShape(12.dp))
                            .border(1.dp, NxBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text("YOU", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                             letterSpacing = 0.15.sp, color = NxOrangeDim,
                             modifier = Modifier.padding(bottom = 6.dp))
                        Text(transcript, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                             color = NxFg, lineHeight = 18.sp)
                    }
                }
                if (response.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(NxBg3, RoundedCornerShape(12.dp))
                            .border(1.dp, NxBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text("NEXIS", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                             letterSpacing = 0.15.sp, color = NxOrange,
                             modifier = Modifier.padding(bottom = 6.dp))
                        Text(response, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                             color = NxFg, lineHeight = 18.sp)
                    }
                }
                if (error != null) {
                    Text(error!!, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxRed,
                         textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
