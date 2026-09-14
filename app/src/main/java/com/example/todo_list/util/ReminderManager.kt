package com.example.todo_list.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.todo_list.receiver.TaskReceiver
import com.example.todo_list.data.Task

/**
 * 定时任务提醒的统一管理。
 *
 * 集中放在这里是为了保证「注册」和「取消」用的是同一套 Intent 规则：
 * 只要 action 和 requestCode 一致，PendingIntent 就能匹配上，任务才能被正确取消。
 */
object ReminderManager {

    /**
     * 构造与任务一一对应的 PendingIntent。
     *
     * ⚠️ requestCode 用 taskId，保证每条任务的 PendingIntent 互不相同，
     *    否则后一条会覆盖前一条的提醒。
     *
     * ⚠️ extra 不参与 PendingIntent 的匹配，所以取消时可以不带 extra。
     */
    private fun pendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, TaskReceiver::class.java).apply {
            action = TaskReceiver.ACTION_TASK_REMIND
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 注册定时提醒。
     *
     * @return 是否注册成功
     */
    fun schedule(context: Context, task: Task): Boolean {
        val remindAt = task.reminderTime ?: return false
        if (remindAt <= System.currentTimeMillis()) return false

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false

        // 用 set() 而非 setExact()：
        // Android 12+ 使用 setExact 需要 SCHEDULE_EXACT_ALARM 权限，且该权限默认关闭，
        // 还要跳系统设置页让用户手动开。set() 无需额外权限，课设场景足够用。
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            remindAt,
            pendingIntent(context, task.id)
        )
        return true
    }

    /**
     * 取消定时提醒。
     *
     * ⚠️ 删除任务时必须调用，否则到点仍会弹出通知，
     *    用户点进去任务已不存在 → 崩溃。
     */
    fun cancel(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        val pi = pendingIntent(context, taskId)
        alarmManager.cancel(pi)
        pi.cancel()
    }
}
