package com.example.todo_list.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.todo_list.R
import com.example.todo_list.data.Task
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.ActivityTaskEditBinding
import kotlinx.coroutines.launch

class TaskEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val NO_TASK_ID = -1L

        fun startUI(context: Context, taskId: Long = NO_TASK_ID) {
            val intent = Intent(context, TaskEditActivity::class.java)
            intent.putExtra(EXTRA_TASK_ID, taskId)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityTaskEditBinding
    private var taskId = NO_TASK_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // 显示返回箭头

        // 取出列表页传来的 ID；取不到就是 -1，代表新增
        taskId = intent.getLongExtra(EXTRA_TASK_ID, NO_TASK_ID)

        if (taskId != NO_TASK_ID) {
            setupEditMode()
        } else {
            setupCreateMode()
        }

        binding.saveButton.setOnClickListener {
            saveTask()
        }
    }

    private fun setupEditMode() {
        // getTaskById 是 suspend（查数据库是耗时操作），必须放进协程
        lifecycleScope.launch {
            val task = TaskRepository.getTaskById(taskId)
            if (task == null) {
                // ⚠️ 判空：ID 非法时不能崩
                Toast.makeText(this@TaskEditActivity, R.string.msg_task_not_found, Toast.LENGTH_SHORT)
                    .show()
                finish()
                return@launch      // 协程里中断要用 return@launch，不能写裸 return
            }
            supportActionBar?.title = getString(R.string.title_edit_task)
            binding.titleEditText.setText(task.title)
            binding.descriptionEditText.setText(task.description)
            binding.completedCheckBox.isChecked = task.isCompleted
        }
    }

    private fun setupCreateMode() {
        supportActionBar?.title = getString(R.string.title_add_task)
    }

    private fun saveTask() {
        // trim() 很重要：只输入空格也算空
        val title = binding.titleEditText.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.msg_title_required)
            return                       // ⚠️ 直接 return，不保存不返回
        }
        binding.titleInputLayout.error = null   // 通过了就清掉错误提示

        val description = binding.descriptionEditText.text?.toString()?.trim().orEmpty()
        val isCompleted = binding.completedCheckBox.isChecked

        // 数据库写入放进协程（suspend 函数的硬性要求）
        lifecycleScope.launch {
            if (taskId != NO_TASK_ID) {
                TaskRepository.updateTask(
                    Task(taskId, title, description, isCompleted)
                )
            } else {
                TaskRepository.addTask(
                    Task(title = title, description = description, isCompleted = isCompleted)
                )
            }

            Toast.makeText(this@TaskEditActivity, R.string.msg_save_success, Toast.LENGTH_SHORT)
                .show()
            finish()  // 关闭当前页，回到列表页
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}