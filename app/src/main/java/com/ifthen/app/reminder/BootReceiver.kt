package com.ifthen.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ifthen.app.data.AppDatabase
import com.ifthen.app.data.RuleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 开机后闹钟会清空，重新给所有带提醒的活跃预案排上 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rules = AppDatabase.get(context).ruleDao().observeAll().first()
                rules.filter { it.status == RuleStatus.ACTIVE.name && it.reminderMin >= 0 }
                    .forEach { ReminderScheduler.scheduleNext(context, it) }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
