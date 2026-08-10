package pl.starocie.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.popoverPresentationController

/**
 * The share sheet, which is where Google lives on iOS.
 *
 * There is no equivalent of Android's "send this image straight to Google": an app
 * cannot name another app's share target, so the sheet is the whole of the choice
 * and Google, Chrome or Lens are entries in it. One more tap than Android, and the
 * same search at the end of it.
 */
@Composable
actual fun rememberPhotoSearch(): ((String) -> Unit)? = remember { { photo -> sharePhoto(photo) } }

@OptIn(BetaInteropApi::class)
private fun sharePhoto(photo: String) {
    val data = NSData.create(base64EncodedString = photo, options = 0uL) ?: return
    val image = UIImage.imageWithData(data) ?: return
    val presenter = topViewController() ?: return

    val sheet = UIActivityViewController(
        activityItems = listOf(image),
        applicationActivities = null,
    )
    // An iPad presents this as a popover and wants somewhere to hang it; without an
    // anchor it raises instead of appearing. Harmless on a phone, which ignores it.
    sheet.popoverPresentationController?.sourceView = presenter.view
    presenter.presentViewController(sheet, animated = true, completion = null)
}
