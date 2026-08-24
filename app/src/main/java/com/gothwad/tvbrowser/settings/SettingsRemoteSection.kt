package com.gothwad.tvbrowser.settings

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.Toast
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.databinding.ViewSettingsMainBinding
import com.gothwad.tvbrowser.utils.HardwareInputManager

object SettingsRemoteSection {

    fun initVirtualCursorPhysicsSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config
    ) {
        vb.scEnableVirtualCursor.isChecked = config.enableVirtualCursor
        vb.llVirtualCursorDetails.visibility = if (config.enableVirtualCursor) View.VISIBLE else View.GONE
        vb.scEnableVirtualCursor.setOnCheckedChangeListener { _, isChecked ->
            config.enableVirtualCursor = isChecked
            vb.llVirtualCursorDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        vb.spCursorStyle.setSelection(config.cursorStyle.coerceIn(0, 4))
        vb.spCursorStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.cursorStyle = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val minSize = Config.CURSOR_SIZE_PERCENT_MIN
        val maxSize = Config.CURSOR_SIZE_PERCENT_MAX
        vb.sbCursorSize.max = maxSize - minSize
        vb.sbCursorSize.progress = config.cursorSizePercent - minSize
        vb.tvCursorSizeValue.text = context.getString(R.string.cursor_physics_percent, config.cursorSizePercent)
        vb.sbCursorSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorSizePercent = minSize + progress
                vb.tvCursorSizeValue.text = context.getString(R.string.cursor_physics_percent, config.cursorSizePercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val minP = Config.CURSOR_PHYSICS_PERCENT_MIN
        val maxP = Config.CURSOR_PHYSICS_PERCENT_MAX
        val range = maxP - minP
        vb.sbCursorMaxSpeed.max = range
        vb.sbCursorAcceleration.max = range

        fun refreshValueLabels() {
            vb.tvCursorMaxSpeedValue.text = context.getString(R.string.cursor_physics_percent, config.cursorMaxSpeedPercent)
            vb.tvCursorAccelerationValue.text = context.getString(R.string.cursor_physics_percent, config.cursorAccelerationPercent)
        }

        vb.sbCursorMaxSpeed.progress = config.cursorMaxSpeedPercent - minP
        vb.sbCursorAcceleration.progress = config.cursorAccelerationPercent - minP
        refreshValueLabels()

        vb.sbCursorMaxSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorMaxSpeedPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        vb.sbCursorAcceleration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorAccelerationPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun initKeyboardMouseSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        activity: Context?
    ) {
        val mainAct = activity as? MainActivity
        vb.scDisableVirtualKeyboardKm.isChecked = config.disableVirtualKeyboard
        vb.scDisableVirtualKeyboardKm.setOnCheckedChangeListener { _, isChecked ->
            config.disableVirtualKeyboard = isChecked
            vb.scDisableVirtualKeyboard.isChecked = isChecked
            mainAct?.applySoftInputMode()
        }

        vb.llDisableVirtualKeyboardKm.setOnClickListener {
            vb.scDisableVirtualKeyboardKm.toggle()
        }

        vb.llMouseCompatibility.setOnClickListener {
            vb.scMouseCompatibility.toggle()
        }

        val hwInput = HardwareInputManager.getInstance(context)
        val updateDeviceList: (List<HardwareInputManager.DeviceInfo>) -> Unit = { devices ->
            val nonVirtual = devices.filter { !it.isVirtual }
            if (nonVirtual.isEmpty()) {
                vb.tvConnectedHardwareStatus.text = "⚠️ No external physical keyboard or mouse currently detected.\n(Using standard TV Remote / D-Pad navigation)"
            } else {
                val sb = StringBuilder()
                for (dev in nonVirtual) {
                    val typeLabel = when {
                        dev.isMouse && dev.isKeyboard -> "⌨️ 🖱️ Combo Input"
                        dev.isMouse -> "🖱️ Mouse / Touchpad"
                        dev.isKeyboard -> "⌨️ Physical Keyboard"
                        dev.isGamepad -> "🎮 Gamepad / Controller"
                        else -> "🔌 Input Device"
                    }
                    sb.append("• ").append(typeLabel).append(": ").append(dev.name).append("\n")
                }
                sb.append("✅ Precision STB Click Optimization: Active")
                vb.tvConnectedHardwareStatus.text = sb.toString().trim()
            }
        }

        hwInput.addListener(object : HardwareInputManager.InputDevicesListener {
            override fun onInputDevicesChanged(devices: List<HardwareInputManager.DeviceInfo>) {
                vb.root.post { updateDeviceList(devices) }
            }
        })

        vb.btnRefreshDevices.setOnClickListener {
            updateDeviceList(hwInput.getConnectedDevices())
            Toast.makeText(context, "Device list refreshed", Toast.LENGTH_SHORT).show()
        }

        updateDeviceList(hwInput.getConnectedDevices())
    }
}
