package com.ifthen.app.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ifthen.app.MainActivity
import com.ifthen.app.R
import com.ifthen.app.data.AppDatabase
import com.ifthen.app.data.RuleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 到点触发：查当前规则 -> 发通知 -> 排下一次 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra("ruleId", -1L)
        if (ruleId <= 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rule = AppDatabase.get(context).ruleDao().getById(ruleId)
                if (rule != null && rule.status == RuleStatus.ACTIVE.name && rule.reminderMin >= 0) {
                    ReminderScheduler.ensureChannel(context)
                    notify(context, rule.id, rule.ifText, rule.thenText)
                    ReminderScheduler.scheduleNext(context, rule)
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, id: Long, ifText: String, thenText: String) {
        val openApp = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setContentTitle("布谷 · 该做了")
            .setContentText(thenText)
            .setStyle(Notification.BigTextStyle().bigText("如果 $ifText，就 $thenText"))
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(id.toInt(), notif)
        } catch (_: SecurityException) {
            // 没给通知权限时静默（启动时会请求）
        }
    }
}
