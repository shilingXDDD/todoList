package com.example.todo_list.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {

    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun format(timestamp: Long): String = formatter.format(Date(timestamp))
}
