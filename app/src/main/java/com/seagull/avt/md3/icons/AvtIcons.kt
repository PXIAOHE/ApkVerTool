package com.seagull.avt.md3.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * 由 material-icons-extended 1.7.8 的路径数据抽取而来。
 * 只保留本工程用到的图标：不引 material-icons-extended 整包（那会让 dex 爆掉），
 * 也不引 material-icons-core（只有 49 个基础图标，缺调色板/文件夹/文档/健康这几类）。
 * 几何数据与原版逐点一致，未做任何改动。
 */

val IcoInventory2: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Inventory2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(20f, 2f)
            horizontalLineTo(4f)
            curveTo(3f, 2f, 2f, 2.9f, 2f, 4f)
            verticalLineToRelative(3.01f)
            curveTo(2f, 7.73f, 2.43f, 8.35f, 3f, 8.7f)
            verticalLineTo(20f)
            curveToRelative(0f, 1.1f, 1.1f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(0.9f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8.7f)
            curveToRelative(0.57f, -0.35f, 1f, -0.97f, 1f, -1.69f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21f, 2f, 20f, 2f)
            close()
            moveTo(15f, 14f)
            horizontalLineTo(9f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(6f)
            verticalLineTo(14f)
            close()
            moveTo(20f, 7f)
            horizontalLineTo(4f)
            verticalLineTo(4f)
            horizontalLineToRelative(16f)
            verticalLineTo(7f)
            close()
        }
    }.build()
}

val IcoMonitorHeart: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "MonitorHeart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(15.11f, 12.45f)
            lineTo(14f, 10.24f)
            lineToRelative(-3.11f, 6.21f)
            curveTo(10.73f, 16.79f, 10.38f, 17f, 10f, 17f)
            reflectiveCurveToRelative(-0.73f, -0.21f, -0.89f, -0.55f)
            lineTo(7.38f, 13f)
            horizontalLineTo(2f)
            verticalLineToRelative(5f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-5f)
            horizontalLineToRelative(-6f)
            curveTo(15.62f, 13f, 15.27f, 12.79f, 15.11f, 12.45f)
            close()
        }
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(20f, 4f)
            horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(6f)
            curveToRelative(0.38f, 0f, 0.73f, 0.21f, 0.89f, 0.55f)
            lineTo(10f, 13.76f)
            lineToRelative(3.11f, -6.21f)
            curveToRelative(0.34f, -0.68f, 1.45f, -0.68f, 1.79f, 0f)
            lineTo(16.62f, 11f)
            horizontalLineTo(22f)
            verticalLineTo(6f)
            curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
            close()
        }
    }.build()
}

val IcoReceiptLong: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "ReceiptLong",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(19.5f, 3.5f)
            lineTo(18f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(15f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(12f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(9f, 2f)
            lineTo(7.5f, 3.5f)
            lineTo(6f, 2f)
            verticalLineToRelative(14f)
            horizontalLineTo(3f)
            verticalLineToRelative(3f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            horizontalLineToRelative(12f)
            curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            verticalLineTo(2f)
            lineTo(19.5f, 3.5f)
            close()
            moveTo(19f, 19f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
            reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
            verticalLineToRelative(-3f)
            horizontalLineTo(8f)
            verticalLineTo(5f)
            horizontalLineToRelative(11f)
            verticalLineTo(19f)
            close()
        }
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(9f, 7f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-6f)
            close()
        }
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(16f, 7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2f)
            close()
        }
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(9f, 10f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-6f)
            close()
        }
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(16f, 10f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2f)
            close()
        }
    }.build()
}

val IcoPalette: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Palette",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(12f, 2f)
            curveTo(6.49f, 2f, 2f, 6.49f, 2f, 12f)
            reflectiveCurveToRelative(4.49f, 10f, 10f, 10f)
            curveToRelative(1.38f, 0f, 2.5f, -1.12f, 2.5f, -2.5f)
            curveToRelative(0f, -0.61f, -0.23f, -1.2f, -0.64f, -1.67f)
            curveToRelative(-0.08f, -0.1f, -0.13f, -0.21f, -0.13f, -0.33f)
            curveToRelative(0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
            horizontalLineTo(16f)
            curveToRelative(3.31f, 0f, 6f, -2.69f, 6f, -6f)
            curveTo(22f, 6.04f, 17.51f, 2f, 12f, 2f)
            close()
            moveTo(17.5f, 13f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
            reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
            curveTo(19f, 12.33f, 18.33f, 13f, 17.5f, 13f)
            close()
            moveTo(14.5f, 9f)
            curveTo(13.67f, 9f, 13f, 8.33f, 13f, 7.5f)
            curveTo(13f, 6.67f, 13.67f, 6f, 14.5f, 6f)
            reflectiveCurveTo(16f, 6.67f, 16f, 7.5f)
            curveTo(16f, 8.33f, 15.33f, 9f, 14.5f, 9f)
            close()
            moveTo(5f, 11.5f)
            curveTo(5f, 10.67f, 5.67f, 10f, 6.5f, 10f)
            reflectiveCurveTo(8f, 10.67f, 8f, 11.5f)
            curveTo(8f, 12.33f, 7.33f, 13f, 6.5f, 13f)
            reflectiveCurveTo(5f, 12.33f, 5f, 11.5f)
            close()
            moveTo(11f, 7.5f)
            curveTo(11f, 8.33f, 10.33f, 9f, 9.5f, 9f)
            reflectiveCurveTo(8f, 8.33f, 8f, 7.5f)
            curveTo(8f, 6.67f, 8.67f, 6f, 9.5f, 6f)
            reflectiveCurveTo(11f, 6.67f, 11f, 7.5f)
            close()
        }
    }.build()
}

val IcoVpnKey: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "VpnKey",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(12.65f, 10f)
            curveTo(11.83f, 7.67f, 9.61f, 6f, 7f, 6f)
            curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
            reflectiveCurveToRelative(2.69f, 6f, 6f, 6f)
            curveToRelative(2.61f, 0f, 4.83f, -1.67f, 5.65f, -4f)
            horizontalLineTo(17f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineTo(12.65f)
            close()
            moveTo(7f, 14f)
            curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
            reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
            reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
            reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
            close()
        }
    }.build()
}

val IcoFolderOpen: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "FolderOpen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(20f, 6f)
            horizontalLineToRelative(-8f)
            lineToRelative(-2f, -2f)
            lineTo(4f, 4f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(2f, 18f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(22f, 8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(20f, 18f)
            lineTo(4f, 18f)
            lineTo(4f, 8f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(10f)
            close()
        }
    }.build()
}

val IcoDownload: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Download",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(5f, 20f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            close()
            moveTo(19f, 9f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineToRelative(6f)
            horizontalLineTo(5f)
            lineToRelative(7f, 7f)
            lineTo(19f, 9f)
            close()
        }
    }.build()
}

val IcoDeleteSweep: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "DeleteSweep",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(15f, 16f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-4f)
            close()
            moveTo(15f, 8f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-7f)
            close()
            moveTo(15f, 12f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-6f)
            close()
            moveTo(3f, 18f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(6f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(13f, 8f)
            lineTo(3f, 8f)
            verticalLineToRelative(10f)
            close()
            moveTo(14f, 5f)
            horizontalLineToRelative(-3f)
            lineToRelative(-1f, -1f)
            lineTo(6f, 4f)
            lineTo(5f, 5f)
            lineTo(2f, 5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(12f)
            close()
        }
    }.build()
}

val IcoArticle: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Article",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(19f, 3f)
            lineTo(5f, 3f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(21f, 5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(14f, 17f)
            lineTo(7f, 17f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(2f)
            close()
            moveTo(17f, 13f)
            lineTo(7f, 13f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(2f)
            close()
            moveTo(17f, 9f)
            lineTo(7f, 9f)
            lineTo(7f, 7f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(2f)
            close()
        }
    }.build()
}

val IcoStraighten: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Straighten",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(21f, 6f)
            lineTo(3f, 6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(8f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(18f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(23f, 8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(21f, 16f)
            lineTo(3f, 16f)
            lineTo(3f, 8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            lineTo(7f, 8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            lineTo(11f, 8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            lineTo(15f, 8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            lineTo(19f, 8f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(8f)
            close()
        }
    }.build()
}

val IcoVerifiedUser: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "VerifiedUser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
            curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
            lineTo(21f, 5f)
            lineToRelative(-9f, -4f)
            close()
            moveTo(10f, 17f)
            lineToRelative(-4f, -4f)
            lineToRelative(1.41f, -1.41f)
            lineTo(10f, 14.17f)
            lineToRelative(6.59f, -6.59f)
            lineTo(18f, 9f)
            lineToRelative(-8f, 8f)
            close()
        }
    }.build()
}

val IcoSave: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Save",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(17f, 3f)
            lineTo(5f, 3f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(21f, 7f)
            lineToRelative(-4f, -4f)
            close()
            moveTo(12f, 19f)
            curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
            reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
            close()
            moveTo(15f, 9f)
            lineTo(5f, 9f)
            lineTo(5f, 5f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(4f)
            close()
        }
    }.build()
}

val IcoLightbulb: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Lightbulb",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(9f, 21f)
            curveToRelative(0f, 0.5f, 0.4f, 1f, 1f, 1f)
            horizontalLineToRelative(4f)
            curveToRelative(0.6f, 0f, 1f, -0.5f, 1f, -1f)
            verticalLineToRelative(-1f)
            lineTo(9f, 20f)
            verticalLineToRelative(1f)
            close()
            moveTo(12f, 2f)
            curveTo(8.1f, 2f, 5f, 5.1f, 5f, 9f)
            curveToRelative(0f, 2.4f, 1.2f, 4.5f, 3f, 5.7f)
            lineTo(8f, 17f)
            curveToRelative(0f, 0.5f, 0.4f, 1f, 1f, 1f)
            horizontalLineToRelative(6f)
            curveToRelative(0.6f, 0f, 1f, -0.5f, 1f, -1f)
            verticalLineToRelative(-2.3f)
            curveToRelative(1.8f, -1.3f, 3f, -3.4f, 3f, -5.7f)
            curveToRelative(0f, -3.9f, -3.1f, -7f, -7f, -7f)
            close()
        }
    }.build()
}

val IcoFileOpen: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "FileOpen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
            horizontalLineTo(15f)
            verticalLineToRelative(-8f)
            horizontalLineToRelative(5f)
            verticalLineTo(8f)
            lineTo(14f, 2f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            horizontalLineTo(13f)
            close()
            moveTo(17f, 21.66f)
            verticalLineTo(16f)
            horizontalLineToRelative(5.66f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2.24f)
            lineToRelative(2.95f, 2.95f)
            lineToRelative(-1.41f, 1.41f)
            lineTo(19f, 19.41f)
            lineToRelative(0f, 2.24f)
            horizontalLineTo(17f)
            close()
        }
    }.build()
}

val IcoLabel: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Label",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(17.63f, 5.84f)
            curveTo(17.27f, 5.33f, 16.67f, 5f, 16f, 5f)
            lineTo(5f, 5.01f)
            curveTo(3.9f, 5.01f, 3f, 5.9f, 3f, 7f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 1.1f, 0.9f, 1.99f, 2f, 1.99f)
            lineTo(16f, 19f)
            curveToRelative(0.67f, 0f, 1.27f, -0.33f, 1.63f, -0.84f)
            lineTo(22f, 12f)
            lineToRelative(-4.37f, -6.16f)
            close()
        }
    }.build()
}

val IcoPhoneAndroid: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "PhoneAndroid",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(16f, 1f)
            lineTo(8f, 1f)
            curveTo(6.34f, 1f, 5f, 2.34f, 5f, 4f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            horizontalLineToRelative(8f)
            curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            lineTo(19f, 4f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            close()
            moveTo(14f, 21f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(1f)
            close()
            moveTo(17.25f, 18f)
            lineTo(6.75f, 18f)
            lineTo(6.75f, 4f)
            horizontalLineToRelative(10.5f)
            verticalLineToRelative(14f)
            close()
        }
    }.build()
}

val IcoEditNote: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "EditNote",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(3f, 10f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineTo(10f)
            close()
            moveTo(3f, 8f)
            horizontalLineToRelative(11f)
            verticalLineTo(6f)
            horizontalLineTo(3f)
            verticalLineTo(8f)
            close()
            moveTo(3f, 16f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            verticalLineTo(16f)
            close()
            moveTo(18.01f, 12.87f)
            lineToRelative(0.71f, -0.71f)
            curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
            lineToRelative(0.71f, 0.71f)
            curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0f, 1.41f)
            lineToRelative(-0.71f, 0.71f)
            lineTo(18.01f, 12.87f)
            close()
            moveTo(17.3f, 13.58f)
            lineToRelative(-5.3f, 5.3f)
            verticalLineTo(21f)
            horizontalLineToRelative(2.12f)
            lineToRelative(5.3f, -5.3f)
            lineTo(17.3f, 13.58f)
            close()
        }
    }.build()
}

val IcoCleaningServices: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "CleaningServices",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(16f, 11f)
            horizontalLineToRelative(-1f)
            verticalLineTo(3f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-2f)
            curveTo(9.9f, 1f, 9f, 1.9f, 9f, 3f)
            verticalLineToRelative(8f)
            horizontalLineTo(8f)
            curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
            verticalLineToRelative(7f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(-7f)
            curveTo(21f, 13.24f, 18.76f, 11f, 16f, 11f)
            close()
            moveTo(19f, 21f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-3f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-3f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
            verticalLineToRelative(3f)
            horizontalLineTo(9f)
            verticalLineToRelative(-3f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
            verticalLineToRelative(3f)
            horizontalLineTo(5f)
            verticalLineToRelative(-5f)
            curveToRelative(0f, -1.65f, 1.35f, -3f, 3f, -3f)
            horizontalLineToRelative(8f)
            curveToRelative(1.65f, 0f, 3f, 1.35f, 3f, 3f)
            verticalLineTo(21f)
            close()
        }
    }.build()
}

val IcoUpload: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Upload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(5f, 20f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            close()
            moveTo(5f, 10f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(4f)
            lineToRelative(-7f, -7f)
            lineTo(5f, 10f)
            close()
        }
    }.build()
}

val IcoContentCopy: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "ContentCopy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(16f, 1f)
            lineTo(4f, 1f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(2f)
            lineTo(4f, 3f)
            horizontalLineToRelative(12f)
            lineTo(16f, 1f)
            close()
            moveTo(19f, 5f)
            lineTo(8f, 5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(11f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(21f, 7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(19f, 21f)
            lineTo(8f, 21f)
            lineTo(8f, 7f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(14f)
            close()
        }
    }.build()
}

