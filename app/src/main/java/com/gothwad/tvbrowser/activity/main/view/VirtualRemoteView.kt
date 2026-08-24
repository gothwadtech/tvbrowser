package com.gothwad.tvbrowser.activity.main.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.handleDpadKey
import com.gothwad.tvbrowser.databinding.ViewVirtualRemoteBinding
import com.gothwad.tvbrowser.utils.Utils

class VirtualRemoteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val vb = ViewVirtualRemoteBinding.inflate(LayoutInflater.from(context), this, true)

    private var dX = 0f
    private var dY = 0f

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        setupClickListeners()
        setupMoveDragListener()
    }

    private fun setupClickListeners() {
        vb.btnDpadUp.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.handleDpadKey(KeyEvent.KEYCODE_DPAD_UP)
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
            }
        }

        vb.btnDpadDown.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.handleDpadKey(KeyEvent.KEYCODE_DPAD_DOWN)
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
            }
        }

        vb.btnDpadLeft.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.handleDpadKey(KeyEvent.KEYCODE_DPAD_LEFT)
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
            }
        }

        vb.btnDpadRight.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.handleDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        }

        vb.btnDpadCenter.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.handleDpadKey(KeyEvent.KEYCODE_DPAD_CENTER)
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            }
        }

        vb.btnDpadBack.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.handleBack()
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_BACK)
            }
        }

        vb.btnDpadHome.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.showHome()
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_HOME)
            }
        }

        vb.btnDpadMenu.setOnClickListener {
            val activity = context as? MainActivity
            if (activity != null) {
                activity.handleMenu()
            } else {
                sendKeyEvent(KeyEvent.KEYCODE_MENU)
            }
        }
    }

    private fun setupMoveDragListener() {
        vb.btnMoveMode.setOnTouchListener { _, event ->
            val parentView = parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = x - event.rawX
                    dY = y - event.rawY
                    setMoveButtonActive(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f, (parentView.width - width).toFloat().coerceAtLeast(0f))
                    val newY = (event.rawY + dY).coerceIn(0f, (parentView.height - height).toFloat().coerceAtLeast(0f))
                    x = newX
                    y = newY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setMoveButtonActive(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun setMoveButtonActive(active: Boolean) {
        if (active) {
            val solidBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = Utils.D2P(context, 8f)
                setColor(Color.parseColor("#0494f4"))
                setStroke(Utils.D2P(context, 1.5f).toInt(), Color.WHITE)
            }
            vb.btnMoveMode.background = solidBg
        } else {
            vb.btnMoveMode.setBackgroundResource(R.drawable.bg_virtual_remote_btn)
        }
    }

    private fun sendKeyEvent(keyCode: Int) {
        val activity = context as? MainActivity
        if (activity != null) {
            if (activity.currentFocus == null) {
                activity.focusDefaultNavigationElement()
            }
        }

        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = KeyEvent(
            downTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
        )
        val upEvent = KeyEvent(
            downTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
        )

        if (activity != null) {
            activity.dispatchKeyEvent(downEvent)
            activity.dispatchKeyEvent(upEvent)
        } else {
            dispatchKeyEvent(downEvent)
            dispatchKeyEvent(upEvent)
        }
    }
}
