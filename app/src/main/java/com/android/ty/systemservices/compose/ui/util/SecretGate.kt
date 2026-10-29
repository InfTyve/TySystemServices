package com.android.ty.systemservices.compose.ui.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.security.MessageDigest
import java.util.UUID

/**
 * 轻量口令校验与进入凭证管理。
 *
 * 源码与资源里都不保留明文口令，只存 `SHA-256(盐 + 口令)` 的十六进制摘要；
 * 校验时重新计算摘要并用 [MessageDigest.isEqual] 做定长比较。
 *
 * 口令通过后并不会给 [DevActivity] 长期通行证：每次进入前都要先
 * [issueLaunchTicket] 领一张**一次性、短时效**的票据，Activity 侧
 * [consumeLaunchTicket] 用完即焚。这样即使用 root 执行
 * `am start -n .../DevActivity` 直接拉起，也拿不到票据，会被挡在门外。
 */
object SecretGate {

    /** 触发口令框所需的连续点击次数 */
    const val REQUIRED_TAPS = 10

    private const val SALT = "TySystemServices@TeYun"

    /** SHA-256(SALT + 口令) 的小写十六进制串 */
    private const val DIGEST = "c022a8bf7f9d4a452416dda6523d03370d3776a56f617094376b199c56593129"

    private const val PREFS = "ty_secret_gate"
    private const val KEY_TICKET = "launch_ticket"
    private const val KEY_ISSUED_AT = "launch_ticket_at"

    /** 票据有效期：只覆盖 startActivity 到 onCreate 这段，给慢机器留足余量 */
    private const val TICKET_TTL_MS = 20_000L

    /** 校验输入口令是否正确 */
    fun verify(input: String): Boolean {
        if (input.isEmpty()) return false
        val actual = sha256(SALT + input).toByteArray(Charsets.UTF_8)
        val expected = DIGEST.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }

    /**
     * 口令通过后、启动 [DevActivity] 之前调用，签发一张一次性票据。
     */
    fun issueLaunchTicket(context: Context) {
        prefs(context).edit()
            .putString(KEY_TICKET, UUID.randomUUID().toString())
            .putLong(KEY_ISSUED_AT, SystemClock.elapsedRealtime())
            .apply()
    }

    /**
     * [DevActivity] 启动时消费票据。
     *
     * 无论校验成功与否票据都会被清掉，保证「一次授权只能用一次」。
     */
    fun consumeLaunchTicket(context: Context): Boolean {
        val prefs = prefs(context)
        val ticket = prefs.getString(KEY_TICKET, null)
        val issuedAt = prefs.getLong(KEY_ISSUED_AT, 0L)
        // 先作废再判断，避免并发或异常路径下票据被重复使用
        prefs.edit().remove(KEY_TICKET).remove(KEY_ISSUED_AT).apply()

        if (ticket.isNullOrEmpty()) return false
        val now = SystemClock.elapsedRealtime()
        // now < issuedAt 说明中间重启过设备（elapsedRealtime 归零），一律视为失效
        if (now < issuedAt) return false
        return now - issuedAt <= TICKET_TTL_MS
    }

    /**
     * [DevActivity] 的入口总闸：调用来源 + 一次性票据双重校验。
     *
     * 来源校验能挡住 `am start`、第三方 App 显式拉起这类不是从首页进入的情况；
     * 拿不到来源包名时（个别 ROM 会返回 null）不直接拒绝，交给票据判定，
     * 避免正常入口被误伤。
     */
    fun canEnterDev(activity: Activity): Boolean {
        val caller = launchedFromPackage(activity)
        val self = activity.packageName
        if (caller != null && caller != self && caller != activity.opPackageName) {
            return false
        }
        return consumeLaunchTicket(activity)
    }

    /** 读取启动本 Activity 的调用方包名；取不到返回 null */
    private fun launchedFromPackage(activity: Activity): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.launchedFromPackage
        } else {
            // 低版本没有公开 API，退回反射；失败按「未知」处理
            Activity::class.java
                .getMethod("getLaunchedFromPackage")
                .invoke(activity) as? String
        }
    } catch (_: Throwable) {
        null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) append("%02x".format(b))
        }
    }
}
