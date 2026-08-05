package pl.starocie.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sized so the photo is worth opening full-screen, but no larger. It lives inside
 * the item document and every item is held in memory, so this number multiplies
 * the app's footprint directly: roughly 45 kB each, which is comfortable for a few
 * hundred photographed items and not for a few thousand. Past that the photos
 * belong in Cloud Storage with only URLs in the document.
 */
private const val MAX_EDGE = 640
private const val JPEG_QUALITY = 75

/**
 * Captures to a real file via `TakePicture`.
 *
 * `TakePicturePreview` is simpler — no FileProvider — but it returns the camera's
 * *thumbnail*, around 150px, which is fine in a list and useless full-screen. The
 * file round-trip is the price of a photo worth enlarging.
 *
 * No CAMERA permission is declared or requested: the capture is performed by the
 * camera app, and declaring the permission would oblige us to ask for it.
 */
@Composable
actual fun rememberPhotoCapture(onCaptured: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = pending
        onCaptured(if (saved && file != null) file.toBase64Photo() else null)
        // The full-resolution original is not kept: the document holds the only copy.
        file?.delete()
        pending = null
    }

    return {
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        pending = file
        launcher.launch(context.uriFor(file))
    }
}

actual fun decodePhoto(base64: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

private fun Context.uriFor(file: File): Uri =
    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

private fun File.toBase64Photo(): String? = runCatching {
    // Two passes: measure first, then decode already subsampled, so a 12 MP
    // original never has to be held in memory at full size.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, bounds)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
    }
    val decoded = BitmapFactory.decodeFile(absolutePath, options) ?: return null

    val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt(),
            (decoded.height * scale).toInt(),
            true,
        )
    } else {
        decoded
    }

    ByteArrayOutputStream().use { out ->
        scaled.uprighted(exifOrientation()).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}.getOrNull()

/**
 * The camera writes the sensor's own landscape frame and records how the phone was
 * held as an Exif tag; `BitmapFactory` ignores that tag. A photo taken in portrait
 * therefore arrives lying on its side, and re-encoding drops the tag that would
 * have explained it — so the rotation is baked into the pixels here, while the
 * original file is still around to be asked.
 */
private fun File.exifOrientation(): Int =
    runCatching { ExifInterface(absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun Bitmap.uprighted(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        // Mirrored variants: rare, but a front camera can produce them.
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun sampleSizeFor(longestEdge: Int): Int {
    var sample = 1
    while (longestEdge / (sample * 2) >= MAX_EDGE) sample *= 2
    return sample
}
