package com.example.todo_list.ui.list

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.todo_list.data.Task
import com.example.todo_list.databinding.ItemTaskBinding

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

        holder.binding.titleTextView.text = task.title

        if (task.description.isBlank()) {
            holder.binding.descriptionTextView.visibility = View.GONE
        } else {
            holder.binding.descriptionTextView.visibility = View.VISIBLE
            holder.binding.descriptionTextView.text = task.description
        }

        // 暂无截止日期字段，先隐藏，等附加项"日期时间设置"做完再显示
        holder.binding.dueDateTextView.visibility = View.GONE

        // 完成后标题加中划线
        holder.binding.titleTextView.paintFlags =
            if (task.isCompleted) {
                holder.binding.titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.binding.titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

        // ⚠️ 点击目标用卡片容器而不是 root：
        // root 现在带 padding（用于卡片间距），点空隙不应触发
        holder.binding.cardLayout.setOnClickListener {
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
