package pl.starocie.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

private const val MAX_EDGE = 320
private const val JPEG_QUALITY = 70

/**
 * Uses `TakePicturePreview`, which returns the camera's own thumbnail bitmap. That
 * avoids a FileProvider and a declared CAMERA permission entirely — declaring the
 * permission would oblige us to request it, whereas handing the job to the camera
 * app needs neither.
 */
@Composable
actual fun rememberPhotoCapture(onCaptured: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap -> onCaptured(bitmap?.toBase64Thumbnail()) }

    return { launcher.launch(null) }
}

actual fun decodePhoto(base64: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

private fun Bitmap.toBase64Thumbnail(): String? = runCatching {
    val scale = MAX_EDGE.toFloat() / maxOf(width, height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    } else {
        this
    }

    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}.getOrNull()
