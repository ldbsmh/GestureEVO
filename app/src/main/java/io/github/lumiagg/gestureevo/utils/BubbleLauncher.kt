package io.github.lumiagg.gestureevo.utils

import android.content.Context
import android.content.Intent
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.diagnostics.AppLogger
import io.github.lumiagg.gestureevo.diagnostics.LogLevel
import io.github.lumiagg.gestureevo.diagnostics.LogModule

/**
 * 把手势选中的应用送进 Android 原生气泡小窗（BubbleStackView）。
 *
 * 气泡由 SystemUI 的 BubbleController 创建和展开，第三方应用没有任何公开 API
 * 能触发它，所以这里发一条**显式**广播给 MoreBubbleButton 模块
 * （它 hook 在 SystemUI 进程里，收到后走官方
 * BubbleController.expandStackAndSelectAppBubble）。
 *
 * 协议与模块的 BubbleBroadcastBridge 对齐：
 *   action = com.floatwindow.morebubblebutton.action.SHOW_APP_BUBBLE
 *   extra  = packageName / activityName / userId
 *
 * 目标进程用 setPackage 锁定，避免被其他应用的同名广播接收器截胡；
 * 模块侧按发送方包名校验，GestureEVO 在它的白名单里。
 */
object BubbleLauncher {
    const val TARGET_PACKAGE = "com.android.systemui"
    const val ACTION_SHOW_APP_BUBBLE =
        "com.floatwindow.morebubblebutton.action.SHOW_APP_BUBBLE"

    private const val EXTRA_PACKAGE = "packageName"
    private const val EXTRA_ACTIVITY = "activityName"
    private const val EXTRA_USER_ID = "userId"

    /**
     * @param pkg          要放进气泡的应用包名
     * @param activityName 应用内具体界面（可选，省略则走启动入口）
     * @param userId       目标用户（工作资料/分身用，默认 0）
     */
    fun showAppBubble(
        context: Context,
        pkg: String,
        activityName: String? = null,
        userId: Long = 0,
        trace: String? = null
    ): Boolean {
        return try {
            val intent = Intent(ACTION_SHOW_APP_BUBBLE).apply {
                // 同时指定包名和动作；SystemUI 的动态接收器只按 action 匹配，
                // setPackage 是为了不让其它应用的同名接收器截胡。
                setPackage(TARGET_PACKAGE)
                putExtra(EXTRA_PACKAGE, pkg)
                if (!activityName.isNullOrEmpty()) putExtra(EXTRA_ACTIVITY, activityName)
                if (userId > 0) putExtra(EXTRA_USER_ID, userId.toInt())
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
            AppLogger.event(
                LogModule.ACTION, "bubble_broadcast_submitted", trace = trace,
                fields = mapOf("package" to pkg, "target" to TARGET_PACKAGE)
            )
            android.util.Log.i(
                "BubbleLauncher",
                "sendBroadcast -> pkg=$pkg uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()}"
            )
            ToastHelper.showInUIThread(context, R.string.gesture_bubble_request_sent)
            true
        } catch (e: Exception) {
            AppLogger.event(
                LogModule.ACTION, "bubble_broadcast_failed", LogLevel.WARN, trace, error = e,
                fields = mapOf("package" to pkg)
            )
            false
        }
    }
}
