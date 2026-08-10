package pl.starocie.ui

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** The Google app, which is what turns a shared image into a Lens search. */
private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
private const val TAG = "starocie/lens"

/**
 * Always available: the fallback is a chooser, and a phone with nothing at all that
 * takes an image is not a phone this app is being used on.
 */
@Composable
actual fun rememberPhotoSearch(): ((String) -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        { photo ->
            // Nothing to report on screen — the search either opens or it does not,
            // and the picture is still there either way.
            runCatching { context.searchByPhoto(photo) }
                .onFailure { Log.w(TAG, "nothing on this phone would take the photo", it) }
            Unit
        }
    }
}

/**
 * Writes the photo out and hands it to Google.
 *
 * It has to become a file first: an intent carries a `content://` URI, never a
 * Base64 string, and the only copy we hold is the one inside the document. It goes
 * to the cache under a fixed name so the searches cannot pile up — this file is a
 * courier, not a second copy of the photo.
 *
 * Straight to the Google app when it is installed, that being the search actually
 * asked for; a chooser in between would be a tap that answers nothing. Where it is
 * absent the chooser *is* the answer, some phones keeping Lens behind another app.
 */
private fun Context.searchByPhoto(photo: String) {
    val file = File(cacheDir, "lens.jpg")
    file.writeBytes(Base64.decode(photo, Base64.NO_WRAP))
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Package visibility is why the manifest has to name the Google app in
    // <queries>: from Android 11 this resolves to null without it, and every phone
    // would fall through to the chooser however plainly Google is installed.
    val direct = Intent(send).setPackage(GOOGLE_APP)
    val intent = if (direct.resolveActivity(packageManager) != null) {
        direct
    } else {
        Intent.createChooser(send, "Szukaj zdjęciem")
    }

    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
