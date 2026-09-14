package com.example.todo_list.ui.statistics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.todo_list.R
import com.example.todo_list.data.TaskRepository
import com.example.todo_list.databinding.ActivityStatisticsBinding
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding

    // 缓存最新值：两个 Flow 谁先返回，都能算出正确的未完成数与完成率
    private var total = 0
    private var completed = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_statistics)

        observeStatistics()
    }

    private fun observeStatistics() {
        lifecycleScope.launch {
            // repeatOnLifecycle：页面退到后台自动停止收集，避免无谓刷新
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    TaskRepository.countAll().collect {
                        total = it
                        binding.txvTotal.text = it.toString()
                        refreshDerived()
                    }
                }

                launch {
                    TaskRepository.countCompleted().collect {
                        completed = it
                        binding.txvCompleted.text = it.toString()
                        refreshDerived()
                    }
                }
            }
        }
    }

    /**
     * 未完成数与完成率都由 total / completed 推导，
     * 两个 Flow 谁先到都调用一次，保证显示始终正确。
     */
    private fun refreshDerived() {
        binding.txvPending.text = (total - completed).toString()

        // ⚠️ 空列表时 total 为 0，直接除会得到 NaN，必须兜底
        val rate = if (total == 0) 0 else completed * 100 / total
        binding.txvRate.text = getString(R.string.stats_rate_value, rate)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
