package com.example.todo_list.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.FragmentTaskListBinding
import com.example.todo_list.ui.detail.TaskDetailActivity
import com.example.todo_list.ui.edit.TaskEditActivity

class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        binding.addTaskFab.setOnClickListener {
            // 不传 ID → 新增模式
            TaskEditActivity.startUI(requireContext())
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onItemClick = { task ->
                TaskDetailActivity.startUI(requireContext(), task.id)
            },
            onCheckedChange = { task, isChecked ->
                TaskRepository.updateTask(task.copy(isCompleted = isChecked))
                loadTasks()
            }
        )
        binding.taskRecyclerView.adapter = taskAdapter
    }

    override fun onResume() {
        super.onResume()
        // 从编辑页返回时会走到这里 → 列表自动刷新
        loadTasks()
    }

    private fun loadTasks() {
        val tasks = TaskRepository.getTasks()
        taskAdapter.submitList(tasks)
        binding.emptyTextView.visibility =
            if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null      // ⚠️ Fragment 必须置空，否则内存泄漏
    }
}
