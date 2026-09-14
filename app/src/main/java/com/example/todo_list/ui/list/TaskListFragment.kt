package com.example.todo_list.ui.list

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.todo_list.R
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.FragmentTaskListBinding
import com.example.todo_list.service.TaskSyncService
import com.example.todo_list.ui.detail.TaskDetailActivity
import com.example.todo_list.ui.edit.TaskEditActivity
import kotlinx.coroutines.launch

class TaskListFragment : Fragment() {


    //防重复点击标志位
    private var isSyncing = false

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private lateinit var taskAdapter: TaskAdapter

    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isSyncing = false
            Toast.makeText(requireContext(), R.string.msg_sync_finished, Toast.LENGTH_SHORT).show()
            showSyncNotification()
        }
    }

    private fun showSyncNotification() {
        val context = context ?: return          // Fragment 已解绑就不发

        // Android 13+ 没权限就不发
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_SYNC, getString(R.string.channel_name_sync),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setSmallIcon(R.drawable.ic_stat_reminder)     // ← 用任务二建的单色图标
            .setContentTitle(getString(R.string.msg_sync_finished))
            .setContentText(getString(R.string.msg_sync_finished_content))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
    }

    companion object {
        private const val CHANNEL_ID_SYNC = "task_sync"
        private const val NOTIFICATION_ID_SYNC = 1001
    }

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

        binding.btnSync.setOnClickListener {
            startSync()
        }
    }

    private fun startSync() {
        if (isSyncing) return           // 同步中再点，直接忽略
        isSyncing = true
        requireContext().startService(
            Intent(requireContext(), TaskSyncService::class.java)
        )
    }

    override fun onStart() {
        super.onStart()
        // ⚠️ Android 13+ 必须指定 RECEIVER_NOT_EXPORTED 或 RECEIVER_EXPORTED
        //    不写会直接崩溃
        val filter = IntentFilter(TaskSyncService.ACTION_SYNC_FINISHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(syncReceiver, filter)
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

    override fun onStop() {
        super.onStop()
        // ⚠️ 必须注销，否则报 IntentReceiverLeaked（广播接收器泄漏）
        requireContext().unregisterReceiver(syncReceiver)
    }
}
