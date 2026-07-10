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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.compositionLocalOf

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

private data class DropdownPopupData(
    val options: List<String>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit,
    val triggerRect: Rect
)

private val LocalDropdownHost = compositionLocalOf<(DropdownPopupData?) -> Unit> { {} }

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

    var dropdownPopup by remember { mutableStateOf<DropdownPopupData?>(null) }
    CompositionLocalProvider(LocalDropdownHost provides { dropdownPopup = it }) {
    Box(Modifier.fillMaxSize()) {
    Surface(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // ── 顶栏 ──
        item("topbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarDp + 4.dp, end = 12.dp, bottom = 8.dp),
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
                    fontSize = DesignTokens.TextHeadline.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
                    // 主题样式
                    val themeOptions = listOf(
                        context.getString(R.string.settings_follow_system),
                        context.getString(R.string.settings_light),
                        context.getString(R.string.settings_dark)
                    )
                    val themeIndex = remember(themeMode) {
                        when (themeMode) { 2 -> 0; 0 -> 1; 1 -> 2; else -> 0 }
                    }
                    DropdownSelector(
                        label = context.getString(R.string.settings_dark_mode),
                        options = themeOptions,
                        selectedIndex = themeIndex,
                        onSelect = { idx ->
                            val newMode = when (idx) { 0 -> 2; 1 -> 0; 2 -> 1; else -> 2 }
                            themeMode = newMode; ThemeUtils.saveThemeMode(context, newMode); onRecreate()
                        }
                    )

                    DividerLine()

                    // 语言
                    val langOptions = listOf(
                        context.getString(R.string.settings_lang_follow_system),
                        context.getString(R.string.settings_lang_chinese),
                        context.getString(R.string.settings_lang_english)
                    )
                    val langValues = listOf(LocaleHelper.LANG_FOLLOW_SYSTEM, LocaleHelper.LANG_CHINESE, LocaleHelper.LANG_ENGLISH)
                    val langIndex = remember(language) { langValues.indexOf(language).coerceAtLeast(0) }
                    DropdownSelector(
                        label = context.getString(R.string.settings_language),
                        options = langOptions,
                        selectedIndex = langIndex,
                        onSelect = { idx ->
                            language = langValues[idx]; ThemeUtils.saveLanguage(context, language); onRecreate()
                        }
                    )
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
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
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
                                        .noRippleClickable {
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
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
                    // 分组
                    SwitchRow(
                        label = context.getString(R.string.settings_grouping),
                        checked = grouping,
                        onCheckedChange = {
                            grouping = it
                            ThemeUtils.saveGrouping(context, it)
                        }
                    )

                    DividerLine()

                    // 显示特惠
                    SwitchRow(
                        label = context.getString(R.string.settings_show_specials),
                        checked = showSpecials,
                        onCheckedChange = {
                            showSpecials = it
                            ThemeUtils.saveShowSpecials(context, it)
                        }
                    )

                    DividerLine()

                    // 排序方式
                    val sortOptions = listOf(
                        context.getString(R.string.settings_sort_playtime),
                        context.getString(R.string.settings_sort_name)
                    )
                    DropdownSelector(
                        label = context.getString(R.string.settings_sort_mode),
                        options = sortOptions,
                        selectedIndex = sortMode,
                        onSelect = { idx ->
                            sortMode = idx; ThemeUtils.saveSortMode(context, sortMode)
                        }
                    )
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
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable { showTagDialog = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${context.getString(R.string.tag_manage)} (${allTags.size})",
                            fontSize = DesignTokens.TextSubtitle.sp,
                            fontWeight = systemFontWeight(),
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_arrow_right),
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconLg),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                            )
                        )
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
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
                    // 导出配置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable {
                                pendingExportJson = DataBackup.exportToJson(context)
                                exportLauncher.launch("gamearchive_backup.json")
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.settings_export),
                            fontSize = DesignTokens.TextSubtitle.sp,
                            fontWeight = systemFontWeight(),
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_arrow_right),
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconLg),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                            )
                        )
                    }

                    DividerLine()

                    // 导入配置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable {
                                importLauncher.launch(arrayOf("application/json"))
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.settings_import),
                            fontSize = DesignTokens.TextSubtitle.sp,
                            fontWeight = systemFontWeight(),
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_arrow_right),
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconLg),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                            )
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
                        .noRippleClickable {
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
                    .noRippleClickable { showTagDialog = false },
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
                                            .noRippleClickable {
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
                                    .then(if (newTagName.trim().isNotEmpty()) Modifier.noRippleClickable {
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
                                .noRippleClickable { showTagDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = context.getString(R.string.settings_save_profile), color = Color.White, fontWeight = FontWeight.Bold, fontSize = DesignTokens.TextBody1.sp)
                        }
                    }
                }
            }
        }
    } // close Surface

        // ── 选择器弹窗叠加层 ──
        if (dropdownPopup != null) {
            val popup = dropdownPopup!!
            val density = LocalDensity.current
            val triggerBottomDp = with(density) { (popup.triggerRect.bottom / density.density).dp }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DesignTokens.ScrimDark)
                    .clickable { dropdownPopup = null },
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = triggerBottomDp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    val screenWidthDp = with(density) { (context.resources.displayMetrics.widthPixels / density.density).dp }
                    val triggerRightDp = with(density) { (popup.triggerRect.right / density.density).dp }
                    val paddingEnd = maxOf(0.dp, screenWidthDp - triggerRightDp)
                    Card(
                        modifier = Modifier
                            .padding(end = paddingEnd)
                            .wrapContentWidth(),
                    cornerRadius = DesignTokens.CornerLarge
                ) {
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        popup.options.forEachIndexed { index, option ->
                            val isSelected = index == popup.selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .noRippleClickable {
                                        popup.onSelect(index)
                                        dropdownPopup = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected)
                                        MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                    softWrap = false
                                )
                                Spacer(Modifier.weight(1f))
                                Spacer(Modifier.width(32.dp))
                                Text(
                                    text = "✓",
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected)
                                        MiuixTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    } // close Box
    } // close CompositionLocalProvider
    } // close SettingsScreen

// ── 复用组件 ──

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
        fontWeight = FontWeight.Bold,
        fontSize = DesignTokens.TextBody1.sp,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp)
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
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = DesignTokens.TextSubtitle.sp,
            fontWeight = systemFontWeight(),
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled
        )
    }
}

@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dropdownHost = LocalDropdownHost.current
    var triggerRect by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = modifier) {
        // 触发器行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    triggerRect = coords.positionInWindow().let {
                        Rect(it.x, it.y, it.x + coords.size.width, it.y + coords.size.height)
                    }
                }
                .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                .noRippleClickable {
                    dropdownHost(DropdownPopupData(options, selectedIndex, onSelect, triggerRect))
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = DesignTokens.TextSubtitle.sp,
                fontWeight = systemFontWeight(),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(32.dp))
            Text(
                text = options[selectedIndex],
                fontSize = DesignTokens.TextBody1.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
            )
            Spacer(Modifier.width(4.dp))
            DropdownArrowEndAction(
                actionColor = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
            )
        }
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
