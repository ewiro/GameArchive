package com.example.gamearchive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun ActivityHistorySection(
    kind: ActivityKind,
    records: List<ItemActivityRecord>,
    dimTitle: Boolean = false,
    titleFontSize: TextUnit = DesignTokens.TextBody1.sp,
    onRecordClick: ((ItemActivityRecord) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)

    Column(modifier = modifier.fillMaxWidth()) {
        ExpandableSectionTrigger(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = DesignTokens.SpaceLg),
            arrowColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = DesignTokens.OpacityHint
            )
        ) {
            Text(
                text = context.getString(
                    if (kind == ActivityKind.GAME) {
                        R.string.activity_game_history
                    } else {
                        R.string.activity_anime_history
                    }
                ),
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                color = if (dimTitle) dim else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        ExpandableSectionContent(expanded = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DesignTokens.SpaceXl,
                        end = DesignTokens.SpaceXl,
                        bottom = DesignTokens.SpaceLg
                    )
            ) {
                if (records.isEmpty()) {
                    Text(
                        text = context.getString(R.string.general_no_data),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = dim
                    )
                } else {
                    records.forEachIndexed { index, record ->
                        if (index > 0) Spacer(Modifier.size(DesignTokens.SpaceMd))
                        Text(
                            text = if (kind == ActivityKind.GAME) {
                                context.getString(
                                    R.string.activity_game_record,
                                    record.date,
                                    formatHours(record.amount)
                                )
                            } else {
                                context.getString(
                                    R.string.activity_anime_record,
                                    record.date,
                                    record.amount
                                )
                            },
                            fontSize = DesignTokens.TextBody1.sp,
                            color = dim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (onRecordClick != null) {
                                        Modifier.noRippleClickable {
                                            onRecordClick(record)
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun formatHours(minutes: Int): String =
    String.format(Locale.US, "%.1f", minutes / 60.0)
