package com.example.todo_list.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.todo_list.R
import com.example.todo_list.ui.detail.TaskDetailActivity

class TaskReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TASK_REMIND = "com.example.todo_list.TASK_REMIND"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"

        private const val TAG = "TaskReceiver"
        private const val CHANNEL_ID = "task_reminder"
        private const val CHANNEL_NAME = "任务提醒"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "① onReceive 被调用，action=${intent.action}")

        if (intent.action != ACTION_TASK_REMIND) {
            Log.w(TAG, "❌ action 不是 TASK_REMIND，直接 return")
            return
        }

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        Log.d(TAG, "② taskId=$taskId, title=$title")

        if (taskId == -1L) {
            Log.w(TAG, "❌ taskId 无效，直接 return")
            return
        }

        showNotification(context, taskId, title)
    }

    private fun showNotification(context: Context, taskId: Long, title: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ③ 关键诊断：App 级通知总开关
        val enabled = notificationManager.areNotificationsEnabled()
        Log.d(TAG, "③ 通知总开关 areNotificationsEnabled = $enabled")
        if (!enabled) {
            Log.e(TAG, "❌ 系统层面通知被关闭！去 设置→应用→TodoList→通知 打开")
        }

        // ④ 创建通知渠道（Android 8.0+ 必须，否则通知不显示）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)

            // 读回渠道真实状态：用户可能在系统设置里单独降级或关闭过
            val actual = notificationManager.getNotificationChannel(CHANNEL_ID)
            Log.d(TAG, "④ 渠道 importance=${actual?.importance}（3=高 2=默认 1=低 0=被关掉）")
            if (actual?.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.e(TAG, "❌ 渠道已被关闭，notify() 不会显示任何东西")
            }
        }

        // ② PendingIntent：点通知进详情页
        val contentIntent = Intent(context, TaskDetailActivity::class.java)
            .putExtra(EXTRA_TASK_ID, taskId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),                    // requestCode 用 taskId，不同任务通知互不覆盖
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ⑤ 构建并发出通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.msg_reminder_content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)               // 点击后自动消失
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .build()

        Log.d(TAG, "⑤ 调用 notify()，id=${taskId.toInt()}")
        notificationManager.notify(taskId.toInt(), notification)
        Log.d(TAG, "⑥ notify() 已返回，没抛异常即表示提交成功")
    }
}