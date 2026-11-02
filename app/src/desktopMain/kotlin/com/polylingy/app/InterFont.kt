package com.polylingy.app

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Inter (github.com/rsms/inter, SIL Open Font License — `fonts/Inter-LICENSE.txt`), the typeface of the design canvas.
 * One variable file serves every weight the app uses, 650 included.
 */
val InterFamily = FontFamily(
    listOf(400, 500, 600, 650, 700).map { weight ->
        Font("fonts/InterVariable.ttf", FontWeight(weight), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(weight)))
    },
)

/** The base text of the desktop app: Inter, drawn with subpixel smoothing like text in a browser on Windows. */
@OptIn(ExperimentalTextApi::class)
val DesktopText = TextStyle(
    fontFamily = InterFamily,
    platformStyle = PlatformTextStyle(
        spanStyle = null,
        paragraphStyle = PlatformParagraphStyle(
            FontRasterizationSettings(FontSmoothing.SubpixelAntiAlias, FontHinting.Slight, subpixelPositioning = true, autoHintingForced = false),
        ),
    ),
)
