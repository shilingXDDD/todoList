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
import com.example.todo_list.data.Task
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.FragmentTaskListBinding
import com.example.todo_list.service.TaskSyncService
import com.example.todo_list.ui.detail.TaskDetailActivity
import com.example.todo_list.ui.edit.TaskEditActivity
import kotlinx.coroutines.launch
import java.util.Calendar

class TaskListFragment : Fragment() {


    //防重复点击标志位
    private var isSyncing = false

    private var keyword = ""

    private var latestTasks: List<Task> = emptyList()

    private var currentFilter = DateFilter.ALL

    private enum class DateFilter { ALL, TODAY, OVERDUE }

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

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                keyword = newText.orEmpty()
                applyFilter(latestTasks)      // 复用下面的过滤方法
                return true
            }
        })
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
        setupSearch()
        setupDateFilter()
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
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TaskRepository.getTasks().collect { tasks ->
                    latestTasks = tasks
                    applyFilter(tasks)      // applyFilter 内部会 submitList
                }
            }
        }
    }

    private fun applyFilter(allTasks: List<Task>){
        val now = System.currentTimeMillis()
        var filtered = allTasks

        // ① 先按关键词（true 表示忽略大小写）
        if (keyword.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(keyword, true) }
        }

        // ② 再按日期
        filtered = when (currentFilter) {
            DateFilter.TODAY -> filtered.filter {
                it.dueDate != null && it.dueDate in todayStart()..todayEnd()
            }
            DateFilter.OVERDUE -> filtered.filter {
                // ⚠️ 已完成的不算过期
                !it.isCompleted && it.dueDate != null && it.dueDate < now
            }
            else -> filtered
        }

        taskAdapter.submitList(filtered)

        // 无结果要有明确提示（区分"没任务"和"筛选/搜索没结果"）
        binding.emptyTextView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyTextView.text =
            if (keyword.isBlank() && currentFilter == DateFilter.ALL)
                getString(R.string.empty_task_list)
            else getString(R.string.empty_search_result)
    }

    // ⚠️ 必须用 Calendar 按当天 0 点 / 23:59:59 算，
    //    不能用 24*60*60*1000 加减（跨天边界会错）
    private fun todayStart(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun todayEnd(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun setupDateFilter() {
        // ⚠️ 用 setOnCheckedStateChangeListener，旧的 setOnCheckedChangeListener 已废弃
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipFilterToday   -> DateFilter.TODAY
                R.id.chipFilterOverdue -> DateFilter.OVERDUE
                else                   -> DateFilter.ALL
            }
            applyFilter(latestTasks)
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
        // ⚠️ 用 context ?: return，别用 requireContext()
        //    横竖屏重建时 Fragment 可能已与 Activity 解绑，
        //    requireContext() 会抛 IllegalStateException 导致崩溃
        val ctx = context ?: return
        try {
            ctx.unregisterReceiver(syncReceiver)
        } catch (e: IllegalArgumentException) {
            // 接收器本来就没注册成功（例如 onStart 没走到），忽略即可
        }
    }
}
