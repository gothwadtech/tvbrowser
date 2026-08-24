package com.gothwad.tvbrowser.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import com.gothwad.tvbrowser.AppContext

/**
 * Custom EditText for Android TV that properly respects the disable on-screen virtual keyboard setting.
 * When disableVirtualKeyboard is enabled (e.g. physical keyboard attached):
 * - showSoftInputOnFocus is disabled
 * - onCreateInputConnection avoids extracting UI and suppresses soft keyboard requests
 * - hideSoftInputFromWindow is immediately invoked
 */
class TvSearchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val isVirtualKeyboardDisabled: Boolean
        get() = try {
            AppContext.provideConfig().disableVirtualKeyboard
        } catch (e: Exception) {
            false
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyVirtualKeyboardPolicy()
    }

    fun applyVirtualKeyboardPolicy() {
        if (isVirtualKeyboardDisabled) {
            showSoftInputOnFocus = false
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
        } else {
            showSoftInputOnFocus = true
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        applyVirtualKeyboardPolicy()
        if (isVirtualKeyboardDisabled) {
            outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        }
        val connection = super.onCreateInputConnection(outAttrs)
        if (isVirtualKeyboardDisabled) {
            post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(windowToken, 0)
            }
        }
        return connection
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }
}
