package com.example.todo_list.data

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false
)