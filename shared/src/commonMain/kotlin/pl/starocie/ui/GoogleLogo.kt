package pl.starocie.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Google's "G", as an [ImageVector] carrying their own path data verbatim.
 *
 * It is the official asset's geometry and its four official colours — nothing here
 * is redrawn, recoloured or re-proportioned, which is what their terms require of
 * the mark on a "Sign in with Google" button.
 *
 * **An `ImageVector` rather than a file** because the Compose resources pipeline
 * does not reach the APK on this project: `:shared` uses AGP's KMP library plugin,
 * whose asset packaging nothing wires `prepareComposeResourcesTaskForCommonMain`
 * into — the accessor generates and compiles, the file never ships, and
 * `painterResource` would fail on the device rather than at build time. Carrying
 * the vector in code is how Material's own icons ship, needs no pipeline, and is
 * one definition for both platforms.
 */
val GoogleLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "GoogleLogo",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 48f,
        viewportHeight = 48f,
    )
        .paint(
            0xFF4285F4,
            "M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 " +
                "6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z",
        )
        .paint(
            0xFF34A853,
            "M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 " +
                "2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z",
        )
        .paint(
            0xFFFBBC05,
            "M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 " +
                "17.09 2 20.45 2 24s.85 6.91 2.34 9.88l7.35-5.7z",
        )
        .paint(
            0xFFEA4335,
            "M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 " +
                "2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z",
        )
        .build()
}

private fun ImageVector.Builder.paint(colour: Long, pathData: String): ImageVector.Builder =
    addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color(colour)))
