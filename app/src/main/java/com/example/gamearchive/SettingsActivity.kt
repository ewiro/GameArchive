package com.example.gamearchive

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 适配全面屏，背景色铺满状态栏
        val rootLayout = findViewById<View>(R.id.settings_root)
        val appBar = findViewById<View>(R.id.settings_app_bar)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 1. 只给 AppBar 加顶部 Padding，这样标题不会被遮挡，但背景色会延伸上去
            appBar.setPadding(0, systemBars.top, 0, 0)

            // 2. 给根布局加底部 Padding，防止退出按钮被小白条遮挡
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)

            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val rgTheme = findViewById<RadioGroup>(R.id.radioGroupTheme)
        val switchPureBlack = findViewById<SwitchCompat>(R.id.switchPureBlack)
        val switchGroup = findViewById<SwitchCompat>(R.id.switchGroup)
        val switchGroupRecent = findViewById<SwitchCompat>(R.id.switchGroupRecent)
        val rgSort = findViewById<RadioGroup>(R.id.rgSort)

        // 绑定个人资料设置控件
        val switchShowProfile = findViewById<SwitchCompat>(R.id.switchShowProfile)
        val llProfileInputs = findViewById<View>(R.id.llProfileInputs)

        val etAvatarUrl = findViewById<TextInputEditText>(R.id.etAvatarUrl)
        val etBgUrl = findViewById<TextInputEditText>(R.id.etBgUrl)
        val etFrameUrl = findViewById<TextInputEditText>(R.id.etFrameUrl)
        val btnSaveProfile = findViewById<View>(R.id.btnSaveProfile)

        when (ThemeUtils.getThemeMode(this)) {
            0 -> findViewById<RadioButton>(R.id.rbLight).isChecked = true
            1 -> findViewById<RadioButton>(R.id.rbDark).isChecked = true
            2 -> findViewById<RadioButton>(R.id.rbAuto).isChecked = true
        }

        // 语言设置
        val rgLanguage = findViewById<RadioGroup>(R.id.radioGroupLanguage)
        when (ThemeUtils.getLanguage(this)) {
            LocaleHelper.LANG_CHINESE -> findViewById<RadioButton>(R.id.rbLangChinese).isChecked = true
            LocaleHelper.LANG_ENGLISH -> findViewById<RadioButton>(R.id.rbLangEnglish).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbLangSystem).isChecked = true
        }
        rgLanguage.setOnCheckedChangeListener { _, id ->
            val lang = when (id) {
                R.id.rbLangChinese -> LocaleHelper.LANG_CHINESE
                R.id.rbLangEnglish -> LocaleHelper.LANG_ENGLISH
                else -> LocaleHelper.LANG_FOLLOW_SYSTEM
            }
            ThemeUtils.saveLanguage(this, lang)
            reload()
        }

        switchPureBlack.isEnabled = true
        switchPureBlack.isChecked = ThemeUtils.isPureBlackEnabled(this)

        switchGroup.isChecked = ThemeUtils.isGroupingEnabled(this)
        switchGroupRecent.isChecked = ThemeUtils.isGroupRecentEnabled(this)
        switchGroupRecent.isEnabled = switchGroup.isChecked

        if (ThemeUtils.getSortMode(this) == 0) {
            findViewById<RadioButton>(R.id.rbSortTime).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.rbSortName).isChecked = true
        }

        etAvatarUrl.setText(UserPrefs.getCustomAvatarUrl(this))
        etBgUrl.setText(UserPrefs.getCustomBgUrl(this))
        etFrameUrl.setText(UserPrefs.getCustomFrameUrl(this))

        // 资料卡总开关：决定输入框区域是否展开
        val isProfileShown = UserPrefs.isShowProfile(this)
        switchShowProfile.isChecked = isProfileShown
        llProfileInputs.visibility = if (isProfileShown) View.VISIBLE else View.GONE

        switchShowProfile.setOnCheckedChangeListener { _, isChecked ->
            UserPrefs.saveShowProfile(this, isChecked)
            llProfileInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
            ThemeUtils.isChanged = true
        }

        // 保存链接按钮
        btnSaveProfile.setOnClickListener {
            val avatar = etAvatarUrl.text.toString()
            val bg = etBgUrl.text.toString()
            val frame = etFrameUrl.text.toString()

            UserPrefs.saveCustomAvatarUrl(this, avatar)
            UserPrefs.saveCustomBgUrl(this, bg)
            UserPrefs.saveCustomFrameUrl(this, frame)

            android.widget.Toast.makeText(this, R.string.settings_profile_saved, android.widget.Toast.LENGTH_SHORT).show()
            ThemeUtils.isChanged = true
        }

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            UserPrefs.logout(context = this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        rgTheme.setOnCheckedChangeListener { _, id ->
            ThemeUtils.saveThemeMode(this, if (id == R.id.rbLight) 0 else if (id == R.id.rbDark) 1 else 2)
            reload()
        }

        switchPureBlack.setOnCheckedChangeListener { _, c ->
            ThemeUtils.savePureBlack(this, c)
            reload()
        }

        switchGroup.setOnCheckedChangeListener { _, c ->
            ThemeUtils.saveGrouping(this, c)
            switchGroupRecent.isEnabled = c
        }

        switchGroupRecent.setOnCheckedChangeListener { _, c -> ThemeUtils.saveGroupRecent(this, c) }

        rgSort.setOnCheckedChangeListener { _, id ->
            ThemeUtils.saveSortMode(this, if (id == R.id.rbSortTime) 0 else 1)
        }
    }

    private fun reload() {
        finish()
        overridePendingTransition(0, 0)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}