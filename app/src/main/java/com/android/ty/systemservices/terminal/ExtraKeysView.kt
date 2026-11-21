package com.android.ty.systemservices.terminal

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 终端底部那一行快捷键：ESC / CTRL / ALT / TAB / - / / / | / 四个方向键。
 *
 * CTRL 与 ALT 是「粘滞」修饰键：点一下变绿，此后输入的字符会自动带上修饰；
 * 再点一下取消。真正的组合逻辑在 TerminalView 里，通过 readControlKey() 拿走。
 */
class ExtraKeysView(context: Context) : LinearLayout(context) {

    var onKey: ((String) -> Unit)? = null
    var onModifierChanged: ((ctrl: Boolean, alt: Boolean) -> Unit)? = null

    private var ctrl = false
    private var alt = false
    private lateinit var ctrlKey: TextView
    private lateinit var altKey: TextView

    init {
        orientation = HORIZONTAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setBackgroundColor(0xFF141414.toInt())

        ctrlKey = addKey("CTRL") { toggleCtrl() }
        altKey = addKey("ALT") { toggleAlt() }
        addKey("ESC") { onKey?.invoke("\u001b") }
        addKey("TAB") { onKey?.invoke("\t") }
        addKey("-") { onKey?.invoke("-") }
        addKey("/") { onKey?.invoke("/") }
        addKey("|") { onKey?.invoke("|") }
        addKey("←") { onKey?.invoke("\u001b[D") }
        addKey("↓") { onKey?.invoke("\u001b[B") }
        addKey("↑") { onKey?.invoke("\u001b[A") }
        addKey("→") { onKey?.invoke("\u001b[C") }
    }

    fun isCtrlPressed(): Boolean = ctrl

    fun isAltPressed(): Boolean = alt

    private fun toggleCtrl() {
        ctrl = !ctrl
        ctrlKey.isActivated = ctrl
        onModifierChanged?.invoke(ctrl, alt)
    }

    private fun toggleAlt() {
        alt = !alt
        altKey.isActivated = alt
        onModifierChanged?.invoke(ctrl, alt)
    }

    private fun addKey(label: String, action: () -> Unit): TextView {
        val key = TextView(context).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setBackgroundResource(com.android.ty.systemservices.R.drawable.termux_key_background)
            isClickable = true
            isFocusable = false
            minWidth = dp(44)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
            // 按下时不要抢焦点，否则软键盘会收起
            setOnClickListener { action() }
        }
        key.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    v.performClick()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
        addView(key)
        return key
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /** 会话结束后把修饰键状态清掉，免得下次输入莫名其妙带 CTRL */
    fun resetModifiers() {
        ctrl = false
        alt = false
        ctrlKey.isActivated = false
        altKey.isActivated = false
        onModifierChanged?.invoke(false, false)
    }

    fun setVisibilityOfKeys(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}
