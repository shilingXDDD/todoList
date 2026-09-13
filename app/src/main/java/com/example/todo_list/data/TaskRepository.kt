package com.example.todo_list.data

object TaskRepository {

    private val tasks = mutableListOf(
        Task(1, "完成 Android 课设", "包含 Room、Service、广播三个模块", false),
        Task(2, "复习 Fragment 生命周期", "onCreateView 与 onViewCreated 的区别", true),
        Task(3, "整理第一步笔记", "", false)
    )

    private var nextId = 4L

    /** 返回副本，避免外部直接改动内部列表 */
    fun getTasks(): List<Task> = tasks.toList()

    fun getTaskById(id: Long): Task? = tasks.find { it.id == id }

    fun addTask(task: Task): Long {
        val newTask = task.copy(id = nextId++)
        tasks.add(0, newTask)          // 加到最前面，用户能立刻看到
        return newTask.id
    }

    fun updateTask(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            tasks[index] = task
        }
    }

    fun deleteTask(task: Task) {
        tasks.removeIf { it.id == task.id }
    }
}
