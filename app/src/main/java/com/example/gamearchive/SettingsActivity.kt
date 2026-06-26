package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class SettingsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)

        val colorSchemeMode = when (ThemeUtils.getThemeMode(this)) {
            0 -> ColorSchemeMode.Light
            1 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }

        setContent {
            MiuixTheme(controller = ThemeController(colorSchemeMode = colorSchemeMode)) {
                SettingsScreen(
                    onBack = { finish() },
                    onRecreate = {
                        finish()
                        overridePendingTransition(0, 0)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onRecreate: () -> Unit) {
    val context = LocalContext.current

    // 状态栏高度
    val statusBarHeight = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val statusBarDp = with(androidx.compose.ui.platform.LocalDensity.current) { statusBarHeight.toDp() }

    // ── 外观设置状态 ──
    var themeMode by remember { mutableIntStateOf(ThemeUtils.getThemeMode(context)) }
    var pureBlack by remember { mutableStateOf(ThemeUtils.isPureBlackEnabled(context)) }
    var language by remember { mutableIntStateOf(ThemeUtils.getLanguage(context)) }

    // ── 个人资料状态 ──
    var showProfile by remember { mutableStateOf(UserPrefs.isShowProfile(context)) }
    var avatarUrl by remember { mutableStateOf(UserPrefs.getCustomAvatarUrl(context)) }
    var bgUrl by remember { mutableStateOf(UserPrefs.getCustomBgUrl(context)) }
    var frameUrl by remember { mutableStateOf(UserPrefs.getCustomFrameUrl(context)) }

    // ── 库存设置状态 ──
    var grouping by remember { mutableStateOf(ThemeUtils.isGroupingEnabled(context)) }
    var groupingRecent by remember { mutableStateOf(ThemeUtils.isGroupRecentEnabled(context)) }
    var sortMode by remember { mutableIntStateOf(ThemeUtils.getSortMode(context)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // ── 顶栏 ──
        item("topbar") {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp)
                        .padding(top = statusBarDp + 4.dp)
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Image(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = context.getString(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f).padding(end = 48.dp)
                    )
                }
            }
        }

        // ── 外观设置 ──
        item("appearance_header") {
            SectionHeader(text = context.getString(R.string.settings_appearance))
        }

        item("appearance_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 深色模式
                    Text(
                        text = context.getString(R.string.settings_dark_mode),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_follow_system),
                            selected = themeMode == 2,
                            onClick = {
                                themeMode = 2
                                ThemeUtils.saveThemeMode(context, 2)
                                onRecreate()
                            }
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_light),
                            selected = themeMode == 0,
                            onClick = {
                                themeMode = 0
                                ThemeUtils.saveThemeMode(context, 0)
                                onRecreate()
                            }
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_dark),
                            selected = themeMode == 1,
                            onClick = {
                                themeMode = 1
                                ThemeUtils.saveThemeMode(context, 1)
                                onRecreate()
                            }
                        )
                    }

                    DividerLine()

                    // 纯黑模式
                    SwitchRow(
                        label = context.getString(R.string.settings_pure_black),
                        checked = pureBlack,
                        onCheckedChange = {
                            pureBlack = it
                            ThemeUtils.savePureBlack(context, it)
                            onRecreate()
                        }
                    )

                    DividerLine()

                    // 语言设置
                    Text(
                        text = context.getString(R.string.settings_language),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_lang_follow_system),
                            selected = language == LocaleHelper.LANG_FOLLOW_SYSTEM,
                            onClick = {
                                language = LocaleHelper.LANG_FOLLOW_SYSTEM
                                ThemeUtils.saveLanguage(context, language)
                                onRecreate()
                            }
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_lang_chinese),
                            selected = language == LocaleHelper.LANG_CHINESE,
                            onClick = {
                                language = LocaleHelper.LANG_CHINESE
                                ThemeUtils.saveLanguage(context, language)
                                onRecreate()
                            }
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_lang_english),
                            selected = language == LocaleHelper.LANG_ENGLISH,
                            onClick = {
                                language = LocaleHelper.LANG_ENGLISH
                                ThemeUtils.saveLanguage(context, language)
                                onRecreate()
                            }
                        )
                    }
                }
            }
        }

        // ── 个人资料设置 ──
        item("profile_header") {
            SectionHeader(text = context.getString(R.string.settings_profile))
        }

        item("profile_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SwitchRow(
                        label = context.getString(R.string.settings_show_profile),
                        checked = showProfile,
                        onCheckedChange = {
                            showProfile = it
                            UserPrefs.saveShowProfile(context, it)
                            ThemeUtils.isChanged = true
                        }
                    )

                    if (showProfile) {
                        DividerLine()

                        // 头像 URL
                        LabeledTextField(
                            label = context.getString(R.string.settings_avatar_url),
                            value = avatarUrl,
                            onValueChange = { avatarUrl = it },
                            hint = context.getString(R.string.settings_avatar_hint)
                        )
                        Spacer(Modifier.height(12.dp))

                        // 背景 URL
                        LabeledTextField(
                            label = context.getString(R.string.settings_bg_url),
                            value = bgUrl,
                            onValueChange = { bgUrl = it },
                            hint = context.getString(R.string.settings_bg_hint)
                        )
                        Spacer(Modifier.height(12.dp))

                        // 挂件 URL
                        LabeledTextField(
                            label = context.getString(R.string.settings_frame_url),
                            value = frameUrl,
                            onValueChange = { frameUrl = it },
                            hint = context.getString(R.string.settings_frame_hint)
                        )
                        Spacer(Modifier.height(16.dp))

                        // 保存按钮
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = {
                                    UserPrefs.saveCustomAvatarUrl(context, avatarUrl)
                                    UserPrefs.saveCustomBgUrl(context, bgUrl)
                                    UserPrefs.saveCustomFrameUrl(context, frameUrl)
                                    Toast.makeText(context, R.string.settings_profile_saved, Toast.LENGTH_SHORT).show()
                                    ThemeUtils.isChanged = true
                                },
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(text = context.getString(R.string.settings_save_profile))
                            }
                        }
                    }
                }
            }
        }

        // ── 库存列表设置 ──
        item("library_header") {
            SectionHeader(text = context.getString(R.string.settings_library))
        }

        item("library_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SwitchRow(
                        label = context.getString(R.string.settings_grouping),
                        checked = grouping,
                        onCheckedChange = {
                            grouping = it
                            ThemeUtils.saveGrouping(context, it)
                        }
                    )

                    SwitchRow(
                        label = context.getString(R.string.settings_group_recent),
                        checked = groupingRecent,
                        enabled = grouping,
                        onCheckedChange = {
                            groupingRecent = it
                            ThemeUtils.saveGroupRecent(context, it)
                        }
                    )

                    DividerLine()

                    Text(
                        text = context.getString(R.string.settings_sort_mode),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { sortMode = 0; ThemeUtils.saveSortMode(context, 0) }.padding(vertical = 4.dp)) {
                        RadioButton(selected = sortMode == 0, onClick = { sortMode = 0; ThemeUtils.saveSortMode(context, 0) })
                        Spacer(Modifier.width(8.dp))
                        Text(text = context.getString(R.string.settings_sort_playtime))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { sortMode = 1; ThemeUtils.saveSortMode(context, 1) }.padding(vertical = 4.dp)) {
                        RadioButton(selected = sortMode == 1, onClick = { sortMode = 1; ThemeUtils.saveSortMode(context, 1) })
                        Spacer(Modifier.width(8.dp))
                        Text(text = context.getString(R.string.settings_sort_name))
                    }
                }
            }
        }

        // ── 退出登录 ──
        item("logout") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                Button(
                    onClick = {
                        UserPrefs.logout(context)
                        val intent = Intent(context, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                        (context as? android.app.Activity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        text = context.getString(R.string.settings_logout),
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }
}

// ── 复用组件 ──

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = Color(0xFF3482FF),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 12.dp)
            .background(Color.Gray.copy(alpha = 0.2f))
    )
}

@Composable
private fun ThemeRadioButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(text = label, fontSize = 13.sp)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = { if (enabled) onCheckedChange(!checked) }
        )
    }
}

@Composable
private fun LabeledTextField(label: String, value: String, onValueChange: (String) -> Unit, hint: String) {
    Column {
        Text(text = label, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = hint,
            useLabelAsPlaceholder = true,
            singleLine = true
        )
    }
}
