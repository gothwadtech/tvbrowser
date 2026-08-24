package com.gothwad.tvbrowser.activity.lock

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.singleton.AppLockManager

class AppLockActivity : Activity() {

    private val inputPin = StringBuilder()
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private lateinit var dot4: View
    private var dotsContainer: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)

        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
        dot4 = findViewById(R.id.dot4)
        dotsContainer = findViewById(R.id.llDotsContainer)

        setupKeypad()
    }

    private fun setupKeypad() {
        val keys = listOf(
            R.id.btnKey0 to "0",
            R.id.btnKey1 to "1",
            R.id.btnKey2 to "2",
            R.id.btnKey3 to "3",
            R.id.btnKey4 to "4",
            R.id.btnKey5 to "5",
            R.id.btnKey6 to "6",
            R.id.btnKey7 to "7",
            R.id.btnKey8 to "8",
            R.id.btnKey9 to "9"
        )

        for ((btnId, digit) in keys) {
            findViewById<Button>(btnId)?.setOnClickListener {
                appendDigit(digit)
            }
        }

        findViewById<Button>(R.id.btnKeyClear)?.setOnClickListener {
            inputPin.clear()
            updateDots()
        }

        findViewById<Button>(R.id.btnKeyBackspace)?.setOnClickListener {
            if (inputPin.isNotEmpty()) {
                inputPin.deleteCharAt(inputPin.length - 1)
                updateDots()
            }
        }

        findViewById<Button>(R.id.btnKey1)?.requestFocus()
    }

    private fun appendDigit(digit: String) {
        if (inputPin.length < 4) {
            inputPin.append(digit)
            updateDots()

            if (inputPin.length == 4) {
                checkPin()
            }
        }
    }

    private fun updateDots() {
        dot1.setBackgroundResource(if (inputPin.length >= 1) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
        dot2.setBackgroundResource(if (inputPin.length >= 2) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
        dot3.setBackgroundResource(if (inputPin.length >= 3) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
        dot4.setBackgroundResource(if (inputPin.length >= 4) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
    }

    private fun checkPin() {
        val entered = inputPin.toString()
        if (AppLockManager.verifyPin(this, entered)) {
            AppLockManager.setSessionUnlocked(true)
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            dotsContainer?.animate()
                ?.translationXBy(20f)
                ?.setDuration(60)
                ?.withEndAction {
                    dotsContainer?.animate()
                        ?.translationXBy(-40f)
                        ?.setDuration(60)
                        ?.withEndAction {
                            dotsContainer?.animate()
                                ?.translationX(0f)
                                ?.setDuration(60)
                                ?.start()
                        }?.start()
                }?.start()
            inputPin.clear()
            updateDots()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit("0"); return true }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit("1"); return true }
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit("2"); return true }
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit("3"); return true }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit("4"); return true }
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit("5"); return true }
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit("6"); return true }
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit("7"); return true }
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit("8"); return true }
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit("9"); return true }
            KeyEvent.KEYCODE_DEL -> {
                if (inputPin.isNotEmpty()) {
                    inputPin.deleteCharAt(inputPin.length - 1)
                    updateDots()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION", "GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back press from bypassing lock screen
        moveTaskToBack(true)
    }
}
