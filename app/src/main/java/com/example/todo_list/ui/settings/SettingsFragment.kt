package com.example.todo_list.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.todo_list.R
import com.example.todo_list.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    companion object {
        // 存储的 XML 文件名：app_settings.xml
        private const val SP_NAME = "app_settings"

        // 键名：相当于 Map 里的 key
        private const val KEY_NICKNAME = "nickname"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // ---------- SharedPreferences 对象 ----------
    // 懒加载：第一次用到时才创建。
    // MODE_PRIVATE = 只有本 App 能读写这个文件
    private val sharedPrefs by lazy {
        requireContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 所有"绑定数据 / 设置监听"的活都在这里做，
     * 因为执行到这里时，布局里的控件已经创建好了。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ===== 1. 进入页面：把已保存的昵称读出来显示 =====
        // 第二个参数 "" 是默认值：从没存过时，返回空字符串
        val savedName = sharedPrefs.getString(KEY_NICKNAME, "") ?: ""
        binding.edtNickname.setText(savedName)
        updateWelcome(savedName)

        // ===== 2. 保存按钮 =====
        binding.btnSaveNickname.setOnClickListener {
            saveNickname()
        }
    }

    /**
     * 保存昵称：取值 → 校验 → 写入 → 提示 → 刷新欢迎语
     */
    private fun saveNickname() {
        // ① 取输入框内容，trim() 去掉首尾空格
        val name = binding.edtNickname.text.toString().trim()

        // ② 校验：空就提示并中断（return = 不再往下执行）
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_nickname_required, Toast.LENGTH_SHORT)
                .show()
            return
        }

        // ③ 写入：edit() 开启编辑 → putString 放值 → apply() 提交
        //    apply() 是异步写到磁盘的，不会卡住界面
        sharedPrefs.edit()
            .putString(KEY_NICKNAME, name)
            .apply()

        // ④ 提示用户
        Toast.makeText(requireContext(), R.string.msg_nickname_saved, Toast.LENGTH_SHORT).show()

        // ⑤ 立刻更新欢迎语，不用等下次进页面
        updateWelcome(name)
    }

    /**
     * 更新欢迎语。
     * settings_welcome 里写了 %1$s 占位符，getString 第二个参数会把名字填进去。
     */
    private fun updateWelcome(name: String) {
        binding.txvWelcome.text = if (name.isBlank()) {
            getString(R.string.settings_welcome_guest)
        } else {
            getString(R.string.settings_welcome, name)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
