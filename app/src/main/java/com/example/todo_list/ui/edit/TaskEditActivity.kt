package com.example.todo_list.ui.edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.todo_list.R
import com.example.todo_list.data.Task
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.ActivityTaskEditBinding
import com.example.todo_list.util.ReminderManager
import com.example.todo_list.util.TimeUtil
import kotlinx.coroutines.launch
import java.util.Calendar

class TaskEditActivity : AppCompatActivity() {

    private val categories = arrayOf("默认", "学习", "工作", "生活")
    private val priorityNames = arrayOf("无", "低", "中", "高")

    // 选中的截止日期，null 表示未设置
    private var selectedDueDate: Long? = null

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
        binding.categorySpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        binding.prioritySpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, priorityNames)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // 显示返回箭头

        // 取出列表页传来的 ID；取不到就是 -1，代表新增
        taskId = intent.getLongExtra(EXTRA_TASK_ID, NO_TASK_ID)

        if (taskId != NO_TASK_ID) {
            setupEditMode()
        } else {
            setupCreateMode()
        }

        binding.btnPickDatetime.setOnClickListener { showDateTimePicker() }

        binding.btnClearDatetime.setOnClickListener {
            selectedDueDate = null
            binding.btnPickDatetime.text = getString(R.string.action_set_date)
        }

        binding.saveButton.setOnClickListener {
            saveTask()
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                // ⚠️ month 是 0~11（0 代表 1 月），直接传给 Calendar.set() 即可，不要 ±1
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val cal = Calendar.getInstance().apply {
                            // ⚠️ 秒沿用当前秒数，不要写死 0
                            //    写 0 的话：当前 14:30:45 选 14:31，实际提醒是 14:31:00，
                            //    只有 15 秒后就响，和"1 分钟后"的预期不符
                            set(year, month, day, hour, minute, calendar.get(Calendar.SECOND))
                            set(Calendar.MILLISECOND, 0)   // 毫秒必须清零，否则排序和"是不是今天"会出错
                        }
                        selectedDueDate = cal.timeInMillis
                        binding.btnPickDatetime.text = TimeUtil.format(cal.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
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
            val index = categories.indexOf(task.category)
            if (index >= 0) binding.categorySpinner.setSelection(index)
            binding.prioritySpinner.setSelection(task.priority.coerceIn(0, 3))

            // 备注 / 链接
            binding.noteEditText.setText(task.note)
            binding.linkEditText.setText(task.link)

            // 截止日期
            selectedDueDate = task.dueDate
            binding.btnPickDatetime.text =
                task.dueDate?.let { TimeUtil.format(it) } ?: getString(R.string.action_set_date)
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
            // ⚠️ 必须先取出 Spinner 的当前选中值，不能等到协程里再取
            //    （binding 在 finish() 后可能失效）
            val category = binding.categorySpinner.selectedItem?.toString() ?: "默认"
            val priority = binding.prioritySpinner.selectedItemPosition
            val note = binding.noteEditText.text?.toString()?.trim().orEmpty()
            val link = binding.linkEditText.text?.toString()?.trim().orEmpty()
            val dueDate = selectedDueDate
            var scheduled = false

            if (taskId != NO_TASK_ID) {
                val existing = TaskRepository.getTaskById(taskId)
                if (existing != null) {
                    val updated = existing.copy(
                        title = title,
                        description = description,
                        isCompleted = isCompleted,
                        category = category,
                        priority = priority,
                        note = note,
                        link = link,
                        dueDate = dueDate,
                        reminderTime = dueDate      // 有截止日期就到点提醒
                    )
                    TaskRepository.updateTask(updated)
                    // 先取消旧的再注册新的，避免改了日期后还按老时间提醒
                    ReminderManager.cancel(this@TaskEditActivity, updated.id)
                    val ok = ReminderManager.schedule(this@TaskEditActivity, updated)
                    scheduled = dueDate != null && ok
                }
            } else {
                val newTask = Task(
                    title = title,
                    description = description,
                    isCompleted = isCompleted,
                    category = category,
                    priority = priority,
                    note = note,
                    link = link,
                    dueDate = dueDate,
                    reminderTime = dueDate
                )
                val newId = TaskRepository.addTask(newTask)
                // ⚠️ 新增时 id 是数据库生成的，要用返回值构造带 id 的对象去注册
                val ok = ReminderManager.schedule(this@TaskEditActivity, newTask.copy(id = newId))
                scheduled = dueDate != null && ok
            }

            // 提醒注册结果要让用户看到，否则根本不知道有没有设上
            val msg = when {
                dueDate == null -> getString(R.string.msg_save_success)
                scheduled -> getString(R.string.msg_reminder_scheduled) + "：" +
                        TimeUtil.format(dueDate)
                else -> getString(R.string.msg_reminder_time_past)
            }
            Toast.makeText(this@TaskEditActivity, msg, Toast.LENGTH_LONG).show()
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