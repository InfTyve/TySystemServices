package com.android.ty.systemservices.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机广播处理（不拉起界面，只恢复后台状态）：
 * 1. 重放「禁止下拉状态栏」标志 —— StatusBarManager 的 disable 标志只存在
 *    system_server 内存里，重启后会被清空，必须按持久化开关重新下发；
 * 2. 恢复 TestMode 悬浮窗 —— 开关状态持久化在 dev_settings，
 *    这里按开关重新 addView，实现"开机后悬浮窗自动出现"。
 *
 * <p>覆盖安装（MY_PACKAGE_REPLACED）同样恢复一次：进程被重启后
 * View 引用已丢失，需要重新挂上。</p>
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        boolean bootCompleted = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean packageReplaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!bootCompleted && !packageReplaced) {
            return;
        }

        Log.i(TAG, "restore background state on " + action);

        // 状态栏 disable 标志只存在 system_server 内存，重启后需要重放
        StatusBarController.reapplyIfEnabled(context);

        // 悬浮窗按持久化开关恢复：开启过就自动挂回来，未开启则什么都不做
        TestModeOverlay.restore(context);

        // 拉起守护服务（START_STICKY + 闹钟 + system_server 心跳）
        OverlayDaemon.start(context);
    }
}
