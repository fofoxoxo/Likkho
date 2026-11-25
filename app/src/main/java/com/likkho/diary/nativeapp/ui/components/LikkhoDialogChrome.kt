package com.likkho.diary.nativeapp.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.likkho.diary.nativeapp.ui.theme.LikkhoFontFamilies
import com.likkho.diary.nativeapp.ui.theme.LikkhoTheme

/**
 * Unified thin hairline stroke width used across all native dialogs, menus, and boxes.
 */
val LikkhoHairlineBorder: Dp = 0.75.dp

/**
 * Standard thin hairline dialog container for Likkho.
 * - Flat zero-radius Wikipedia editorial box with a crisp 0.75.dp hairline frame
 * - Full IME & system-bar padding so keyboards never clip inputs or footer buttons
 * - Uniform header bar, internal hairline dividers, and optional footer bar
 */
@Composable
fun LikkhoHairlineDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LikkhoTheme.colors.accent,
    borderColor: Color = LikkhoTheme.colors.border,
    titleColor: Color = LikkhoTheme.colors.text,
    dismissEnabled: Boolean = true,
    maxWidth: Dp = 440.dp,
    maxHeight: Dp = 640.dp,
    scrollableBody: Boolean = true,
    bodyPadding: Dp = 14.dp,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LikkhoTheme.colors
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismissRequest() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            findDialogWindow(view)?.let { win ->
                win.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isStatusBarContrastEnforced = false
                    win.isNavigationBarContrastEnforced = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null
                ) {
                    if (dismissEnabled) onDismissRequest()
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .shadow(16.dp, RectangleShape)
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null
                    ) { /* Consume taps inside the dialog box */ }
                    .background(colors.bg)
                    .border(LikkhoHairlineBorder, borderColor, RectangleShape)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (leadingIcon != null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(leadingIconTint.copy(alpha = 0.08f))
                                    .border(LikkhoHairlineBorder, leadingIconTint.copy(alpha = 0.45f), RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = leadingIcon,
                                    contentDescription = null,
                                    tint = leadingIconTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontFamily = LikkhoFontFamilies.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    fontFamily = LikkhoFontFamilies.Mono,
                                    fontSize = 10.sp,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (headerTrailing != null) {
                            headerTrailing()
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .border(LikkhoHairlineBorder, Color.Transparent, RectangleShape)
                                .clickable(enabled = dismissEnabled) { onDismissRequest() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close",
                                tint = colors.muted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.border)

                // Body
                val bodyModifier = if (scrollableBody) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bodyPadding)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(bodyPadding)
                }
                Column(
                    modifier = bodyModifier,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )

                // Optional Footer
                if (footer != null) {
                    HorizontalDivider(thickness = LikkhoHairlineBorder, color = colors.hairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        content = footer
                    )
                }
            }
        }
    }
}

@Composable
fun LikkhoDialogOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = LikkhoTheme.colors.text,
    borderColor: Color = LikkhoTheme.colors.border,
    bgColor: Color = LikkhoTheme.colors.bg,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(bgColor)
            .border(LikkhoHairlineBorder, borderColor, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LikkhoDialogPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bgColor: Color = LikkhoTheme.colors.accent,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(bgColor)
            .border(LikkhoHairlineBorder, bgColor, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontFamily = LikkhoFontFamilies.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun findDialogWindow(view: View): Window? {
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    return null
}
