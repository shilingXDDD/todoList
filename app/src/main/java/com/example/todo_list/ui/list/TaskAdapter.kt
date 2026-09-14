package com.example.todo_list.ui.list

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.todo_list.R
import com.example.todo_list.data.Task
import com.example.todo_list.databinding.ItemTaskBinding
import com.example.todo_list.util.TimeUtil
import android.graphics.Color

class TaskAdapter(
    private val onItemClick: (Task) -> Unit,
    private val onCheckedChange: (Task, Boolean) -> Unit
) : ListAdapter<Task, TaskAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = getItem(position)

        // 分类标签：空内容时隐藏，避免显示一个空胶囊
        if (task.category.isBlank()) {
            holder.binding.categoryTextView.visibility = android.view.View.GONE
        } else {
            holder.binding.categoryTextView.visibility = android.view.View.VISIBLE
            holder.binding.categoryTextView.text = task.category
        }

        // 优先级色条：卡片左侧的竖条
        // 3高=红 2中=橙 1低=蓝 0无=透明
        val priorityColor = when (task.priority) {
            3 -> ContextCompat.getColor(holder.itemView.context, R.color.overdue_red)
            2 -> Color.parseColor("#FF9800")
            1 -> ContextCompat.getColor(holder.itemView.context, R.color.primary)
            else -> Color.TRANSPARENT
        }
        holder.binding.priorityBar.setBackgroundColor(priorityColor)

        // 截止日期：过期且未完成 → 红色
        val dueDate = task.dueDate
        if (dueDate != null) {
            holder.binding.dueDateTextView.visibility = android.view.View.VISIBLE
            holder.binding.dueDateTextView.text =
                holder.itemView.context.getString(R.string.label_due_date, TimeUtil.format(dueDate))

            // ⚠️ 已完成的任务不算过期
            val isOverdue = !task.isCompleted && dueDate < System.currentTimeMillis()
            holder.binding.dueDateTextView.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (isOverdue) R.color.overdue_red else R.color.text_secondary
                )
            )
        } else {
            holder.binding.dueDateTextView.visibility = android.view.View.GONE
        }

        holder.binding.titleTextView.text = task.title

        if (task.description.isBlank()) {
            holder.binding.descriptionTextView.visibility = android.view.View.GONE
        } else {
            holder.binding.descriptionTextView.visibility = android.view.View.VISIBLE
            holder.binding.descriptionTextView.text = task.description
        }

        // 完成后标题加中划线 + 置灰
        holder.binding.titleTextView.paintFlags =
            if (task.isCompleted) {
                holder.binding.titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.binding.titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

        val titleColorRes = if (task.isCompleted) R.color.text_disabled else R.color.text_primary
        holder.binding.titleTextView.setTextColor(
            ContextCompat.getColor(holder.itemView.context, titleColorRes)
        )

        // 整行点击 → 进入编辑页
        holder.binding.root.setOnClickListener {
            onItemClick(task)
        }

        // ⚠️ 关键：先清空旧监听器，再设值，最后设新监听器
        holder.binding.completedCheckBox.setOnCheckedChangeListener(null)
        holder.binding.completedCheckBox.isChecked = task.isCompleted
        holder.binding.completedCheckBox.setOnCheckedChangeListener { _, isChecked ->
            onCheckedChange(task, isChecked)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id          // 是不是同一条数据
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem                // 内容有没有变（靠 data class 的 equals）
        }
    }
}