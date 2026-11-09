package com.ifthen.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ifthen.app.data.Rule
import java.util.Calendar

/** 预案定时提醒：用 AlarmManager 的 inexact（setAndAllowWhileIdle）排下一次，到点由 ReminderReceiver 发通知并重排。 */
object ReminderScheduler {
    const val CHANNEL_ID = "ifthen_reminder"
    private const val ACTION = "com.ifthen.app.REMIND"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "预案提醒", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "到点提醒你执行 if-then 预案"
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun scheduleNext(context: Context, rule: Rule) {
        if (rule.id <= 0L || rule.reminderMin < 0) return
        ensureChannel(context)
        val trigger = nextTriggerMillis(rule.reminderMin, rule.reminderDays, System.currentTimeMillis())
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(context, rule.id, 0))
    }

    fun cancel(context: Context, ruleId: Long) {
        if (ruleId <= 0L) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, ruleId, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun pendingIntent(context: Context, ruleId: Long, extraFlags: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("ifthen://reminder/$ruleId")
            putExtra("ruleId", ruleId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or extraFlags
        return PendingIntent.getBroadcast(context, ruleId.toInt(), intent, flags)
    }

    /** 从「今天/未来 8 天」里找下一个匹配 reminderDays 的时刻（毫秒） */
    fun nextTriggerMillis(minOfDay: Int, daysMask: Int, now: Long): Long {
        val mask = if (daysMask == 0) 127 else daysMask
        val cal = Calendar.getInstance()
        for (i in 0..7) {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, i)
            val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=周日 .. 7=周六
            val bit = when (dow) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                else -> 6
            }
            if ((mask shr bit) and 1 == 0) continue
            cal.set(Calendar.HOUR_OF_DAY, minOfDay / 60)
            cal.set(Calendar.MINUTE, minOfDay % 60)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > now + 1000) return cal.timeInMillis
        }
        return now + 24L * 60 * 60 * 1000
    }

    fun label(minOfDay: Int, daysMask: Int): String {
        if (minOfDay < 0) return "未设置"
        val hh = (minOfDay / 60).toString().padStart(2, '0')
        val mm = (minOfDay % 60).toString().padStart(2, '0')
        val dayText = when (daysMask) {
            0, 127 -> "每天"
            31 -> "工作日"
            else -> "周" + weekdaysText(daysMask)
        }
        return "$dayText $hh:$mm"
    }

    private fun weekdaysText(mask: Int): String {
        val names = arrayOf("一", "二", "三", "四", "五", "六", "日")
        val sb = StringBuilder()
        names.forEachIndexed { i, n -> if ((mask shr i) and 1 == 1) sb.append(n) }
        return if (sb.isEmpty()) "一" else sb.toString()
    }
}
