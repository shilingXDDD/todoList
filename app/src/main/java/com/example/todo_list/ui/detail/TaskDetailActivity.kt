package com.example.todo_list.ui.detail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.todo_list.R
import com.example.todo_list.data.Task
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.ActivityTaskDetailBinding
import com.example.todo_list.receiver.TaskReceiver
import com.example.todo_list.ui.edit.TaskEditActivity
import com.example.todo_list.util.ReminderManager
import com.example.todo_list.util.TimeUtil
import kotlinx.coroutines.launch

class TaskDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TaskDetail"
        private const val EXTRA_TASK_ID = "task_id"
        private const val NO_TASK_ID = -1L

        fun startUI(context: Context, taskId: Long) {
            val intent = Intent(context, TaskDetailActivity::class.java)
            intent.putExtra(EXTRA_TASK_ID, taskId)
            context.startActivity(intent)
        }
    }

    /**
     * 权限申请通过时，要补发的那次点击动作。
     *
     * 背景：Android 13+ 第一次点「提醒」只会弹权限框，不发通知，
     * 用户会以为"点了没反应"。存下这个动作，等用户点了「允许」立刻补发，
     * 就不需要再点第二次了。
     */
    private var pendingReminderAction: (() -> Unit)? = null

    private val binding by lazy {
        ActivityTaskDetailBinding.inflate(layoutInflater)
    }

    private var taskId = NO_TASK_ID
    private var currentTask: Task? = null

    /**
     * 通知权限申请结果回调。
     * registerForActivityResult 必须在 Activity 创建时（字段初始化阶段）注册，
     * 不能放在 onClick 里，否则会抛 IllegalStateException。
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // 用户点了「允许」→ 立刻补发刚才那次点击，不用再点一次
                Log.d(TAG, "权限已授予，补发提醒")
                pendingReminderAction?.invoke()
            } else {
                Toast.makeText(
                    this,
                    R.string.msg_notification_permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingReminderAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        taskId = intent.getLongExtra(EXTRA_TASK_ID, NO_TASK_ID)

        setupListeners()
    }

    /**
     * 放在 onResume 而不是 onCreate：
     * 从编辑页返回后会走到这里，页面能自动显示修改后的内容。
     */
    override fun onResume() {
        super.onResume()
        setupDetail(taskId)
    }

    private fun setupDetail(taskId: Long) {
        // 查数据库是 suspend，放进协程
        lifecycleScope.launch {
            val task = TaskRepository.getTaskById(taskId)
            if (task == null) {
                Toast.makeText(this@TaskDetailActivity, R.string.msg_task_not_found, Toast.LENGTH_SHORT)
                    .show()
                finish()
                return@launch
            }
            currentTask = task

            renderTask(task)
        }
    }

    private fun renderTask(task: Task) {
        // 标题：已完成时加中划线
        binding.txvTitle.text = task.title
        binding.txvTitle.paintFlags =
            if (task.isCompleted) {
                binding.txvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.txvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

        binding.txvStatus.text = getString(
            if (task.isCompleted) R.string.status_completed else R.string.status_pending
        )

        // 分类：空字符串时隐藏标签，避免显示一个空胶囊
        if (task.category.isBlank()) {
            binding.txvCategory.visibility = View.GONE
        } else {
            binding.txvCategory.visibility = View.VISIBLE
            binding.txvCategory.text = task.category
        }

        // 截止日期：过期且未完成 → 红色
        val dueDate = task.dueDate
        if (dueDate != null) {
            binding.txvDueDate.visibility = View.VISIBLE
            binding.txvDueDate.text = getString(R.string.label_due_date, TimeUtil.format(dueDate))
            val isOverdue = !task.isCompleted && dueDate < System.currentTimeMillis()
            binding.txvDueDate.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isOverdue) R.color.overdue_red else R.color.text_secondary
                )
            )
        } else {
            binding.txvDueDate.visibility = View.GONE
        }

        // 备注：为空时整块隐藏
        if (task.note.isBlank()) {
            binding.txvNote.visibility = View.GONE
        } else {
            binding.txvNote.visibility = View.VISIBLE
            binding.txvNote.text = getString(R.string.label_note_with_value, task.note)
        }

        // 链接：为空时隐藏；有值时加下划线并可点击跳转浏览器
        if (task.link.isBlank()) {
            binding.txvLink.visibility = View.GONE
        } else {
            binding.txvLink.visibility = View.VISIBLE
            binding.txvLink.text = task.link
            binding.txvLink.paintFlags = binding.txvLink.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            binding.txvLink.setOnClickListener {
                // ⚠️ 必须补全 http 前缀，否则 Uri.parse 可能崩
                val url = if (task.link.startsWith("http")) task.link else "https://${task.link}"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        // 图标表达"点了会发生什么"：
        // 未完成 → 对勾（点它变完成）；已完成 → 撤销（点它变未完成）
        binding.btnToggleComplete.setImageResource(
            if (task.isCompleted) R.drawable.ic_undo else R.drawable.ic_check
        )

        binding.txvDesc.text = task.description
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnToggleComplete.setOnClickListener {
            val current = currentTask ?: return@setOnClickListener
            lifecycleScope.launch {
                // 用 copy 生成新对象再写回，不要直接改字段
                TaskRepository.updateTask(current.copy(isCompleted = !current.isCompleted))
                setupDetail(taskId)   // 页面在前台不会走 onResume，手动刷新一次
            }
        }

        binding.btnEdit.setOnClickListener {
            TaskEditActivity.startUI(this, taskId)
        }

        binding.btnDelete.setOnClickListener {
            val current = currentTask ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.msg_delete_confirm_title)
                .setPositiveButton(R.string.action_confirm) { _, _ ->
                    lifecycleScope.launch {
                        // ⚠️ 先取消提醒再删数据：
                        //    否则到点仍会弹通知，用户点进去任务已不存在会崩溃
                        ReminderManager.cancel(this@TaskDetailActivity, current.id)
                        TaskRepository.deleteTask(current)
                        Toast.makeText(this@TaskDetailActivity, R.string.msg_task_deleted, Toast.LENGTH_SHORT).show()
                        finish()      // 回到列表页
                    }
                }
                // 取消按钮：只关闭对话框，不做任何删除
                .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
                .create()
                .show()
        }

        binding.btnTestReminder.setOnClickListener {
            val current = currentTask
            if (current == null) {
                // 数据库查询是异步的，进页面立刻点就可能还是 null
                Log.w(TAG, "❌ currentTask 为空，提醒未发出")
                Toast.makeText(this, "任务尚未加载完成，请稍后再试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "点铃铛：taskId=${current.id}, title=${current.title}")

            ensureNotificationPermission {
                // 显式广播：直接指定 Receiver 的类名。
                // 比 setPackage 更可靠——静态注册的 Receiver 一定能被唤醒，
                // 不受 Android 8.0+ 隐式广播限制的影响。
                val intent = Intent(TaskReceiver.ACTION_TASK_REMIND)
                    .setClassName(this@TaskDetailActivity, TaskReceiver::class.java.name)
                    .putExtra(TaskReceiver.EXTRA_TASK_ID, current.id)
                    .putExtra(TaskReceiver.EXTRA_TASK_TITLE, current.title)
                Log.d(TAG, "发送广播 → ${intent.action}")
                sendBroadcast(intent)

                Toast.makeText(this, R.string.msg_reminder_sent, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 确保拿到通知权限后再执行 onGranted。
     *
     * Android 13（API 33）起，发通知需要运行时申请 POST_NOTIFICATIONS 权限，
     * 否则通知完全不显示。13 以下系统不需要申请，直接执行。
     */
    private fun ensureNotificationPermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                Log.d(TAG, "权限已具备，直接发提醒")
                onGranted()
            } else {
                // 存下动作，等用户在权限框点「允许」后由回调补发
                Log.d(TAG, "权限不足，先申请（授权后会自动补发）")
                pendingReminderAction = onGranted
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onGranted()
        }
    }
}
