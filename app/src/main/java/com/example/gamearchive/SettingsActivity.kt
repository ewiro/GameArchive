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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.icon.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Suppress("DEPRECATION")
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
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

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
    var customUrlsExpanded by remember { mutableStateOf(false) }

    // ── 库存设置状态 ──
    var showPlaytimeBackground by remember {
        mutableStateOf(ThemeUtils.isPlaytimeBadgeBackgroundEnabled(context))
    }
    var usePlaytimeBadgeTextColor by remember {
        mutableStateOf(ThemeUtils.isPlaytimeBadgeTextColorEnabled(context))
    }
    var grouping by remember { mutableStateOf(ThemeUtils.isGroupingEnabled(context)) }
    var sortMode by remember { mutableIntStateOf(ThemeUtils.getSortMode(context)) }
    var showSpecials by remember { mutableStateOf(ThemeUtils.isSpecialsEnabled(context)) }
    var showBangumi by remember { mutableStateOf(ThemeUtils.isBangumiEnabled(context)) }
    var showActivity by remember { mutableStateOf(ThemeUtils.isActivityEnabled(context)) }

    // ── 标签管理状态 ──
    var showTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var tagListRefresh by remember { mutableIntStateOf(0) }
    val tagData = remember(tagListRefresh) {
        val usageCounts = GameTags.getTagUsageCounts(context)
        val tags = GameTags.getAllTags(context)
            .distinctBy { it.lowercase() }
            .sortedByDescending { usageCounts[it] ?: 0 }
        tags to usageCounts
    }
    val allTags = tagData.first
    val tagUsageCounts = tagData.second

    // ── 账号管理状态 ──
    var showAccountTypeDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var newSteamId by remember { mutableStateOf("") }
    var newApiKey by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf(UserPrefs.getAdditionalAccounts(context)) }
    var steamNamesRevision by remember { mutableIntStateOf(0) }
    var bangumiUserId by remember { mutableIntStateOf(UserPrefs.getBangumiUserId(context)) }
    var bangumiNickname by remember { mutableStateOf(UserPrefs.getBangumiNickname(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bangumiUserId = UserPrefs.getBangumiUserId(context)
                bangumiNickname = UserPrefs.getBangumiNickname(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(accounts) {
        val namesChanged = withContext(Dispatchers.IO) {
            var changed = false
            UserPrefs.getAllAccounts(context).forEach { (steamId, apiKey) ->
                if (UserPrefs.getStoredSteamNickname(context, steamId).isBlank()) {
                    val nickname = runCatching {
                        GameArchiveApp.apiService
                            .getPlayerSummaries(apiKey, steamId)
                            .response.players
                            .firstOrNull()
                            ?.personaname
                            .orEmpty()
                    }.getOrDefault("")
                    if (nickname.isNotBlank()) {
                        UserPrefs.saveSteamNickname(context, steamId, nickname)
                        changed = true
                    }
                }
            }
            changed
        }
        if (namesChanged) steamNamesRevision++
    }

    LaunchedEffect(bangumiUserId) {
        if (bangumiUserId > 0) {
            val token = UserPrefs.getBangumiAccessToken(context)
            if (token.isNotBlank()) {
                val currentUser = withContext(Dispatchers.IO) {
                    runCatching {
                        BangumiAuthSession.execute(context) { it.getCurrentUser() }
                    }.getOrNull()
                }
                if (currentUser != null) {
                    UserPrefs.setBangumiUsername(context, currentUser.username)
                    val displayName = currentUser.nickname.ifBlank { currentUser.username }
                    UserPrefs.setBangumiNickname(context, displayName)
                    bangumiNickname = displayName
                }
            }
        }
    }

    // ── 导出/导入文件选择器 ──
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExportJson ?: return@rememberLauncherForActivityResult
        pendingExportJson = null
        if (uri != null) {
            scope.launch {
                val succeeded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Unable to open output stream")
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (succeeded) R.string.settings_export_ok else R.string.settings_export_fail,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()
                        DataBackup.importFromJson(context, json)
                    }.getOrDefault(false)
                }
                if (imported) {
                    Toast.makeText(context, R.string.settings_import_ok, Toast.LENGTH_SHORT).show()
                    onRecreate()
                } else {
                    Toast.makeText(context, R.string.settings_import_fail, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var obsidianImporting by remember { mutableStateOf(false) }
    var skippedImportNames by remember { mutableStateOf<List<String>>(emptyList()) }
    val obsidianImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            skippedImportNames = emptyList()
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                obsidianImporting = true
                val result = withContext(Dispatchers.IO) {
                    runCatching { ObsidianImporter.importTree(context, uri) }.getOrNull()
                }
                obsidianImporting = false
                if (result == null) {
                    Toast.makeText(
                        context,
                        R.string.settings_obsidian_import_fail,
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (result.mediaNoteCount == 0) {
                    Toast.makeText(
                        context,
                        R.string.settings_obsidian_import_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    tagListRefresh++
                    skippedImportNames = result.skippedNames
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_obsidian_import_result,
                            result.gameCount,
                            result.animeCount,
                            result.recordCount,
                            result.tagCount,
                            result.skippedCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    var hasBangumiProfileBackground by remember {
        mutableStateOf(BangumiProfileBackground.exists(context))
    }
    val bangumiBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    BangumiProfileBackground.save(context, uri)
                }
                if (saved) {
                    hasBangumiProfileBackground = true
                    ThemeUtils.isChanged = true
                }
                Toast.makeText(
                    context,
                    if (saved) {
                        R.string.settings_bangumi_background_saved
                    } else {
                        R.string.settings_bangumi_background_failed
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val openBangumiBackgroundPicker = {
        bangumiBackgroundLauncher.launch(arrayOf("image/*"))
    }

    var dropdownPopup by remember { mutableStateOf<DropdownPopupData?>(null) }
    var dropdownPopupVisible by remember { mutableStateOf(false) }
    val dismissDropdown = {
        dropdownPopupVisible = false
    }
    CompositionLocalProvider(LocalDropdownHost provides { popup ->
        if (popup == null) {
            dismissDropdown()
        } else {
            dropdownPopup = popup
            dropdownPopupVisible = true
        }
    }) {
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
                        imageVector = MiuixIcons.Demibold.Back,
                        contentDescription = context.getString(R.string.general_back),
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

        item("page_visibility_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 10.dp
                    )
                ) {
                    SwitchRow(
                        label = context.getString(R.string.settings_show_specials),
                        checked = showSpecials,
                        onCheckedChange = {
                            showSpecials = it
                            ThemeUtils.saveShowSpecials(context, it)
                        }
                    )

                    DividerLine()

                    // 展示动漫页
                    SwitchRow(
                        label = context.getString(R.string.settings_show_bangumi),
                        checked = showBangumi,
                        onCheckedChange = {
                            showBangumi = it
                            ThemeUtils.saveShowBangumi(context, it)
                        }
                    )

                    DividerLine()

                    SwitchRow(
                        label = context.getString(R.string.settings_show_activity),
                        checked = showActivity,
                        onCheckedChange = {
                            showActivity = it
                            ThemeUtils.saveShowActivity(context, it)
                        }
                    )
                }
            }
        }

        // ── 个人资料设置 ──
        item("profile_header") {
            SectionHeader(text = context.getString(R.string.settings_game_page))
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
                        enter = smoothExpandEnter(),
                        exit = smoothExpandExit()
                    ) {
                        Column {
                            DividerLine()
                            ExpandableSectionTrigger(
                                expanded = customUrlsExpanded,
                                onToggle = {
                                    customUrlsExpanded = !customUrlsExpanded
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(DesignTokens.CornerMedium)),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                arrowColor = MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = DesignTokens.OpacityBody
                                )
                            ) {
                                Text(
                                    text = context.getString(R.string.settings_custom_urls),
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    fontWeight = systemFontWeight(),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            ExpandableSectionContent(expanded = customUrlsExpanded) {
                                Column {
                                    LabeledTextField(
                                        label = context.getString(R.string.settings_avatar_url),
                                        value = avatarUrl,
                                        onValueChange = { avatarUrl = it },
                                        hint = context.getString(R.string.settings_avatar_hint)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    LabeledTextField(
                                        label = context.getString(R.string.settings_bg_url),
                                        value = bgUrl,
                                        onValueChange = { bgUrl = it },
                                        hint = context.getString(R.string.settings_bg_hint)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    LabeledTextField(
                                        label = context.getString(R.string.settings_frame_url),
                                        value = frameUrl,
                                        onValueChange = { frameUrl = it },
                                        hint = context.getString(R.string.settings_frame_hint)
                                    )
                                    Spacer(Modifier.height(16.dp))
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
                                                    Toast.makeText(
                                                        context,
                                                        R.string.settings_profile_saved,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    ThemeUtils.isChanged = true
                                                }
                                                .padding(horizontal = 28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = context.getString(R.string.settings_save_profile),
                                                fontSize = DesignTokens.TextBody1.sp,
                                                color = if (isAppInDarkTheme()) {
                                                    DesignTokens.AccentBlue
                                                } else {
                                                    Color.White
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 游戏列表设置 ──
        item("library_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
                    SwitchRow(
                        label = context.getString(R.string.settings_playtime_background),
                        checked = showPlaytimeBackground,
                        onCheckedChange = {
                            showPlaytimeBackground = it
                            ThemeUtils.savePlaytimeBadgeBackground(context, it)
                        }
                    )

                    AnimatedVisibility(
                        visible = !showPlaytimeBackground,
                        enter = smoothExpandEnter(),
                        exit = smoothExpandExit()
                    ) {
                        Column {
                            DividerLine()
                            SwitchRow(
                                label = context.getString(
                                    R.string.settings_playtime_text_color
                                ),
                                checked = usePlaytimeBadgeTextColor,
                                onCheckedChange = {
                                    usePlaytimeBadgeTextColor = it
                                    ThemeUtils.savePlaytimeBadgeTextColor(context, it)
                                }
                            )
                        }
                    }

                    DividerLine()

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
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconMd),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
                        )
                    }
                }
            }
        }

        // ── 数据管理 ──
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
                                scope.launch {
                                    pendingExportJson = withContext(Dispatchers.IO) {
                                        DataBackup.exportToJson(context)
                                    }
                                    exportLauncher.launch("gamearchive_backup.json")
                                }
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
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconMd),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
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
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconMd),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
                        )
                    }

                    DividerLine()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable {
                                if (!obsidianImporting) obsidianImportLauncher.launch(null)
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.settings_import_obsidian),
                            fontSize = DesignTokens.TextSubtitle.sp,
                            fontWeight = systemFontWeight(),
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Image(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconMd),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = DesignTokens.OpacityBody
                                )
                            )
                        )
                    }

                }
            }
        }

        // ── Bangumi ──
        if (showBangumi) {
        item("bangumi_header") {
            SectionHeader(text = context.getString(R.string.settings_bangumi))
        }
        item("bangumi_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
                    // 评分展示下拉
                    var ratingMode by remember { mutableIntStateOf(UserPrefs.getBangumiRatingMode(context)) }
                    val ratingOptions = listOf(
                        context.getString(R.string.settings_bangumi_rating_all),
                        context.getString(R.string.settings_bangumi_rating_mine),
                        context.getString(R.string.settings_bangumi_rating_none)
                    )
                    DropdownSelector(
                        label = context.getString(R.string.settings_bangumi_show_rating),
                        options = ratingOptions,
                        selectedIndex = ratingMode,
                        onSelect = { ratingMode = it; UserPrefs.setBangumiRatingMode(context, it) }
                    )

                    // 展示风格下拉
                    var displayStyle by remember { mutableIntStateOf(ThemeUtils.getBangumiDisplayStyle(context)) }
                    val displayOptions = listOf(
                        context.getString(R.string.settings_bangumi_display_list),
                        context.getString(R.string.settings_bangumi_display_grid)
                    )
                    DropdownSelector(
                        label = context.getString(R.string.settings_bangumi_display_style),
                        options = displayOptions,
                        selectedIndex = displayStyle,
                        onSelect = { displayStyle = it; ThemeUtils.setBangumiDisplayStyle(context, it) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable(openBangumiBackgroundPicker)
                            .heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.settings_bangumi_profile_background),
                            fontSize = DesignTokens.TextSubtitle.sp,
                            fontWeight = systemFontWeight(),
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(DesignTokens.CornerSmall))
                                .pointerInput(hasBangumiProfileBackground) {
                                    detectTapGestures(
                                        onTap = {
                                            openBangumiBackgroundPicker()
                                        },
                                        onLongPress = {
                                            if (
                                                hasBangumiProfileBackground &&
                                                BangumiProfileBackground.clear(context)
                                            ) {
                                                hasBangumiProfileBackground = false
                                                ThemeUtils.isChanged = true
                                                Toast.makeText(
                                                    context,
                                                    R.string.settings_bangumi_background_cleared,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Image(
                                imageVector = MiuixIcons.Basic.ArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(DesignTokens.IconMd),
                                colorFilter = ColorFilter.tint(
                                    MiuixTheme.colorScheme.onSurface.copy(
                                        alpha = DesignTokens.OpacityBody
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
        } // end if (showBangumi)

        // ── 关于与更新 ──
        item("about_header") {
            SectionHeader(text = context.getString(R.string.settings_about))
        }
        item("about_card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 10.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable {
                                scope.launch {
                                    Toast.makeText(
                                        context,
                                        R.string.settings_check_update,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    var latestTag: String? = null
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val request = okhttp3.Request.Builder()
                                                .url("https://github.com/ewiro/GameArchive/releases/latest")
                                                .header("User-Agent", "GameArchive")
                                                .build()
                                            GameArchiveApp.okHttpClient.newCall(request).execute()
                                        }
                                        val finalUrl = result.request.url.toString()
                                        val rawTag = finalUrl.substringAfterLast("/")
                                        if (rawTag.startsWith("v")) {
                                            latestTag = rawTag.removePrefix("v")
                                        }
                                    } catch (_: Exception) {
                                    }
                                    if (latestTag == null) {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                val request = okhttp3.Request.Builder()
                                                    .url("https://api.github.com/repos/ewiro/GameArchive/releases/latest")
                                                    .header("Accept", "application/vnd.github.v3+json")
                                                    .header("User-Agent", "GameArchive")
                                                    .build()
                                                GameArchiveApp.okHttpClient.newCall(request).execute()
                                            }
                                            val body = result.body?.string()
                                            if (body != null) {
                                                val json = JSONObject(body)
                                                latestTag = json.optString("tag_name", "")
                                                    .removePrefix("v")
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                    if (
                                        latestTag != null &&
                                        latestTag.isNotEmpty() &&
                                        latestTag != BuildConfig.VERSION_NAME
                                    ) {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                "https://github.com/ewiro/GameArchive/releases/latest"
                                            )
                                        )
                                        context.startActivity(intent)
                                    } else if (latestTag != null) {
                                        Toast.makeText(
                                            context,
                                            R.string.settings_update_latest,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.settings_update_failed,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.settings_check_update),
                            fontSize = DesignTokens.TextSubtitle.sp,
                            fontWeight = systemFontWeight(),
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(32.dp))
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            fontSize = DesignTokens.TextBody1.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(
                                alpha = DesignTokens.OpacityBody
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Image(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconMd),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = DesignTokens.OpacityBody
                                )
                            )
                        )
                    }
                }
            }
        }

        // ── 账号管理 ──
        item("accounts_header") { SectionHeader(text = context.getString(R.string.settings_accounts)) }
        item("accounts_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                cornerRadius = DesignTokens.CornerLarge
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
                    Text(
                        text = context.getString(R.string.account_steam),
                        fontSize = DesignTokens.TextBody1.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface.copy(
                            alpha = DesignTokens.OpacityBody
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    // 主账号
                    val primaryId = UserPrefs.getSteamId(context)
                    val primaryName = remember(primaryId, steamNamesRevision) {
                        UserPrefs.getSteamNickname(context, primaryId)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DesignTokens.SpaceSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(DesignTokens.SpaceXl))
                        Text(
                            text = primaryName,
                            fontSize = DesignTokens.TextSubtitle.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 分隔线
                    if (accounts.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        DividerLine()
                        Spacer(Modifier.height(4.dp))
                    }

                    // 额外账号列表
                    accounts.forEachIndexed { idx, acc ->
                        val accountName = remember(acc.first, steamNamesRevision) {
                            UserPrefs.getSteamNickname(context, acc.first)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = DesignTokens.SpaceSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(DesignTokens.SpaceXl))
                            Text(
                                text = accountName,
                                fontSize = DesignTokens.TextSubtitle.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = context.getString(R.string.account_remove),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = DesignTokens.ErrorRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                    .noRippleClickable {
                                        UserPrefs.removeAccount(context, idx)
                                        accounts = UserPrefs.getAdditionalAccounts(context)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    DividerLine()
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = context.getString(R.string.account_bangumi),
                        fontSize = DesignTokens.TextBody1.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface.copy(
                            alpha = DesignTokens.OpacityBody
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    if (bangumiUserId > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = DesignTokens.SpaceSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(DesignTokens.SpaceXl))
                            Text(
                                text = bangumiNickname.ifBlank { bangumiUserId.toString() },
                                fontSize = DesignTokens.TextSubtitle.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = context.getString(R.string.account_remove),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = DesignTokens.ErrorRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                    .noRippleClickable {
                                        UserPrefs.clearBangumiToken(context)
                                        bangumiUserId = 0
                                        bangumiNickname = ""
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    DividerLine()
                    Spacer(Modifier.height(4.dp))

                    // 添加账号按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable { showAccountTypeDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.account_add),
                            fontSize = DesignTokens.TextSubtitle.sp,
                            color = MiuixTheme.colorScheme.primary,
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
                        color = if (isAppInDarkTheme()) DesignTokens.ErrorRed else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

    }

        MotionModalOverlay(
            visible = skippedImportNames.isNotEmpty(),
            onDismissRequest = { skippedImportNames = emptyList() }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .heightIn(max = 540.dp)
                    .noRippleClickable { }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = context.getString(R.string.settings_obsidian_skipped_title),
                        fontSize = DesignTokens.TextSubtitle.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(DesignTokens.SpaceSm))
                    Text(
                        text = context.getString(R.string.settings_obsidian_skipped_hint),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(
                            alpha = DesignTokens.OpacityBody
                        )
                    )
                    Spacer(Modifier.height(DesignTokens.SpaceLg))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        skippedImportNames.forEach { fileName ->
                            Text(
                                text = fileName,
                                fontSize = DesignTokens.TextBody1.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = DesignTokens.SpaceXs)
                            )
                        }
                    }
                    Spacer(Modifier.height(DesignTokens.SpaceLg))
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .noRippleClickable { skippedImportNames = emptyList() }
                            .padding(
                                horizontal = DesignTokens.SpaceLg,
                                vertical = DesignTokens.SpaceSm
                            )
                    ) {
                        Text(
                            text = context.getString(R.string.general_close),
                            fontSize = DesignTokens.TextBody1.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ── 标签管理弹窗 ──
        MotionModalOverlay(
            visible = showTagDialog,
            onDismissRequest = { showTagDialog = false }
        ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .heightIn(max = 540.dp)
                        .noRippleClickable { }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = context.getString(R.string.tag_manage),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (allTags.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                allTags.forEach { tag ->
                                    val count = tagUsageCounts[tag] ?: 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .motionClickable {
                                                showTagDialog = false
                                                context.startActivity(
                                                    GameCollectionActivity.createIntent(
                                                        context,
                                                        GameCollectionActivity.MODE_TAG,
                                                        tag
                                                    )
                                                )
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = DesignTokens.TextSubtitle.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${count}",
                                            fontSize = DesignTokens.TextBody1.sp,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = context.getString(R.string.general_no_data),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint),
                                fontSize = DesignTokens.TextBody1.sp
                            )
                        }
                    }
                }
        }

        // ── 选择账号类型 ──
        MotionModalOverlay(
            visible = showAccountTypeDialog,
            onDismissRequest = { showAccountTypeDialog = false }
        ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .noRippleClickable { }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = context.getString(R.string.account_choose_type),
                            color = MiuixTheme.colorScheme.onSurface.copy(
                                alpha = DesignTokens.OpacityBody
                            ),
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        AccountTypeRow(
                            label = context.getString(R.string.account_steam),
                            onClick = {
                                showAccountTypeDialog = false
                                showAddAccountDialog = true
                            }
                        )
                        DividerLine()
                        AccountTypeRow(
                            label = context.getString(R.string.account_bangumi),
                            onClick = {
                                showAccountTypeDialog = false
                                context.startActivity(
                                    Intent(context, BangumiAuthActivity::class.java)
                                )
                            }
                        )
                    }
                }
        }

        // ── 添加 Steam 账号弹窗 ──
        MotionModalOverlay(
            visible = showAddAccountDialog,
            onDismissRequest = { showAddAccountDialog = false }
        ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .noRippleClickable { }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = context.getString(R.string.account_add_steam),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LabeledTextField(
                            label = context.getString(R.string.account_steam_id_hint),
                            value = newSteamId,
                            onValueChange = { newSteamId = it },
                            hint = ""
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledTextField(
                            label = context.getString(R.string.account_api_key_hint),
                            value = newApiKey,
                            onValueChange = { newApiKey = it },
                            hint = ""
                        )

                        Spacer(Modifier.height(16.dp))
                        val canAdd =
                            newSteamId.trim().isNotEmpty() &&
                                newApiKey.trim().isNotEmpty()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DesignTokens.ButtonHeightSmall)
                                .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                .background(if (canAdd) buttonBgColor() else buttonBgColor().copy(alpha = DesignTokens.OpacityDisabled))
                                .then(if (canAdd) Modifier.noRippleClickable {
                                    if (
                                        UserPrefs.addAccount(
                                            context,
                                            newSteamId.trim(),
                                            newApiKey.trim()
                                        )
                                    ) {
                                        accounts = UserPrefs.getAdditionalAccounts(context)
                                        showAddAccountDialog = false
                                        newSteamId = ""
                                        newApiKey = ""
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.account_exists), Toast.LENGTH_SHORT).show()
                                    }
                                } else Modifier),
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

    } // close Surface

        // ── 选择器弹窗叠加层 ──
        AnimatedVisibility(
            visible = dropdownPopupVisible && dropdownPopup != null,
            enter = fadeIn(
                animationSpec = tween(DesignTokens.FadeInDuration)
            ),
            exit = fadeOut(
                animationSpec = tween(DesignTokens.CollapseDuration)
            )
        ) {
            val popup = dropdownPopup!!
            val density = LocalDensity.current
            val triggerBottomDp = with(density) { (popup.triggerRect.bottom / density.density).dp }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DesignTokens.ScrimDark)
                    .clickable { dismissDropdown() },
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = triggerBottomDp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    val screenWidthDp = with(density) {
                        LocalWindowInfo.current.containerSize.width.toDp()
                    }
                    val triggerRightDp = with(density) { (popup.triggerRect.right / density.density).dp }
                    val paddingEnd = maxOf(0.dp, screenWidthDp - triggerRightDp)
                    AnimatedVisibility(
                        visible = dropdownPopupVisible,
                        enter = dropdownPopupEnter(),
                        exit = dropdownPopupExit()
                    ) {
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
                                            .motionClickable {
                                                popup.onSelect(index)
                                                dismissDropdown()
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
                                        Image(
                                            imageVector = MiuixIcons.Basic.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(DesignTokens.IconMd),
                                            colorFilter = ColorFilter.tint(if (isSelected)
                                                MiuixTheme.colorScheme.primary
                                            else Color.Transparent)
                                        )
                                    }
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
private fun AccountTypeRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
            .noRippleClickable(onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = DesignTokens.TextSubtitle.sp,
            fontWeight = systemFontWeight(),
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Image(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(DesignTokens.IconMd),
            colorFilter = ColorFilter.tint(
                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
            )
        )
    }
}

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
private fun SwitchRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
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
                .motionClickable {
                    dropdownHost(
                        DropdownPopupData(
                            options = options,
                            selectedIndex = selectedIndex,
                            onSelect = onSelect,
                            triggerRect = triggerRect
                        )
                    )
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
                actionColor = MiuixTheme.colorScheme.onSurface.copy(
                    alpha = DesignTokens.OpacityBody
                )
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
