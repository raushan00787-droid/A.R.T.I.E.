package com.raushan.jarvis

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small system-owned assistant surface.
 *
 * Lifecycle:
 * - Android creates the session after the session service returns it.
 * - onCreateContentView() builds the session's content view.
 * - onShow()/onHide() follow each visible interaction.
 * - onDestroy() is the final cleanup callback for this session.
 *
 * This is not a SYSTEM_ALERT_WINDOW overlay: Android hosts it in the
 * voice-interaction window, so no overlay permission is required.
 */
class JarvisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val appContext = context
    private var statusView: TextView? = null

    override fun onCreateContentView(): View {
        val root = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }

        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 18, 30))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(48, 190, 225))
            }
            elevation = dp(12).toFloat()
        }

        val title = TextView(appContext).apply {
            text = "J.A.R.V.I.S."
            setTextColor(Color.rgb(119, 229, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.10f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(appContext).apply {
            text = "At your service."
            setTextColor(Color.rgb(222, 238, 247))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(14))
        }

        statusView = TextView(appContext).apply {
            text = "READY"
            setTextColor(Color.rgb(92, 190, 211))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }

        val actions = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val openButton = actionText("OPEN JARVIS", primary = true) {
            val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startAssistantActivity(launchIntent)
        }

        val dismissButton = actionText("DISMISS", primary = false) {
            finish()
        }

        actions.addView(
            openButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(6)
            }
        )
        actions.addView(
            dismissButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(6)
            }
        )

        card.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            statusView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        statusView?.text = "READY"
    }

    override fun onHide() {
        super.onHide()
        statusView?.text = "STANDBY"
    }

    override fun onDestroy() {
        statusView = null
        super.onDestroy()
    }

    private fun actionText(
        label: String,
        primary: Boolean,
        onClick: () -> Unit
    ): TextView = TextView(appContext).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.04f
        setTextColor(
            if (primary) Color.rgb(4, 20, 30) else Color.rgb(125, 224, 246)
        )
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(
                if (primary) Color.rgb(103, 218, 245) else Color.rgb(13, 34, 49)
            )
            if (!primary) {
                setStroke(dp(1), Color.rgb(56, 149, 180))
            }
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density + 0.5f).toInt()
}
