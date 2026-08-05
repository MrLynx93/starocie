package pl.starocie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.starocie.domain.Item
import pl.starocie.domain.format

/**
 * One thing in stock, as it appears in a list.
 *
 * Shared by the sell search and the stock list so a thing looks the same wherever
 * it is met — the two screens differ in what tapping does, not in what a row is.
 *
 * The photo is its own target, because the two screens want different things of it:
 * on the sell list the row sells and the picture opens the thing to look at
 * properly, which is the only way to reach that from the middle of a sale. Where
 * the row already opens the item, [onPhotoClick] simply follows it.
 */
@Composable
internal fun StockRow(item: Item, onClick: () -> Unit, onPhotoClick: () -> Unit = onClick) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ItemThumb(item.photo, onClick = onPhotoClick)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium)
            if (item.splittable) {
                // "12 szt." alone reads as a stock level, which invites the
                // expectation that selling counts it down. It never does — the lot
                // leaves stock when a sale is marked as completing it.
                Text(
                    "${item.quantity} szt. · na sztuki",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item.price?.let {
            Text(
                it.format(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A photo helps you spot the thing among similarly named ones; the name still does
 * the finding, so a missing photo costs nothing. An empty square stands in for it
 * anyway, so the names stay on one line down the list instead of stepping in and
 * out.
 */
@Composable
internal fun ItemThumb(photo: String?, onClick: (() -> Unit)? = null) {
    val thumb = remember(photo) { photo?.let { decodePhoto(it) } }
    // Clipped before the click so the ripple stays inside the rounded square, and
    // the click last of all so nothing is painted over the ripple afterwards.
    val square = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
    val tap = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)

    if (thumb != null) {
        Image(
            bitmap = thumb,
            contentDescription = onClick?.let { "Pokaż przedmiot" },
            contentScale = ContentScale.Crop,
            modifier = square.then(tap),
        )
    } else {
        Box(
            modifier = square
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(tap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
