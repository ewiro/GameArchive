package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)

        setContent {
            MiuixThemeForApp {
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
    val statusBarDp = statusBarHeightDp()

    // ── 外观设置状态 ──
    var themeMode by remember { mutableIntStateOf(ThemeUtils.getThemeMode(context)) }
    var language by remember { mutableIntStateOf(ThemeUtils.getLanguage(context)) }

    // ── 个人资料状态 ──
    var showProfile by remember { mutableStateOf(UserPrefs.isShowProfile(context)) }
    var avatarUrl by remember { mutableStateOf(UserPrefs.getCustomAvatarUrl(context)) }
    var bgUrl by remember { mutableStateOf(UserPrefs.getCustomBgUrl(context)) }
    var frameUrl by remember { mutableStateOf(UserPrefs.getCustomFrameUrl(context)) }

    // ── 库存设置状态 ──
    var grouping by remember { mutableStateOf(ThemeUtils.isGroupingEnabled(context)) }
    var sortMode by remember { mutableIntStateOf(ThemeUtils.getSortMode(context)) }
    var showSpecials by remember { mutableStateOf(ThemeUtils.isSpecialsEnabled(context)) }

    // ── 标签管理状态 ──
    var showTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var tagListRefresh by remember { mutableIntStateOf(0) }
    val allTags = remember(tagListRefresh) { GameTags.getAllTags(context) }

    // ── 导出/导入文件选择器 ──
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExportJson ?: return@rememberLauncherForActivityResult
        pendingExportJson = null
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, R.string.settings_export_ok, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, R.string.settings_import_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                if (DataBackup.importFromJson(context, json)) {
                    Toast.makeText(context, R.string.settings_import_ok, Toast.LENGTH_SHORT).show()
                    onRecreate()
                } else {
                    Toast.makeText(context, R.string.settings_import_fail, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, R.string.settings_import_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
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
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                    Text(
                        text = context.getString(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextTitle.sp,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 深色模式
                    Text(
                        text = context.getString(R.string.settings_dark_mode),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = DesignTokens.SpaceHuge),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_follow_system),
                            selected = themeMode == 2,
                            onClick = { themeMode = 2; ThemeUtils.saveThemeMode(context, 2); onRecreate() },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_light),
                            selected = themeMode == 0,
                            onClick = { themeMode = 0; ThemeUtils.saveThemeMode(context, 0); onRecreate() },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_dark),
                            selected = themeMode == 1,
                            onClick = { themeMode = 1; ThemeUtils.saveThemeMode(context, 1); onRecreate() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DividerLine()

                    // 语言设置
                    Text(
                        text = context.getString(R.string.settings_language),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = DesignTokens.SpaceHuge),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_lang_follow_system),
                            selected = language == LocaleHelper.LANG_FOLLOW_SYSTEM,
                            onClick = { language = LocaleHelper.LANG_FOLLOW_SYSTEM; ThemeUtils.saveLanguage(context, language); onRecreate() },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_lang_chinese),
                            selected = language == LocaleHelper.LANG_CHINESE,
                            onClick = { language = LocaleHelper.LANG_CHINESE; ThemeUtils.saveLanguage(context, language); onRecreate() },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeRadioButton(
                            label = context.getString(R.string.settings_lang_english),
                            selected = language == LocaleHelper.LANG_ENGLISH,
                            onClick = { language = LocaleHelper.LANG_ENGLISH; ThemeUtils.saveLanguage(context, language); onRecreate() },
                            modifier = Modifier.weight(1f)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
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

                    AnimatedVisibility(
                        visible = showProfile,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = DesignTokens.AnimDuration),
                            expandFrom = Alignment.Top
                        ) + fadeIn(animationSpec = tween(durationMillis = DesignTokens.AnimDuration)),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = DesignTokens.AnimDuration),
                            shrinkTowards = Alignment.Top
                        ) + fadeOut(animationSpec = tween(durationMillis = DesignTokens.AnimDuration))
                    ) {
                        Column {
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

                            // 保存按钮 — 浅色蓝底 / 深色灰底
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(DesignTokens.ButtonHeight)
                                        .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                        .background(buttonBgColor())
                                        .clickable {
                                            UserPrefs.saveCustomAvatarUrl(context, avatarUrl)
                                            UserPrefs.saveCustomBgUrl(context, bgUrl)
                                            UserPrefs.saveCustomFrameUrl(context, frameUrl)
                                            Toast.makeText(context, R.string.settings_profile_saved, Toast.LENGTH_SHORT).show()
                                            ThemeUtils.isChanged = true
                                        }
                                        .padding(horizontal = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = context.getString(R.string.settings_save_profile),
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = context.getString(R.string.settings_group_status),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                    Spacer(Modifier.height(10.dp))

                    SwitchRow(
                        label = context.getString(R.string.settings_show_specials),
                        checked = showSpecials,
                        modifier = Modifier.padding(start = 24.dp),
                        onCheckedChange = {
                            showSpecials = it
                            ThemeUtils.saveShowSpecials(context, it)
                        }
                    )

                    Spacer(Modifier.height(DesignTokens.SpaceLg))
                    DividerLine()
                    Spacer(Modifier.height(DesignTokens.SpaceLg))

                    SwitchRow(
                        label = context.getString(R.string.settings_grouping),
                        checked = grouping,
                        modifier = Modifier.padding(start = 24.dp),
                        onCheckedChange = {
                            grouping = it
                            ThemeUtils.saveGrouping(context, it)
                        }
                    )

                    Spacer(Modifier.height(DesignTokens.SpaceLg))
                    DividerLine()
                    Spacer(Modifier.height(DesignTokens.SpaceLg))

                    Text(
                        text = context.getString(R.string.settings_sort_mode),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = DesignTokens.SpaceHuge).clickable { sortMode = 0; ThemeUtils.saveSortMode(context, 0) }.padding(vertical = 6.dp)) {
                        SelectableIndicator(selected = sortMode == 0)
                        Spacer(Modifier.width(6.dp))
                        Text(text = context.getString(R.string.settings_sort_playtime), fontSize = DesignTokens.TextBody1.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = DesignTokens.SpaceHuge).clickable { sortMode = 1; ThemeUtils.saveSortMode(context, 1) }.padding(vertical = 6.dp)) {
                        SelectableIndicator(selected = sortMode == 1)
                        Spacer(Modifier.width(6.dp))
                        Text(text = context.getString(R.string.settings_sort_name), fontSize = DesignTokens.TextBody1.sp)
                    }
                }
            }
        }

        // ── 标签管理 ──
        item("tags_header") {
            SectionHeader(text = context.getString(R.string.tag_manage))
        }
        item("tags_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${context.getString(R.string.tag_manage)} (${allTags.size})",
                        fontSize = DesignTokens.TextBody1.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(DesignTokens.ButtonHeight)
                                .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                .background(buttonBgColor())
                                .clickable { showTagDialog = true }
                                .padding(horizontal = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = context.getString(R.string.tag_manage),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── 数据管理 ──
        item("data_header") {
            SectionHeader(text = context.getString(R.string.settings_data))
        }

        item("data_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 导出按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DesignTokens.ButtonHeight)
                            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                            .background(buttonBgColor())
                            .clickable {
                                pendingExportJson = DataBackup.exportToJson(context)
                                exportLauncher.launch("gamearchive_backup.json")
                            }
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.settings_export),
                            fontSize = DesignTokens.TextBody1.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(DesignTokens.SpaceLg))

                    // 导入按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DesignTokens.ButtonHeight)
                            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                            .background(buttonBgColor(lightColor = Color(0xFF5C6BC0)))
                            .clickable {
                                importLauncher.launch(arrayOf("application/json"))
                            }
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.settings_import),
                            fontSize = DesignTokens.TextBody1.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                }
            }
        }

        // ── 退出登录 ──
        item("logout") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(DesignTokens.ButtonHeight)
                        .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                        .background(buttonBgColor(lightColor = DesignTokens.ErrorRed))
                        .clickable {
                            UserPrefs.logout(context)
                            val intent = Intent(context, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                            (context as? android.app.Activity)?.finish()
                        }
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.settings_logout),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

        // ── 标签管理弹窗 ──
        if (showTagDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DesignTokens.ScrimDark)
                    .clickable { showTagDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .clickable(enabled = false, onClick = {})
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = context.getString(R.string.tag_manage),
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextSubtitle.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        // 现有标签列表
                        if (allTags.isEmpty()) {
                            Text(
                                text = context.getString(R.string.general_no_data),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint),
                                fontSize = DesignTokens.TextBody1.sp
                            )
                        } else {
                            allTags.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = DesignTokens.TextBody1.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "✕",
                                        fontSize = DesignTokens.TextSubtitle.sp,
                                        color = DesignTokens.ErrorRed,
                                        modifier = Modifier
                                            .clickable {
                                                GameTags.deleteTag(context, tag)
                                                tagListRefresh++
                                            }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // 新建标签输入
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = newTagName,
                                onValueChange = { newTagName = it },
                                modifier = Modifier.weight(1f),
                                label = context.getString(R.string.tag_hint),
                                useLabelAsPlaceholder = true,
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .height(DesignTokens.ButtonHeightSmall)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                    .background(if (newTagName.trim().isNotEmpty()) buttonBgColor() else buttonBgColor().copy(alpha = DesignTokens.OpacityDisabled))
                                    .then(if (newTagName.trim().isNotEmpty()) Modifier.clickable {
                                        val trimmed = newTagName.trim()
                                        if (trimmed.isNotEmpty() && !allTags.contains(trimmed)) {
                                            GameTags.addTag(context, trimmed)
                                            newTagName = ""
                                            tagListRefresh++
                                        }
                                    } else Modifier)
                                    .padding(horizontal = DesignTokens.SpaceXl, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = context.getString(R.string.tag_new), fontSize = DesignTokens.TextBody2.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DesignTokens.ButtonHeightSmall)
                                .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                .background(buttonBgColor())
                                .clickable { showTagDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = context.getString(R.string.settings_save_profile), color = Color.White, fontWeight = FontWeight.Bold, fontSize = DesignTokens.TextBody1.sp)
                        }
                    }
                }
            }
        }
    } // close Surface
}

// ── 复用组件 ──

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = DesignTokens.AccentBlue,
        fontWeight = FontWeight.Bold,
        fontSize = DesignTokens.TextBody1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DesignTokens.DividerHeight)
            .padding(vertical = 12.dp)
            .background(MiuixTheme.colorScheme.outline)
    )
}

@Composable
private fun ThemeRadioButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        SelectableIndicator(selected = selected)
        Spacer(Modifier.width(4.dp))
        Text(text = label, fontSize = DesignTokens.TextBody2.sp)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { if (enabled) onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectableIndicator(selected = checked, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontSize = DesignTokens.TextBody1.sp)
    }
}

@Composable
private fun LabeledTextField(label: String, value: String, onValueChange: (String) -> Unit, hint: String) {
    Column {
        Text(text = label, fontSize = DesignTokens.TextBody2.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
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
