package info.cemu.cemu.emulation.input

import android.content.Context
import android.hardware.input.InputManager
import info.cemu.cemu.common.android.inputdevice.listGameControllers
import info.cemu.cemu.common.android.inputdevice.toControllerInfo
import info.cemu.cemu.common.input.InputDeviceListener
import info.cemu.cemu.nativeinterface.NativeInput
import info.cemu.cemu.settings.input.controller.InputMapper

class NativeInputDeviceListener(private val context: Context) {
    private val inputManager
        get() = context.getSystemService(Context.INPUT_SERVICE) as InputManager?

    private fun refreshControllers() {
        val gameControllers = listGameControllers()

        NativeInput.setControllers(gameControllers.map { it.toControllerInfo() }.toTypedArray())
        autoConfigurePlayerOneIfUnset(gameControllers.firstOrNull()?.id)
    }

    /**
     * First-run convenience: if controller slot 0 has no button mappings at
     * all yet, wire the first detected physical controller into it
     * automatically as a Wii U GamePad and map every button it has, so a
     * gamepad "just works" the moment a game starts instead of requiring a
     * trip through Input settings first. Checking mappings rather than the
     * controller type matters because slot 0 defaults to VPAD out of the
     * box (mirroring real hardware) even with nothing mapped - checking the
     * type here would see "already VPAD" and skip auto-mapping entirely.
     * Never touches a slot that already has at least one mapping, so this
     * can't clobber a custom setup.
     */
    private fun autoConfigurePlayerOneIfUnset(deviceId: Int?) {
        if (deviceId == null) return
        if (NativeInput.getControllerMappings(0).isNotEmpty()) return
        if (NativeInput.getControllerType(0) == NativeInput.EmulatedControllerType.DISABLED) {
            NativeInput.setControllerType(0, NativeInput.EmulatedControllerType.VPAD)
        }
        InputMapper.mapAllInputs(deviceId, 0)
    }

    private val listener = object : InputDeviceListener {
        override fun onInputDeviceChanged() = refreshControllers()
    }

    fun register() {
        inputManager?.registerInputDeviceListener(listener, null)
        refreshControllers()
    }

    fun unregister() {
        inputManager?.unregisterInputDeviceListener(listener)
    }
}