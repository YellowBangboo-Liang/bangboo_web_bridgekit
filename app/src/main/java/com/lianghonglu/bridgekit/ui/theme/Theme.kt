package com.lianghonglu.bridgekit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

/**
 * BridgeKit 的 Compose 主题。
 *
 * Author: 梁鸿禄
 * 使用低饱和蓝绿色，方便在 Demo 中区分原生容器与内置 H5 内容。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF006875),
    secondary = Color(0xFF4A6267),
    tertiary = Color(0xFF4D5F92),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD8EB),
    secondary = Color(0xFFB1CBD0),
    tertiary = Color(0xFFB7C4FF),
)

@Composable
fun BridgeKitTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

@Preview(showBackground = true)
@Composable
private fun BridgeKitThemePreview() {
    BridgeKitTheme { }
}
