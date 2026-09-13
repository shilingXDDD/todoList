package com.example.todo_list.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.FragmentTaskListBinding
import com.example.todo_list.ui.detail.TaskDetailActivity
import com.example.todo_list.ui.edit.TaskEditActivity
import kotlinx.coroutines.launch

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
        observeTasks()

        binding.addTaskFab.setOnClickListener {
            // 不传 ID → 新增模式
            TaskEditActivity.startUI(requireContext())
        }
    }

    /**
     * 用 Flow 持续收集数据：数据库一变，这里就会收到新列表。
     * 所以增删改之后不需要手动刷新——这就是"自动刷新"那 1 分的实现。
     *
     * repeatOnLifecycle(STARTED) 的作用：
     *   页面可见  → 开始收集
     *   页面不可见 → 自动停止（省资源，也避免 View 销毁后还更新 UI 而崩溃）
     *   页面回来   → 自动恢复
     */
    private fun observeTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TaskRepository.getTasks().collect { tasks ->
                    taskAdapter.submitList(tasks)
                    binding.emptyTextView.visibility =
                        if (tasks.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onItemClick = { task ->
                TaskDetailActivity.startUI(requireContext(), task.id)
            },
            onCheckedChange = { task, isChecked ->
                // updateTask 是 suspend 函数，必须在协程里调用
                // 写完后不用手动刷新，Flow 会自动推送新列表
                viewLifecycleOwner.lifecycleScope.launch {
                    TaskRepository.updateTask(task.copy(isCompleted = isChecked))
                }
            }
        )
        binding.taskRecyclerView.adapter = taskAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null      // ⚠️ Fragment 必须置空，否则内存泄漏
    }
}
