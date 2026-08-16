package com.dtraas.homestock.ui.recipes

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.theme.SoftCardShape
import kotlinx.coroutines.delay

/**
 * Full-screen, one-step-at-a-time walkthrough of a recipe — reached from RecipeDetailScreen's
 * "Start koken" button. Keeps the screen awake for the whole time it's open (see the
 * [DisposableEffect] below): a phone that locks itself mid-recipe, with flour on your hands,
 * is the exact annoyance this exists to avoid. Each step gets its own one-tap timer chip when
 * [detectDurationSeconds] finds a duration mentioned in it (see [StepTimerChip]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(
    mealId: String,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    // Same as RecipeDetailScreen's own languageTag — resolved independently here rather than
    // threaded through a nav arg, since Compose Navigation only carries primitives cleanly and
    // this is already cheap to read directly.
    val languageTag = LocalConfiguration.current.locales[0].language
    val viewModel: CookModeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CookModeViewModel(mealId, languageTag, application.container.recipeRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    // The whole point of Kookmodus — a locked screen mid-recipe defeats the purpose. Set on
    // entry, cleared on leaving (back press, navigating away, or the process reclaiming this
    // composable) rather than left on, which would keep the screen awake even back on
    // RecipeDetailScreen or elsewhere in the app.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val steps = uiState.steps
    val currentIndex = uiState.currentStepIndex
    val currentStep = steps.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            HomeStockTopAppBar(
                title = {
                    Text(
                        if (steps.isNotEmpty()) {
                            stringResource(R.string.cook_mode_step_counter_format, currentIndex + 1, steps.size)
                        } else {
                            stringResource(R.string.cook_mode_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            uiState.hasError || currentStep == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (uiState.hasError) R.string.recipes_detail_error else R.string.cook_mode_no_instructions,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / steps.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = currentStep,
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                    )
                    val durationSeconds = remember(currentStep) { detectDurationSeconds(currentStep) }
                    if (durationSeconds != null) {
                        StepTimerChip(totalSeconds = durationSeconds, modifier = Modifier.padding(top = 24.dp))
                    }
                }
                Surface(shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::previousStep,
                            enabled = currentIndex > 0,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.cook_mode_previous))
                        }
                        if (currentIndex < steps.lastIndex) {
                            Button(onClick = viewModel::nextStep, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cook_mode_next))
                                Icon(
                                    Icons.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 8.dp).size(18.dp),
                                )
                            }
                        } else {
                            Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.cook_mode_done), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One-tap countdown for a duration [detectDurationSeconds] found in the current step — tap to
 * start, tap again to restart. A short vibration (see [vibrateOnce]) marks completion instead
 * of a system notification: this only needs to matter while Kookmodus is open and the phone is
 * right there on the counter, not as a background alarm to survive the app being closed.
 */
@Composable
private fun StepTimerChip(totalSeconds: Long, modifier: Modifier = Modifier) {
    var remainingSeconds by remember(totalSeconds) { mutableStateOf(totalSeconds) }
    var isRunning by remember(totalSeconds) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(isRunning, totalSeconds) {
        if (isRunning) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
            }
            isRunning = false
            vibrateOnce(context)
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeLabel = "%d:%02d".format(minutes, seconds)
    val label = when {
        isRunning -> timeLabel
        remainingSeconds == 0L -> stringResource(R.string.cook_mode_timer_done)
        else -> stringResource(R.string.cook_mode_timer_start_format, timeLabel)
    }

    AssistChip(
        onClick = {
            if (!isRunning) {
                remainingSeconds = totalSeconds
                isRunning = true
            }
        },
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = if (isRunning) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
        shape = RoundedCornerShape(50),
        modifier = modifier,
    )
}

private fun vibrateOnce(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
}
