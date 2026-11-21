package com.android.ty.systemservices.terminal

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.preference.PreferenceManager

/**
 * 终端设置的读写入口。
 *
 * 直接复用 PreferenceManager 的默认 SharedPreferences，这样设置页改完，
 * 终端页下次创建视图就能读到，不需要额外的同步逻辑。
 */
object TermuxPreferences {

    const val KEY_VOLUME_KEY = "volumekey_mode"
    const val KEY_FULLSCREEN = "fullscreen_mode"
    const val KEY_ORIENTATION = "screen_orientation"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_COLORS = "text_colors"

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun fontSize(context: Context): Int =
        prefs(context).getString(KEY_FONT_SIZE, "14")?.toIntOrNull() ?: 14

    fun volumeKeyMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOLUME_KEY, false)

    fun fullscreen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FULLSCREEN, false)

    fun orientation(context: Context): String =
        prefs(context).getString(KEY_ORIENTATION, "automatic") ?: "automatic"

    /** 配色存成 "#前景/#背景"，解析失败时回落到黑底白字 */
    fun colors(context: Context): Pair<Int, Int> {
        val raw = prefs(context).getString(KEY_COLORS, "#FFFFFF/#000000") ?: "#FFFFFF/#000000"
        val parts = raw.split("/")
        if (parts.size != 2) return Color.WHITE to Color.BLACK
        val fg = runCatching { Color.parseColor(parts[0].trim()) }.getOrDefault(Color.WHITE)
        val bg = runCatching { Color.parseColor(parts[1].trim()) }.getOrDefault(Color.BLACK)
        return fg to bg
    }
}
