package com.example.todo_list.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.todo_list.R
import com.example.todo_list.ui.detail.TaskDetailActivity

class TaskReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TASK_REMIND = "com.example.todo_list.TASK_REMIND"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"

        private const val CHANNEL_ID = "task_reminder"
        private const val CHANNEL_NAME = "任务提醒"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TASK_REMIND) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        if (taskId == -1L) return

        showNotification(context, taskId, title)
    }

    private fun showNotification(context: Context, taskId: Long, title: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ① 创建通知渠道（Android 8.0+ 必须，否则通知不显示）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
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

        // ③ 构建并发出通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.msg_reminder_content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)               // 点击后自动消失
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(taskId.toInt(), notification)
    }
}