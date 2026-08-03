package pl.starocie.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Not implemented yet: iOS cannot be built on this machine (no Xcode), so shipping
 * an untested UIImagePickerController interop would be guesswork. The seam exists
 * and compiles, so the screens are already correct on both platforms; capture
 * simply yields nothing here.
 */
@Composable
actual fun rememberPhotoCapture(onCaptured: (String?) -> Unit): () -> Unit = { onCaptured(null) }

actual fun decodePhoto(base64: String): ImageBitmap? = null
