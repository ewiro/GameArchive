package com.example.gamearchive

import android.os.Build

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
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
    const val ExpandDuration = 280
    const val CollapseDuration = 220
    const val FadeInDuration = 180
    const val FadeOutDuration = 140
    const val PressInDuration = 45
    const val PressOutDuration = 160
    const val PressScale = 0.97f
    const val PressAlpha = 0.84f

    // ═══════════════════ 图标尺寸 ═══════════════════

    val IconSm   = 10.dp
    val IconMd   = 14.dp
    val IconExpandable = 16.dp
    val IconStd  = 18.dp
    val IconLg   = 20.dp
    val IconXl   = 24.dp
    val IconHuge = 28.dp
    val IconPlay = 48.dp
}

fun smoothExpandEnter(): EnterTransition =
    expandVertically(
        animationSpec = tween(
            durationMillis = DesignTokens.ExpandDuration,
            easing = FastOutSlowInEasing
        ),
        expandFrom = Alignment.Top
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DesignTokens.FadeInDuration,
            easing = LinearOutSlowInEasing
        ),
        initialAlpha = 0f
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = DesignTokens.ExpandDuration,
            easing = LinearOutSlowInEasing
        ),
        initialOffsetY = { fullHeight -> -fullHeight / 16 }
    )

fun smoothExpandExit(): ExitTransition =
    shrinkVertically(
        animationSpec = tween(
            durationMillis = DesignTokens.CollapseDuration,
            easing = FastOutLinearInEasing
        ),
        shrinkTowards = Alignment.Top
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = DesignTokens.FadeOutDuration,
            easing = FastOutLinearInEasing
        )
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = DesignTokens.CollapseDuration,
            easing = FastOutLinearInEasing
        ),
        targetOffsetY = { fullHeight -> -fullHeight / 24 }
    )

/** 统一展开触发器：整行点击、轻量按压反馈、箭头状态和无涟漪外观。 */
@Composable
fun ExpandableSectionTrigger(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    arrowColor: Color = MiuixTheme.colorScheme.onSurface.copy(
        alpha = DesignTokens.OpacityBody
    ),
    expandedArrowColor: Color = DesignTokens.AccentBlue,
    content: @Composable RowScope.() -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) DesignTokens.PressScale else 1f,
        animationSpec = if (pressed) {
            tween(
                durationMillis = DesignTokens.PressInDuration,
                easing = FastOutLinearInEasing
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "expandable_trigger_scale"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) DesignTokens.PressAlpha else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) {
                DesignTokens.PressInDuration
            } else {
                DesignTokens.PressOutDuration
            },
            easing = if (pressed) FastOutLinearInEasing else LinearOutSlowInEasing
        ),
        label = "expandable_trigger_alpha"
    )
    val animatedArrowColor by animateColorAsState(
        targetValue = if (expanded) expandedArrowColor else arrowColor,
        animationSpec = tween(
            durationMillis = DesignTokens.AnimDuration,
            easing = FastOutSlowInEasing
        ),
        label = "expandable_trigger_arrow_color"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
            }
            .then(modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onToggle
            )
            .semantics {
                stateDescription = context.getString(
                    if (expanded) R.string.accessibility_expanded
                    else R.string.accessibility_collapsed
                )
            }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
        ExpandableArrow(expanded = expanded, color = animatedArrowColor)
    }
}

/** 与 [ExpandableSectionTrigger] 配套的可中断展开内容动画。 */
@Composable
fun ExpandableSectionContent(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = smoothExpandEnter(),
        exit = smoothExpandExit(),
        content = content
    )
}

fun dropdownPopupEnter(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = DesignTokens.FadeInDuration,
            easing = LinearOutSlowInEasing
        )
    ) + scaleIn(
        initialScale = 0.94f,
        transformOrigin = TransformOrigin(1f, 0f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + slideInVertically(
        initialOffsetY = { fullHeight -> -fullHeight / 12 },
        animationSpec = tween(
            durationMillis = DesignTokens.ExpandDuration,
            easing = LinearOutSlowInEasing
        )
    )

fun dropdownPopupExit(): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = DesignTokens.FadeOutDuration,
            easing = FastOutLinearInEasing
        )
    ) + scaleOut(
        targetScale = 0.97f,
        transformOrigin = TransformOrigin(1f, 0f),
        animationSpec = tween(
            durationMillis = DesignTokens.CollapseDuration,
            easing = FastOutLinearInEasing
        )
    )

@Composable
fun MotionModalOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    bottomSheet: Boolean = false,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = DesignTokens.FadeInDuration,
                easing = LinearOutSlowInEasing
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = DesignTokens.FadeOutDuration,
                easing = FastOutLinearInEasing
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DesignTokens.ScrimDark)
                    .noRippleClickable(onDismissRequest)
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(contentAlignment),
                enter = if (bottomSheet) {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(
                        initialAlpha = 0.82f,
                        animationSpec = tween(DesignTokens.FadeInDuration)
                    )
                } else {
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(DesignTokens.FadeInDuration))
                },
                exit = if (bottomSheet) {
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(
                            durationMillis = DesignTokens.CollapseDuration,
                            easing = FastOutLinearInEasing
                        )
                    ) + fadeOut(tween(DesignTokens.FadeOutDuration))
                } else {
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(
                            durationMillis = DesignTokens.CollapseDuration,
                            easing = FastOutLinearInEasing
                        )
                    ) + fadeOut(tween(DesignTokens.FadeOutDuration))
                },
                content = { content() }
            )
        }
    }
}

/** 无暗色状态层的通用按压反馈，适用于卡片、封面和选择器。 */
@Composable
fun Modifier.motionClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    pressedAlpha: Float = 0.88f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = if (pressed && enabled) {
            tween(DesignTokens.PressInDuration, easing = FastOutLinearInEasing)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "motion_clickable_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedAlpha else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) {
                DesignTokens.PressInDuration
            } else {
                DesignTokens.PressOutDuration
            },
            easing = if (pressed) FastOutLinearInEasing else LinearOutSlowInEasing
        ),
        label = "motion_clickable_alpha"
    )
    return Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(this)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
}

/** 系统 Dialog 内容入场；系统窗口负责退出淡化，此处补齐原生弹性浮起。 */
@Composable
fun Modifier.motionDialogSurface(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dialog_surface_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = DesignTokens.FadeInDuration,
            easing = LinearOutSlowInEasing
        ),
        label = "dialog_surface_alpha"
    )
    return Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(this)
}

@Composable
fun ExpandableArrow(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expandable_arrow_rotation"
    )
    Image(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        modifier = modifier
            .size(DesignTokens.IconExpandable)
            .rotate(rotation),
        colorFilter = ColorFilter.tint(color)
    )
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

/** 获取状态栏高度（dp），消除 5 处重复代码 */
@Composable
fun statusBarHeightDp(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.statusBars.getTop(density).toDp() }
}

/** 评价分数 → 语义颜色 */
fun reviewColor(percent: Int): Color = when {
    percent >= 95 -> DesignTokens.ReviewGreat
    percent >= 70 -> DesignTokens.ReviewGood
    percent >= 40 -> DesignTokens.ReviewMixed
    else          -> DesignTokens.ErrorRed
}

/** 读取系统"粗体文字"辅助功能设置（Android 12+），返回适配后的字重 */
@Composable
fun systemFontWeight(): FontWeight {
    val configuration = LocalConfiguration.current
    val adjustment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        configuration.fontWeightAdjustment
    } else {
        0
    }
    return when {
        adjustment >= 300 -> FontWeight(1000)
        adjustment >= 200 -> FontWeight.Bold
        adjustment >= 100 -> FontWeight.SemiBold
        adjustment >= 1 -> FontWeight.Medium
        else -> FontWeight.Normal
    }
}

/** 无涟漪点击修饰符 — 去掉了 Material ripple 灰色方框效果 */
@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )

/** 统一 MiuixTheme 包装 — 消除 4 个 Activity 中的重复 */
@Composable
fun MiuixThemeForApp(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorSchemeMode = when (ThemeUtils.getThemeMode(context)) {
        0 -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light
        1 -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark
        else -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.System
    }
    top.yukonga.miuix.kmp.theme.MiuixTheme(
        controller = top.yukonga.miuix.kmp.theme.ThemeController(colorSchemeMode = colorSchemeMode),
        content = content
    )
}
