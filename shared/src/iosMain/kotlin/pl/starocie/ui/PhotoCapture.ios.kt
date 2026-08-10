package pl.starocie.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Matches the Android side exactly — see the note there. The number is a footprint
 * multiplier, because the photo lives in the item document and every item is held
 * in memory.
 */
private const val MAX_EDGE = 640.0
private const val JPEG_QUALITY = 0.75

/**
 * Hands capture to the system picker, which owns the camera UI — so, as on Android,
 * no camera permission has to be requested, only the usage string in `Info.plist`
 * that iOS shows when the picker opens.
 *
 * Falls back to the photo library when no camera is available, which is the case on
 * every simulator: without that fallback the feature could not be exercised at all
 * without a physical phone.
 */
@Composable
actual fun rememberPhotoCapture(onCaptured: (String?) -> Unit): () -> Unit {
    // The picker outlives the composition that launched it, so it must call the
    // *current* callback rather than the one captured when the delegate was made.
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    // Held across recompositions for two reasons: the picker's delegate reference is
    // weak, and a delegate replaced mid-capture would drop the result.
    val pickerDelegate = remember { PhotoPickerDelegate { currentOnCaptured(it) } }

    return {
        val presenter = topViewController()
        if (presenter == null) {
            onCaptured(null)
        } else {
            val picker = UIImagePickerController()
            picker.sourceType = cameraOrLibrary()
            picker.delegate = pickerDelegate
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun decodePhoto(base64: String): ImageBitmap? = runCatching {
    val data = NSData.create(base64EncodedString = base64, options = 0uL) ?: return null
    val length = data.length.toInt()
    if (length == 0) return null
    val bytes = ByteArray(length)
    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    // Skia is already linked in — it is what Compose draws with — so the JPEG never
    // has to become a UIImage just to be shown.
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

private fun cameraOrLibrary(): UIImagePickerControllerSourceType {
    val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    return if (UIImagePickerController.isSourceTypeAvailable(camera)) {
        camera
    } else {
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
    }
}

/**
 * Compose owns the single root controller, but presenting over an already-presented
 * controller is rejected, so the chain is walked to its end first.
 *
 * Shared with the share sheet in `PhotoSearch.ios.kt`: anything presented over the
 * app has the same problem to solve, and two answers to it would drift.
 */
internal fun topViewController(): UIViewController? {
    val windows = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows.filterIsInstance<UIWindow>() }

    var top = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())
        ?.rootViewController
        ?: return null

    while (true) top = top.presentedViewController ?: return top
}

private class PhotoPickerDelegate(
    private val onCaptured: (String?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        // Encode only once the picker is gone, so the UI is never held by the work.
        picker.dismissViewControllerAnimated(true) { onCaptured(image?.toBase64Photo()) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) { onCaptured(null) }
    }

    // Required by the delegate's other half; the picker drives its own navigation.
    override fun navigationController(
        navigationController: UINavigationController,
        didShowViewController: UIViewController,
        animated: Boolean,
    ) = Unit
}

/**
 * Android has to read the Exif tag and rotate the pixels by hand. Here `drawInRect`
 * already honours `imageOrientation`, so redrawing the image at the smaller size
 * uprights it as a side effect and there is nothing left to correct.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toBase64Photo(): String? = runCatching {
    val width = size.useContents { width }
    val height = size.useContents { height }
    if (width <= 0.0 || height <= 0.0) return null

    val scale = minOf(1.0, MAX_EDGE / maxOf(width, height))
    val targetWidth = width * scale
    val targetHeight = height * scale

    // The trailing 1.0 is the context's scale factor: without it the context is
    // sized in points, and a 3x device produces an image three times the edge we
    // are trying to hold the document to.
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    UIImageJPEGRepresentation(scaled ?: return null, JPEG_QUALITY)
        ?.base64EncodedStringWithOptions(0uL)
}.getOrNull()
