package com.example.todo_list.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.todo_list.receiver.TaskReceiver
import com.example.todo_list.data.Task
import com.example.todo_list.util.TimeUtil

/**
 * 定时任务提醒的统一管理。
 *
 * 集中放在这里是为了保证「注册」和「取消」用的是同一套 Intent 规则：
 * 只要 action 和 requestCode 一致，PendingIntent 就能匹配上，任务才能被正确取消。
 */
object ReminderManager {

    private const val TAG = "ReminderManager"

    /**
     * 构造与任务一一对应的 PendingIntent。
     *
     * ⚠️ requestCode 用 taskId，保证每条任务的 PendingIntent 互不相同，
     *    否则后一条会覆盖前一条的提醒。
     *
     * ⚠️⚠️ 必须放 extra！
     *    PendingIntent 的「匹配」确实不看 extra（所以 cancel 时可以不带），
     *    但 extra 会被原样传递给 Receiver。不放的话 TaskReceiver 拿到的
     *    taskId 是 -1，会直接 return，通知永远弹不出来。
     */
    private fun pendingIntent(context: Context, taskId: Long, title: String = ""): PendingIntent {
        val intent = Intent(context, TaskReceiver::class.java).apply {
            action = TaskReceiver.ACTION_TASK_REMIND
            putExtra(TaskReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReceiver.EXTRA_TASK_TITLE, title)
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

        // ⚠️ 必须传 title，否则通知标题是空的
        val pi = pendingIntent(context, task.id, task.title)

        // ⚠️ 关键：不要用 set()
        //    Android 4.4 起 set() 不再精确，且息屏进入 Doze 后会被大幅延迟（可能几十分钟）。
        //    这里优先用精确闹钟；没权限时退化为 AllowWhileIdle 版本（Doze 下也能触发）。
        if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi)
            Log.d(TAG, "已注册精确提醒：taskId=${task.id} at ${TimeUtil.format(remindAt)}")
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi)
            Log.w(TAG, "无精确闹钟权限，退化为非精确：taskId=${task.id} at ${TimeUtil.format(remindAt)}")
        }
        return true
    }

    /**
     * Android 12（API 31）起，精确闹钟需要 SCHEDULE_EXACT_ALARM 权限。
     * Android 14（API 34）起该权限**默认关闭**，需要用户到设置页手动开启。
     */
    private fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return alarmManager?.canScheduleExactAlarms() ?: false
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
