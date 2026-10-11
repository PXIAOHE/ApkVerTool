// Miuix 悬浮顶栏 / 底栏模糊 —— 从 ApkEditorMiuix 的 BlurUtils.kt 移植（同一套 miuix 0.9.4-rc01 API）。
// 原理：页面内容经 layerBackdrop 录制 → 栏体 progressiveTextureBlur 实时采样模糊；
// 顶栏滚过内容时透明起步、收拢后变实，底栏常驻毛玻璃。
package com.seagull.avt.app

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

object TopBarBlurConfig {
    const val BlurRadius: Float = 10f
    const val SurfaceAlpha: Float = 0.3f
    const val Curve: Float = 2.2f
}

/** RuntimeShader 可用（API 33+，与 minSdk 33 一致恒真）才返回 backdrop，否则整条模糊链静默禁用 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 顶/底栏毛玻璃容器。
 * @param gradient 顶栏用 [ProgressiveBlur.Top]（顶部最强），底栏必须用 [ProgressiveBlur.Bottom]
 *                  （底部最强向上渐清）—— 方向反了模糊带会落在屏幕外，视觉上等于没糊。
 * @param blurRadius 模糊半径（吃 UI 设置的 UiConfigState.blurRadius）
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    gradient: ProgressiveBlur = ProgressiveBlur.Top,
    blurRadius: Float = TopBarBlurConfig.BlurRadius,
    surfaceAlpha: Float = TopBarBlurConfig.SurfaceAlpha,
    content: @Composable () -> Unit,
) {
    val blurActive = blurEnabled && backdrop != null
    Box {
        if (blurActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop!!,
                        shape = RectangleShape,
                        gradient = gradient.copy(curve = TopBarBlurConfig.Curve),
                        blurRadius = blurRadius,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(surfaceAlpha)),
                            ),
                        ),
                    ),
            )
        }
        content()
    }
}

@Composable
fun isInDarkTheme(): Boolean {
    val surface = MiuixTheme.colorScheme.surface
    val luminance = 0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue
    return luminance < 0.5f
}

/** 列表滚动接顶栏收拢行为 + 过滚动回弹（Miuix 手感） */
fun Modifier.pageScrollModifiers(
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)

/** 顶栏收拢判定色：滚过内容 = 实色，顶部 = 透明（悬浮） */
@Composable
fun topBarColor(collapsed: Boolean, blurActive: Boolean): Color =
    if (blurActive) Color.Transparent
    else if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
