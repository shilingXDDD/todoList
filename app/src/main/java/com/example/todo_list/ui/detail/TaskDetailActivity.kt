package com.example.todo_list.ui.detail

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.todo_list.R
import com.example.todo_list.data.Task
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.ActivityTaskDetailBinding
import com.example.todo_list.ui.edit.TaskEditActivity
import kotlinx.coroutines.launch

class TaskDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val NO_TASK_ID = -1L

        fun startUI(context: Context, taskId: Long) {
            val intent = Intent(context, TaskDetailActivity::class.java)
            intent.putExtra(EXTRA_TASK_ID, taskId)
            context.startActivity(intent)
        }
    }

    private val binding by lazy {
        ActivityTaskDetailBinding.inflate(layoutInflater)
    }

    private var taskId = NO_TASK_ID
    private var currentTask: Task? = null

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
    }
}
