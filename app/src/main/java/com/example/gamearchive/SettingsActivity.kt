package com.example.gamearchive

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    private var currentSelectedColor: Int = 0

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
        val switchDynamic = findViewById<MaterialSwitch>(R.id.switchDynamic)
        val switchPureBlack = findViewById<MaterialSwitch>(R.id.switchPureBlack)
        val gridColors = findViewById<GridLayout>(R.id.gridColors)
        val switchGroup = findViewById<MaterialSwitch>(R.id.switchGroup)
        val switchGroupRecent = findViewById<MaterialSwitch>(R.id.switchGroupRecent)
        val rgSort = findViewById<RadioGroup>(R.id.rgSort)

        // 绑定个人资料设置控件
        val switchShowProfile = findViewById<MaterialSwitch>(R.id.switchShowProfile) // 🔥 新开关
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

        val isDynamic = ThemeUtils.isDynamicColorEnabled(this)
        switchDynamic.isChecked = isDynamic

        switchPureBlack.isEnabled = true
        switchPureBlack.isChecked = ThemeUtils.isPureBlackEnabled(this)

        val prefs = getSharedPreferences("app_theme_prefs", MODE_PRIVATE)
        currentSelectedColor = prefs.getInt("custom_color", ThemeUtils.COLOR_PALETTE[5])
        renderPaletteGrid(gridColors, isDynamic)

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

        // 读取当前开关状态
        val isProfileShown = UserPrefs.isShowProfile(this)
        switchShowProfile.isChecked = isProfileShown
        // 根据状态决定是否显示输入框
        llProfileInputs.visibility = if (isProfileShown) View.VISIBLE else View.GONE

        // 回显文本
        etAvatarUrl.setText(UserPrefs.getCustomAvatarUrl(this))
        etBgUrl.setText(UserPrefs.getCustomBgUrl(this))
        etFrameUrl.setText(UserPrefs.getCustomFrameUrl(this))

        // 🔥 监听开关变化
        switchShowProfile.setOnCheckedChangeListener { _, isChecked ->
            UserPrefs.saveShowProfile(this, isChecked) // 保存状态
            llProfileInputs.visibility = if (isChecked) View.VISIBLE else View.GONE // 切换显示
            ThemeUtils.isChanged = true // 通知主页刷新
        }


        // 保存链接按钮
        btnSaveProfile.setOnClickListener {
            val avatar = etAvatarUrl.text.toString()
            val bg = etBgUrl.text.toString()
            val frame = etFrameUrl.text.toString()

            UserPrefs.saveCustomAvatarUrl(this, avatar)
            UserPrefs.saveCustomBgUrl(this, bg)
            UserPrefs.saveCustomFrameUrl(this, frame)

            android.widget.Toast.makeText(this, "个人资料设置已更新", android.widget.Toast.LENGTH_SHORT).show()
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

        switchDynamic.setOnCheckedChangeListener { _, c ->
            ThemeUtils.saveDynamicColor(this, c)
            renderPaletteGrid(gridColors, c)
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

    private fun renderPaletteGrid(grid: GridLayout, isDynamic: Boolean) {
        grid.removeAllViews()

        if (isDynamic) {
            grid.visibility = View.GONE
            return
        }
        grid.visibility = View.VISIBLE

        val screenWidth = resources.displayMetrics.widthPixels
        val availableWidth = screenWidth - dpToPx(64 + 32)
        val itemSize = availableWidth / 5

        for (color in ThemeUtils.COLOR_PALETTE) {
            val card = FrameLayout(this)
            val params = GridLayout.LayoutParams()
            params.width = itemSize
            params.height = itemSize
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            card.layoutParams = params

            val bgDrawable = GradientDrawable()
            bgDrawable.cornerRadius = dpToPx(50).toFloat()
            if (color == currentSelectedColor) {
                bgDrawable.setColor(Color.TRANSPARENT)
                bgDrawable.setStroke(dpToPx(2), color)
            } else {
                bgDrawable.setColor(Color.TRANSPARENT)
            }
            card.background = bgDrawable

            val colorDot = View(this)
            val dotBg = GradientDrawable()
            dotBg.shape = GradientDrawable.OVAL
            dotBg.setColor(color)
            colorDot.background = dotBg

            val dotParams = FrameLayout.LayoutParams(itemSize - dpToPx(12), itemSize - dpToPx(12))
            dotParams.gravity = Gravity.CENTER
            card.addView(colorDot, dotParams)

            card.setOnClickListener {
                ThemeUtils.saveCustomColor(this, color)
                currentSelectedColor = color
                reload()
            }
            grid.addView(card)
        }
    }

    private fun reload() {
        finish()
        overridePendingTransition(0, 0)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}