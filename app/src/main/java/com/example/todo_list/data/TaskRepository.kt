package com.example.todo_list.data

import com.example.todo_list.TodoApplication
import kotlinx.coroutines.flow.Flow

object TaskRepository {
    private val taskDao: TaskDao by lazy {
        AppDatabase.getInstance(TodoApplication.context).taskDao()
    }

    fun getTasks(): Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun addTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
}
