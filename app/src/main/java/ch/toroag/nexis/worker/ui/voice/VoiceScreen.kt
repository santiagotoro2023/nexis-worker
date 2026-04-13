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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.*
import ch.toroag.nexis.worker.util.SpeechRecognizerHelper

@OptIn(ExperimentalMaterial3Api::class)
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

    // When state enters Listening, start STT
    LaunchedEffect(state) {
        if (state == VoiceState.Listening) {
            helper.startListening(
                onResult = { text ->
                    if (text.isBlank()) vm.onListeningCancelled()
                    else vm.onTranscript(text)
                },
                onError = { vm.onListeningCancelled() },
            )
        }
    }

    // Pulse animation while listening
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("voice", style = MaterialTheme.typography.titleMedium, color = NxFg) },
                navigationIcon = {
                    IconButton(onClick = { vm.stopSpeaking(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NxFg2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Status label
            Text(
                when (state) {
                    VoiceState.Idle      -> "tap to speak"
                    VoiceState.Listening -> "listening..."
                    VoiceState.Thinking  -> "thinking..."
                    VoiceState.Speaking  -> "speaking — tap to stop"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = NxFg2,
            )

            // Big mic / stop button with pulse halo when listening
            Box(contentAlignment = Alignment.Center) {
                if (state == VoiceState.Listening) {
                    Box(
                        Modifier
                            .size(128.dp)
                            .scale(pulseScale)
                            .background(NxOrange.copy(alpha = 0.15f), CircleShape)
                    )
                }
                Box(
                    Modifier
                        .size(96.dp)
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
                        .clickable(
                            enabled = state == VoiceState.Idle || state == VoiceState.Speaking
                        ) {
                            when (state) {
                                VoiceState.Speaking -> vm.stopSpeaking()
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
                        imageVector        = if (state == VoiceState.Speaking) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "mic",
                        tint               = if (state == VoiceState.Idle) NxFg2 else MaterialTheme.colorScheme.background,
                        modifier           = Modifier.size(36.dp),
                    )
                }
            }

            // Conversation display
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (transcript.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(NxDim, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Text("you", style = MaterialTheme.typography.labelSmall, color = NxOrangeDim)
                        Spacer(Modifier.height(4.dp))
                        Text(transcript, style = MaterialTheme.typography.bodySmall, color = NxFg)
                    }
                }
                if (response.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(NxBg3, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Text("nexis", style = MaterialTheme.typography.labelSmall, color = NxOrange)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            response,
                            style      = MaterialTheme.typography.bodySmall,
                            color      = NxFg,
                            lineHeight = 18.sp,
                        )
                    }
                }
                if (error != null) {
                    Text(
                        error!!,
                        color     = MaterialTheme.colorScheme.error,
                        style     = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
