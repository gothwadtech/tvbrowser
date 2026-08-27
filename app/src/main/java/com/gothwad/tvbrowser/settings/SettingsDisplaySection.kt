package com.gothwad.tvbrowser.settings

import android.content.Context
import android.widget.SeekBar
import android.widget.Toast
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.applyWebPageZoom
import com.gothwad.tvbrowser.databinding.ViewSettingsMainBinding

object SettingsDisplaySection {

    fun initDisplayAndZoomSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        onDismissDialog: (() -> Unit)?,
        activity: Context?
    ) {
        val mainAct = activity as? MainActivity

        // Top Tab Bar toggle
        vb.scShowTopTabBar.isChecked = config.showTopTabBar.value
        vb.llShowTopTabBar.setOnClickListener {
            val newState = !vb.scShowTopTabBar.isChecked
            vb.scShowTopTabBar.isChecked = newState
            config.showTopTabBar.value = newState
        }

        // UI Scaling controls
        val minUiScale = Config.UI_SCALE_PERCENT_MIN
        val maxUiScale = Config.UI_SCALE_PERCENT_MAX
        vb.sbUiScale.max = maxUiScale - minUiScale
        vb.sbUiScale.progress = config.uiScalePercent - minUiScale
        vb.tvUiScaleValue.text = "${config.uiScalePercent}%"

        vb.sbUiScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = minUiScale + progress
                config.uiScalePercent = value
                vb.tvUiScaleValue.text = "$value%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun setUiScale(percent: Int) {
            config.uiScalePercent = percent
            vb.sbUiScale.progress = (percent - minUiScale).coerceIn(0, maxUiScale - minUiScale)
            vb.tvUiScaleValue.text = "$percent%"
        }

        vb.btnUiScale1.setOnClickListener { setUiScale(1) }
        vb.btnUiScale25.setOnClickListener { setUiScale(25) }
        vb.btnUiScale50.setOnClickListener { setUiScale(50) }
        vb.btnUiScale75.setOnClickListener { setUiScale(75) }
        vb.btnUiScale100.setOnClickListener { setUiScale(100) }
        vb.btnUiScale125.setOnClickListener { setUiScale(125) }
        vb.btnUiScale150.setOnClickListener { setUiScale(150) }
        vb.btnUiScale200.setOnClickListener { setUiScale(200) }
        vb.btnUiScale300.setOnClickListener { setUiScale(300) }
        vb.btnUiScaleApply.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.applyUiScale()
            Toast.makeText(context, R.string.apply_ui_scale, Toast.LENGTH_SHORT).show()
        }

        // Web Page Zoom controls
        val minWebZoom = Config.WEB_PAGE_ZOOM_PERCENT_MIN
        val maxWebZoom = Config.WEB_PAGE_ZOOM_PERCENT_MAX
        vb.sbWebPageZoom.max = maxWebZoom - minWebZoom
        vb.sbWebPageZoom.progress = config.webPageZoomPercent - minWebZoom
        vb.tvWebPageZoomValue.text = "${config.webPageZoomPercent}%"

        vb.sbWebPageZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = minWebZoom + progress
                config.webPageZoomPercent = value
                vb.tvWebPageZoomValue.text = "$value%"
                mainAct?.applyWebPageZoom(value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun setWebZoom(percent: Int) {
            config.webPageZoomPercent = percent
            vb.sbWebPageZoom.progress = (percent - minWebZoom).coerceIn(0, maxWebZoom - minWebZoom)
            vb.tvWebPageZoomValue.text = "$percent%"
            mainAct?.applyWebPageZoom(percent)
        }

        vb.btnWebZoom75.setOnClickListener { setWebZoom(75) }
        vb.btnWebZoom100.setOnClickListener { setWebZoom(100) }
        vb.btnWebZoom125.setOnClickListener { setWebZoom(125) }
        vb.btnWebZoom150.setOnClickListener { setWebZoom(150) }
        vb.btnWebZoom200.setOnClickListener { setWebZoom(200) }
    }
}
