package info.cemu.cemu.games.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import info.cemu.cemu.common.ui.components.Button
import info.cemu.cemu.common.ui.components.Header
import info.cemu.cemu.common.ui.components.ScreenContent
import info.cemu.cemu.common.ui.components.SingleSelection
import info.cemu.cemu.common.ui.components.Toggle
import info.cemu.cemu.common.ui.localization.tr
import info.cemu.cemu.nativeinterface.NativeEmulation
import info.cemu.cemu.nativeinterface.NativeGameTitles
import info.cemu.cemu.nativeinterface.NativeGameTitles.DriverSettingMode
import info.cemu.cemu.nativeinterface.NativeGameTitles.PrecompiledShaderOption

@Composable
fun GameProfileEditScreen(
    game: NativeGameTitles.Game,
    navigateBack: () -> Unit,
    viewModel: GameProfileEditViewModel = viewModel(
        factory = GameProfileEditViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(GameProfileEditViewModel.GAME_KEY, game)
        },
    ),
) {
    val supportsLoadingCustomDrivers = remember { NativeEmulation.supportsLoadingCustomDriver() }

    val driverSettingChoices by viewModel.driverSettingChoices.collectAsState()
    val selectedDriverSetting by viewModel.selectedDriverSetting.collectAsState()

    val titleId = game.titleId

    // Bumped after "Reset to default" so every Toggle/SingleSelection below
    // re-reads its initialState instead of keeping its already-remembered
    // (now stale) value.
    var resetGeneration by remember { mutableIntStateOf(0) }

    ScreenContent(
        appBarText = tr("Edit game profile"),
        navigateBack = navigateBack,
        contentModifier = Modifier.padding(16.dp),
        contentVerticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(text = game.name)
        Button(
            label = tr("Reset to default"),
            description = tr("Remove all per-game overrides for this title"),
            onClick = {
                NativeGameTitles.resetGameProfileForTitle(titleId)
                resetGeneration++
            },
        )
        key(resetGeneration) {
        Toggle(
            label = tr("Load shared libraries"),
            description = tr("Load libraries from the cafeLibs directory"),
            initialCheckedState = {
                NativeGameTitles.isLoadingSharedLibrariesForTitleEnabled(
                    titleId
                )
            },
            onCheckedChanged = { enabled ->
                NativeGameTitles.setLoadingSharedLibrariesForTitleEnabled(
                    titleId, enabled
                )
            },
        )
        Toggle(
            label = tr("Shader multiplication accuracy"),
            description = tr("Controls the accuracy of floating point multiplication in shaders"),
            initialCheckedState = {
                NativeGameTitles.isShaderMultiplicationAccuracyForTitleEnabled(
                    titleId
                )
            },
            onCheckedChanged = { enabled ->
                NativeGameTitles.setShaderMultiplicationAccuracyForTitleEnabled(
                    titleId, enabled
                )
            },
        )
        SingleSelection(
            label = tr("CPU mode"),
            initialChoice = { NativeGameTitles.getCpuModeForTitle(titleId) },
            choices = listOf(
                NativeGameTitles.CPUMode.SINGLECOREINTERPRETER,
                NativeGameTitles.CPUMode.SINGLECORERECOMPILER,
                NativeGameTitles.CPUMode.MULTICORERECOMPILER,
                NativeGameTitles.CPUMode.AUTO
            ),
            choiceToString = { cpuMode -> cpuModeToString(cpuMode) },
            onChoiceChanged = { cpuMode -> NativeGameTitles.setCpuModeForTitle(titleId, cpuMode) })
        SingleSelection(
            label = tr("Thread quantum"),
            initialChoice = { NativeGameTitles.getThreadQuantumForTitle(titleId) },
            choices = NativeGameTitles.THREAD_QUANTUM_VALUES,
            choiceToString = { it.toString() },
            onChoiceChanged = { threadQuantum ->
                NativeGameTitles.setThreadQuantumForTitle(titleId, threadQuantum)
            })
        Toggle(
            label = tr("Disable audio"),
            initialCheckedState = { NativeGameTitles.isAudioDisabledForTitle(titleId) },
            onCheckedChanged = { disabled ->
                NativeGameTitles.setAudioDisabledForTitle(titleId, disabled)
            },
        )
        Toggle(
            label = tr("Start with GamePad view"),
            initialCheckedState = { NativeGameTitles.isStartWithGamepadViewForTitleEnabled(titleId) },
            onCheckedChanged = { enabled ->
                NativeGameTitles.setStartWithGamepadViewForTitleEnabled(titleId, enabled)
            },
        )
        SingleSelection(
            label = tr("Precompiled shaders"),
            initialChoice = { NativeGameTitles.getPrecompiledShadersForTitle(titleId) },
            choices = listOf(
                PrecompiledShaderOption.AUTO,
                PrecompiledShaderOption.ENABLE,
                PrecompiledShaderOption.DISABLE,
            ),
            choiceToString = { precompiledShadersToString(it) },
            onChoiceChanged = { precompiledShaders ->
                NativeGameTitles.setPrecompiledShadersForTitle(titleId, precompiledShaders)
            },
        )
        }
        if (supportsLoadingCustomDrivers) {
            SingleSelection(
                label = tr("Custom driver"),
                choice = selectedDriverSetting,
                choices = driverSettingChoices,
                choiceToString = ::customDriverSettingChoiceToString,
                onChoiceChanged = viewModel::setSelectedDriverSetting,
            )
        }
    }
}

private fun customDriverSettingChoiceToString(driverSettingChoice: DriverSettingChoice): String =
    when (driverSettingChoice.mode) {
        DriverSettingMode.GLOBAL -> tr("Global")
        DriverSettingMode.SYSTEM -> tr("System driver")
        else -> driverSettingChoice.customDriverData!!.name
    }

private fun cpuModeToString(cpuMode: Int): String = when (cpuMode) {
    NativeGameTitles.CPUMode.SINGLECOREINTERPRETER -> tr("Single-core interpreter")
    NativeGameTitles.CPUMode.SINGLECORERECOMPILER -> tr("Single-core recompiler")
    NativeGameTitles.CPUMode.MULTICORERECOMPILER -> tr("Multi-core recompiler")
    else -> tr("Auto (recommended)")
}

private fun precompiledShadersToString(precompiledShaders: Int): String = when (precompiledShaders) {
    PrecompiledShaderOption.ENABLE -> tr("Enabled")
    PrecompiledShaderOption.DISABLE -> tr("Disabled")
    else -> tr("Auto (recommended)")
}
