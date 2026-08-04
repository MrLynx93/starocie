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
 * tapping opens it full-screen, and the overlay buttons retake or remove it.
 */
@Composable
fun PhotoArea(
    photo: String?,
    onCapture: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(photo) { photo?.let { decodePhoto(it) } }
    var viewing by remember { mutableStateOf(false) }

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
                    contentDescription = "Zdjęcie rzeczy — dotknij, aby powiększyć",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                )

                // Tonal buttons rather than plain icons: a bare icon disappears
                // against a photo of the wrong brightness.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                ) {
                    FilledTonalIconButton(onClick = onCapture) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Zmień zdjęcie")
                    }
                    FilledTonalIconButton(onClick = onClear) {
                        Icon(Icons.Filled.Delete, contentDescription = "Usuń zdjęcie")
                    }
                }
            }
        } else {
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

/**
 * The same photo with nothing to press.
 *
 * For looking at a thing that is already recorded: tapping still enlarges it, but
 * there is no camera and no bin, because a screen you opened to read should not
 * put a destructive button under your thumb.
 */
@Composable
fun PhotoView(photo: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(photo) { photo?.let { decodePhoto(it) } }
    var viewing by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Zdjęcie rzeczy — dotknij, aby powiększyć",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { viewing = true },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp),
                )
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
                contentDescription = "Zdjęcie rzeczy",
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
