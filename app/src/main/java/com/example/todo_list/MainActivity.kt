package com.example.todo_list

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.todo_list.databinding.ActivityMainBinding
import com.example.todo_list.ui.list.TaskListFragment
import com.example.todo_list.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ⚠️ 只在第一次创建时添加 Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, TaskListFragment())
                .commit()
        }

        binding.bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.bottom_navigation_task_list -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, TaskListFragment())
                        .commit()
                }
                R.id.bottom_navigation_settings -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, SettingsFragment())
                        .commit()
                }
            }
            true
        }
    }
}
