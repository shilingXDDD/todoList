package com.example.todo_list.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val category: String = "默认",
    val priority: Int = 0,    //0无 1低 2中 3高
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val note: String = "",
    val link: String = "",
    val createdAt: Long = System.currentTimeMillis()

)
