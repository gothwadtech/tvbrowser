package com.gothwad.tvbrowser.utils

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * HardwareInputManager & Airtel TV Box Mouse Click Compatibility Engine.
 *
 * Specifically fixes:
 * 1. Airtel Xstream and Android TV Set-Top Boxes missing/dropping click events on web pages
 *    (handling ACTION_HOVER vs ACTION_BUTTON_PRESS vs synthesized touch injection).
 * 2. Real-time detection & monitoring of connected physical/USB/Bluetooth Keyboards & Mice.
 * 3. Synthetic click pipeline allowing precision single & double clicks on modern dynamic web apps
 *    (SPA, React, PW.live, Google sign-in buttons).
 */
class HardwareInputManager private constructor(private val context: Context) : InputManager.InputDeviceListener {

    companion object {
        @Volatile
        private var instance: HardwareInputManager? = null

        fun getInstance(context: Context): HardwareInputManager {
            return instance ?: synchronized(this) {
                instance ?: HardwareInputManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class DeviceInfo(
        val id: Int,
        val name: String,
        val isMouse: Boolean,
        val isKeyboard: Boolean,
        val isGamepad: Boolean,
        val isVirtual: Boolean
    )

    interface InputDevicesListener {
        fun onInputDevicesChanged(devices: List<DeviceInfo>)
    }

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val listeners = mutableListOf<InputDevicesListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastHardwareMouseActionDownTime: Long = 0L

    init {
        try {
            inputManager.registerInputDeviceListener(this, mainHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addListener(listener: InputDevicesListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onInputDevicesChanged(getConnectedDevices())
        }
    }

    fun removeListener(listener: InputDevicesListener) {
        listeners.remove(listener)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        notifyChanged()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        notifyChanged()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        notifyChanged()
    }

    private fun notifyChanged() {
        val currentDevices = getConnectedDevices()
        for (l in listeners) {
            l.onInputDevicesChanged(currentDevices)
        }
    }

    fun getConnectedDevices(): List<DeviceInfo> {
        val list = mutableListOf<DeviceInfo>()
        val deviceIds = inputManager.inputDeviceIds
        for (id in deviceIds) {
            val device = inputManager.getInputDevice(id) ?: continue
            val sources = device.sources
            val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                    (sources and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE ||
                    (sources and InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL ||
                    (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD

            val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                    device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

            val isVirtual = device.isVirtual

            // Filter out purely virtual system input devices if needed or mark them
            list.add(
                DeviceInfo(
                    id = device.id,
                    name = device.name,
                    isMouse = isMouse,
                    isKeyboard = isKeyboard,
                    isGamepad = isGamepad,
                    isVirtual = isVirtual
                )
            )
        }
        return list
    }

    fun isPhysicalMouseConnected(): Boolean {
        return getConnectedDevices().any { it.isMouse && !it.isVirtual }
    }

    fun isPhysicalKeyboardConnected(): Boolean {
        return getConnectedDevices().any { it.isKeyboard && !it.isVirtual }
    }

    /**
     * Inspects MotionEvent for physical mouse click anomalies on Android TV / STB.
     * Airtel Xstream and select TV boxes dispatch ACTION_BUTTON_PRESS or generic motion events
     * without a corresponding proper ACTION_DOWN -> ACTION_UP stream to the WebView DOM.
     *
     * @return true if handled / synthesized touch sequence was dispatched to target view.
     */
    fun processHardwareMouseEvent(event: MotionEvent, targetView: View): Boolean {
        val isMouseSource = (event.source and InputDevice.SOURCE_MOUSE) != 0 ||
                (event.source and InputDevice.SOURCE_TOUCHPAD) != 0 ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

        if (!isMouseSource) return false

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                    dispatchSyntheticTouchEvent(targetView, x, y, MotionEvent.ACTION_DOWN)
                    lastHardwareMouseActionDownTime = android.os.SystemClock.uptimeMillis()
                    return true
                }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                    dispatchSyntheticTouchEvent(targetView, x, y, MotionEvent.ACTION_UP)
                    return true
                }
            }
            MotionEvent.ACTION_DOWN -> {
                // Ensure proper touch sequence starts
                lastHardwareMouseActionDownTime = android.os.SystemClock.uptimeMillis()
            }
        }
        return false
    }

    /**
     * Synthesizes standard touch events for Android WebView so that Chromium pointer / touch / click
     * DOM listeners execute seamlessly even on buggy Android TV mouse drivers.
     */
    fun dispatchSyntheticTouchEvent(targetView: View, x: Float, y: Float, action: Int) {
        val downTime = if (action == MotionEvent.ACTION_DOWN) android.os.SystemClock.uptimeMillis() else lastHardwareMouseActionDownTime
        val eventTime = android.os.SystemClock.uptimeMillis()

        val properties = arrayOfNulls<MotionEvent.PointerProperties>(1)
        val pp = MotionEvent.PointerProperties()
        pp.id = 0
        pp.toolType = MotionEvent.TOOL_TYPE_FINGER
        properties[0] = pp

        val coords = arrayOfNulls<MotionEvent.PointerCoords>(1)
        val pc = MotionEvent.PointerCoords()
        pc.x = x
        pc.y = y
        pc.pressure = 1.0f
        pc.size = 1.0f
        coords[0] = pc

        val motionEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        try {
            targetView.dispatchTouchEvent(motionEvent)
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            motionEvent.recycle()
        }
    }
}
