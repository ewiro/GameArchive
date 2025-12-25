package com.example.gamearchive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用应用主题配置
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查用户是否已登录，如果已登录直接跳转主页
        if (UserPrefs.isLoggedIn(this)) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        // 处理系统栏边距，防止内容被状态栏或导航栏遮挡
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etSteamId = findViewById<TextInputEditText>(R.id.etSteamId)
        val etApiKey = findViewById<TextInputEditText>(R.id.etApiKey)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvHelp = findViewById<TextView>(R.id.tvHelp)

        // 处理登录按钮点击事件
        btnLogin.setOnClickListener {
            val steamId = etSteamId.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()

            // 校验输入内容是否为空
            if (steamId.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存用户凭证到本地存储
            UserPrefs.saveCredentials(this, apiKey, steamId)
            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
            startMainActivity()
        }

        // 跳转浏览器打开Steam API Key申请页面
        tvHelp.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/dev/apikey"))
            startActivity(intent)
        }
    }

    // 启动主页面并关闭当前登录页面
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}