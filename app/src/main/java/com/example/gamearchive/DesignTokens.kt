package com.example.gamearchive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设计标记 — 应用层单一事实来源。主题色走 MiuixTheme.colorScheme，此处仅补充应用专有色与尺寸常量。 */
object DesignTokens {

    // ═══════════════════ 颜色 ═══════════════════

    /** Miuix HyperOS 蓝 — 强调色 (保留品牌标志) */
    val AccentBlue = Color(0xFF3482FF)

    // ── 评价语义色 ──
    val ReviewGreat   = Color(0xFFE65100)  // ≥95%
    val ReviewGood    = Color(0xFF1565C0)  // ≥70%
    val ReviewMixed   = Color(0xFF616161)  // ≥40%
    val ReviewPoor    = Color(0xFFD32F2F)  // <40%
    val ReviewDefault = Color(0xFF66C0F4)  // 暂无评分

    // ── 语义反馈 ──
    val SuccessGreen = Color(0xFF4CAF50)
    val ErrorRed     = Color(0xFFD32F2F)

    // ── 价格 / 折扣 ──
    val DiscountGreen     = Color(0xFFA1CD44)
    val DiscountGreenText = Color(0xFF000400)
    val PriceOrange       = Color(0xFFFF6600)

    // ── 时长徽章 ──
    val BadgeZero    = Color(0xFFCCCCD6)
    val Badge200     = Color(0xFFD42517)
    val Badge100     = Color(0xFFFB8B05)
    val Badge050     = Color(0xFF7E1671)
    val Badge020     = Color(0xFF1772B4)
    val BadgeDefault = Color(0xFF20894D)

    /** 时长 → 徽章颜色（匹配现有逻辑：≥200→红 ≥100→橙 ≥50→紫 ≥20→蓝 >0→绿 0→灰） */
    val badgeColorMap = mapOf(
        200 to Badge200,
        100 to Badge100,
        50  to Badge050,
        20  to Badge020,
        0   to BadgeZero,
    )

    /** 根据小时数取徽章颜色 */
    fun badgeColor(hours: Double): Color = when {
        hours >= 200 -> Badge200
        hours >= 100 -> Badge100
        hours >= 50  -> Badge050
        hours >= 20  -> Badge020
        hours >  0   -> BadgeDefault
        else         -> BadgeZero
    }

    // ── 个人资料卡片 ──
    val ProfileOverlay  = Color(0x40000000)
    val ProfileTextDim1 = Color(0xFFCCCCCC)
    val ProfileTextDim2 = Color(0xFFDDDDDD)
    val TextShadowColor = Color(0x80000000)

    // ── 在线状态 ──
    val StatusOnline  = Color(0xFFB3E5FC)
    val StatusOffline = Color(0xFFE0E0E0)
    val StatusInGame  = Color(0xFFA3CF06)

    // ── 遮罩 ──
    val ScrimDark = Color(0x80000000)

    // ── 标记颜色 ──
    val MarkUnplayed  = Color(0xFF757575)  // gray — 未开始
    val MarkPlaying   = Color(0xFF1565C0)  // blue — 正在玩
    val MarkCompleted = Color(0xFF2E7D32)  // green — 通关一周目
    val MarkMulti     = Color(0xFF7B1FA2)  // purple — 多周目通关
    val MarkLongterm  = Color(0xFFE65100)  // orange — 长期游玩
    val MarkPerfected = Color(0xFFFF8F00)  // gold — 完美通关
    val MarkShelved   = Color(0xFF4E342E)  // brown — 搁置
    val MarkAbandoned = Color(0xFFC62828)  // red — 抛弃

    // ═══════════════════ 透明度层级 ═══════════════════

    const val OpacityChipBg   = 0.15f  // 筛选标签选中背景
    const val OpacityDisabled = 0.30f  // 未选中标签边框
    const val OpacityHint     = 0.40f  // 次要图标 / 空状态
    const val OpacityMuted    = 0.45f  // 标记状态弱化文字
    const val OpacityInactive = 0.50f  // 未激活 Tab
    const val OpacityBody     = 0.60f  // 正文辅助信息
    const val OpacityEmphasis = 0.70f  // 分组箭头

    // ═══════════════════ 字号层级 ═══════════════════

    const val TextCaption  = 10  // 底栏标签 / 近期时长 / 点赞数 / 时长徽章
    const val TextBody2    = 12  // 区块标题 / 标记芯片 / 评价摘要 / 单选标签
    const val TextBody1    = 14  // 正文 / 游戏名 / 设置标签 / 评价正文
    const val TextSubtitle = 16  // 分组标题 / 弹窗标题 / 折扣% / 统计数值
    const val TextTitle    = 18  // 顶栏标题 / 折后价
    const val TextHeadline = 20  // 个人资料名 / 全价展示 / "+" 按钮
    // ⚠️ 用法: fontSize = DesignTokens.TextBody1.sp

    // ═══════════════════ 圆角层级 ═══════════════════

    val CornerSmall  = 4.dp   // 标签芯片
    val CornerMedium = 8.dp   // 封面 / 折扣徽章 / 时长徽章 / 媒体卡片
    val CornerLarge  = 16.dp  // 卡片 / 筛选芯片 / 弹窗
    val CornerXLarge = 22.dp  // 个人资料卡片

    // ═══════════════════ 间距层级 ═══════════════════

    val SpaceXxs   = 2.dp
    val SpaceXs    = 4.dp
    val SpaceSm    = 6.dp
    val SpaceMd    = 8.dp
    val SpaceLg    = 12.dp
    val SpaceXl    = 16.dp
    val SpaceXxl   = 20.dp
    val SpaceHuge  = 24.dp
    val SpaceMassive = 32.dp
    val SpaceUltra = 48.dp

    // ═══════════════════ 组件尺寸 ═══════════════════

    // 导航栏（顶栏高度各自 Activity 决定：主页 48dp，详情/设置 52dp）
    val BottomBarHeight = 56.dp

    // 按钮
    val ButtonHeight      = 48.dp   // 主操作按钮
    val ButtonHeightSmall = 40.dp   // 弹窗内操作按钮
    val ButtonMaxWidth    = 260.dp  // 居中按钮最大宽度

    // 封面
    val CoverWidth  = 130.dp
    val CoverHeight = 61.dp

    // 分割线
    val DividerHeight = 1.dp

    // 头像
    val AvatarOuter = 70.dp
    val AvatarInner = 58.dp

    // 边框
    val BorderThin  = 1.dp
    val BorderThick = 1.5.dp

    // 动画时长 (ms)
    const val AnimDuration = 200

    // ═══════════════════ 图标尺寸 ═══════════════════

    val IconSm   = 10.dp
    val IconMd   = 14.dp
    val IconStd  = 18.dp
    val IconLg   = 20.dp
    val IconXl   = 24.dp
    val IconHuge = 28.dp
    val IconPlay = 48.dp
}

// ═══════════════════ 全局可组合项 ═══════════════════

/** 统一选中态指示器 — 未选：灰色空心圆 / 选中：蓝色实心圆 + 白色对勾 */
@Composable
fun SelectableIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    val indicatorColor = when {
        !enabled -> MiuixTheme.colorScheme.outline.copy(alpha = DesignTokens.OpacityDisabled)
        selected -> if (isDark) MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityEmphasis)
                    else DesignTokens.AccentBlue
        else -> MiuixTheme.colorScheme.outline.copy(alpha = DesignTokens.OpacityHint)
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .border(
                if (selected) 0.dp else DesignTokens.BorderThick,
                indicatorColor,
                CircleShape
            )
            .background(
                if (selected) indicatorColor else Color.Transparent,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.5f)
                    lineTo(w * 0.45f, h * 0.75f)
                    lineTo(w * 0.8f, h * 0.25f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/** App 内主题检测 — 尊重用户在设置中选择的模式，而非仅查系统主题 */
@Composable
fun isAppInDarkTheme(): Boolean {
    val context = LocalContext.current
    return when (ThemeUtils.getThemeMode(context)) {
        0 -> false   // 强制浅色
        1 -> true    // 强制深色
        else -> isSystemInDarkTheme()  // 跟随系统
    }
}

/** 主题自适应按钮背景色 — 浅色蓝底 / 深色灰底。lightColor 仅在浅色模式下生效 */
@Composable
fun buttonBgColor(lightColor: Color = DesignTokens.AccentBlue): Color =
    if (isAppInDarkTheme()) Color(0xFF3A3A3C) else lightColor

/** 主题自适应标签背景色 — 浅色蓝底 / 深色灰底 */
@Composable
fun tagBgColor(): Color =
    if (isAppInDarkTheme()) Color(0xFF3A3A3C) else DesignTokens.AccentBlue
