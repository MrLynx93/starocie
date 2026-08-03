package pl.starocie.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Opens the platform camera and hands back a small JPEG as Base64, or null if the
 * user backed out.
 *
 * The photo is deliberately a *thumbnail*, not a full-resolution shot. Cloud
 * Storage needs a paid plan, so the image rides inside the item document instead;
 * at roughly 15 kB against Firestore's 1 MiB document limit that is comfortable,
 * and it means both phones see the picture. A photo is supplementary — it helps
 * you recognise a thing you already named — so thumbnail quality is enough.
 */
@Composable
expect fun rememberPhotoCapture(onCaptured: (String?) -> Unit): () -> Unit

/** Decodes what [rememberPhotoCapture] produced, for display. */
expect fun decodePhoto(base64: String): ImageBitmap?
