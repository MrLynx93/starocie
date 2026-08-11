package pl.starocie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The photo of the thing in front of you. Empty, it is a large camera target —
 * large because it is meant to be hit one-handed while holding the object. Filled,
 * tapping opens it full-screen, and the buttons down its right-hand edge retake it,
 * ask Google what it is, or throw it away.
 *
 * A null [onCapture] drops the camera, which is what a thing already sold wants:
 * the picture is worth keeping and worth looking at, but there is nothing left in
 * front of you to point a lens at. A null [onClear] drops the bin the same way.
 */
@Composable
fun PhotoArea(
    photo: String?,
    onCapture: (() -> Unit)?,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(photo) { photo?.let { decodePhoto(it) } }
    var viewing by remember { mutableStateOf(false) }
    val search = rememberPhotoSearch()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (bitmap != null) {
            Box(modifier = Modifier.fillMaxSize().clickable { viewing = true }) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Zdjęcie przedmiotu — dotknij, aby powiększyć",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                )

                // Down the right-hand edge rather than across the top: a row of
                // three starts covering the thing in the picture, which is the one
                // part of this card that has to stay readable.
                //
                // Tonal buttons rather than plain icons: a bare icon disappears
                // against a photo of the wrong brightness.
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                ) {
                    onCapture?.let {
                        FilledTonalIconButton(onClick = it) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "Zmień zdjęcie")
                        }
                    }
                    onClear?.let {
                        FilledTonalIconButton(onClick = it) {
                            Icon(Icons.Filled.Delete, contentDescription = "Usuń zdjęcie")
                        }
                    }
                    // Asking Google what the thing is. Offered wherever there is a
                    // photo, including on something already sold — "what was that,
                    // and what do people ask for one" outlives the sale. It sits
                    // last, under the bin: it is the button reached for while
                    // looking at the picture, so the end of the stack is where a
                    // thumb should find it.
                    search?.let { ask ->
                        FilledTonalIconButton(onClick = { photo?.let(ask) }) {
                            // Image, not Icon: an Icon tints what it draws, and
                            // Google's mark may not be recoloured.
                            Image(
                                imageVector = GoogleLogo,
                                contentDescription = "Poszukaj w Google",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        } else if (onCapture != null) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(onClick = onCapture),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = "Zrób zdjęcie",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "Zrób zdjęcie",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (viewing && bitmap != null) {
        FullScreenPhoto(bitmap, onDismiss = { viewing = false })
    }
}

@Composable
private fun FullScreenPhoto(bitmap: ImageBitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Zdjęcie przedmiotu",
                // Fit, not Crop: full-screen is for seeing the whole thing.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) { Icon(Icons.Filled.Close, contentDescription = "Zamknij") }
        }
    }
}
