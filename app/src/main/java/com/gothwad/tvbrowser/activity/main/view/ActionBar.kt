package com.gothwad.tvbrowser.activity.main.view

import android.content.Context
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View.OnFocusChangeListener
import android.view.View.OnKeyListener
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import com.gothwad.tvbrowser.AppContext
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.downloads.ActiveDownloadsModel
import com.gothwad.tvbrowser.databinding.ViewActionbarBinding
import com.gothwad.tvbrowser.utils.Utils
import com.gothwad.tvbrowser.utils.activemodel.ActiveModelsRepository

class ActionBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val vb = ViewActionbarBinding.inflate(LayoutInflater.from(context), this)
    var callback: Callback? = null
    private var downloadAnimation: Animation? = null
    private var downloadsModel = ActiveModelsRepository.get(ActiveDownloadsModel::class, context)
    private var extendedAddressBarMode = false

    interface Callback {
        fun closeWindow()
        fun showDownloads()
        fun showFavorites()
        fun showHistory()
        fun showSettings()
        fun initiateVoiceSearch()
        fun search(text: String)
        fun onExtendedAddressBarMode()
        fun onUrlInputDone()
        fun toggleIncognitoMode()
        fun toggleHeader()
    }

    private val etUrlFocusChangeListener = OnFocusChangeListener { _, focused ->
        if (focused) {
            enterExtendedAddressBarMode()

            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            if (!AppContext.provideConfig().disableVirtualKeyboard) {
                imm.showSoftInput(vb.etUrl, InputMethodManager.SHOW_IMPLICIT)
            } else {
                vb.etUrl.showSoftInputOnFocus = false
                imm.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
            }
            postDelayed(//workaround an android TV bug
                {
                    vb.etUrl.selectAll()
                    if (AppContext.provideConfig().disableVirtualKeyboard) {
                        vb.etUrl.showSoftInputOnFocus = false
                        val imm2 = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm2?.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
                    }
                }, 500)
        } else {
            dismissExtendedAddressBarMode()
        }
    }

    private val etUrlKeyListener = OnKeyListener { view, i, keyEvent ->
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                if (keyEvent.action == KeyEvent.ACTION_UP) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
                    callback?.search(vb.etUrl.text.toString())
                    dismissExtendedAddressBarMode()
                    callback?.onUrlInputDone()
                }
                return@OnKeyListener true
            }
        }
        false
    }

    init {
        init()
    }

    fun init() {
        orientation = HORIZONTAL

        if (isInEditMode) return

        if (Utils.isFireTV(context)) {
            (vb.ibVoiceSearch.parent as? ViewGroup)?.removeView(vb.ibVoiceSearch)
        } else {
            vb.ibVoiceSearch.setOnClickListener { callback?.initiateVoiceSearch() }
        }

        vb.etUrl.onFocusChangeListener = etUrlFocusChangeListener
        vb.etUrl.setOnKeyListener(etUrlKeyListener)

        updateAddressBarIcon(vb.etUrl.text.toString())

        AppContext.provideConfig().searchEngineURL.subscribe({ _ ->
            post { updateAddressBarIcon(vb.etUrl.text.toString()) }
        })
    }

    fun setAddressBoxText(text: String) {
        if (text == Config.HOME_PAGE_URL || text == Config.HOME_URL_ALIAS) {
            vb.etUrl.setText("")
            updateAddressBarIcon("")
        } else {
            vb.etUrl.setText(text)
            updateAddressBarIcon(text)
        }
    }

    fun setHeaderToggleIcon(isExpanded: Boolean) {
        // No-op (header toggle icon removed from inside actionbar)
    }

    fun updateAddressBarIcon(url: String?) {
        vb.ivLockIcon.setImageResource(R.drawable.ic_lock_security)
    }

    fun setAddressBoxTextColor(color: Int) {
        vb.etUrl.setTextColor(color)
    }

    private fun enterExtendedAddressBarMode() {
        if (extendedAddressBarMode) return
        extendedAddressBarMode = true
        TransitionManager.beginDelayedTransition(this)
        callback?.onExtendedAddressBarMode()
    }

    fun dismissExtendedAddressBarMode() {
        if (!extendedAddressBarMode) return
        extendedAddressBarMode = false
        TransitionManager.beginDelayedTransition(this)
    }

    fun catchFocus() {
        vb.etUrl.requestFocus()
    }
}
