package info.cemu.cemu.settings.debug

import androidx.compose.runtime.Composable
import info.cemu.cemu.common.ui.components.ScreenContent
import info.cemu.cemu.common.ui.components.Toggle
import info.cemu.cemu.common.ui.localization.tr
import info.cemu.cemu.nativeinterface.NativeSettings

/**
 * Debug-build-only toggles - anything added here should be gated on
 * [info.cemu.cemu.BuildConfig.DEBUG] at the call site (see SettingsHomeScreen),
 * so this whole section (and the code paths it flips) compiles out of release.
 */
@Composable
fun DebugSettingsScreen(
    navigateBack: () -> Unit,
) {
    ScreenContent(
        appBarText = tr("Debug settings"),
        navigateBack = navigateBack,
    ) {
        Toggle(
            label = tr("Create crash dump"),
            description = tr("On a crash, let the OS generate a full native crash dump instead of just writing a backtrace to log.txt"),
            initialCheckedState = NativeSettings::isCrashDumpEnabled,
            onCheckedChanged = NativeSettings::setCrashDumpEnabled,
        )
    }
}
