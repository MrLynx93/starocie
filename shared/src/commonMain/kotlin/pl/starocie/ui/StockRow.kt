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
import pl.starocie.domain.ItemStats
import pl.starocie.domain.format

/**
 * One thing in stock, as it appears in the list.
 *
 * What we paid sits under the name, the way it does on the sold list: a thing is
 * worth stopping at because of the gap between the two numbers, and the ask alone
 * does not say whether there is one. A guess says "ok." so it never passes for a
 * measured price, and an unknown says so rather than showing nothing.
 *
 * The whole row is one target, photo included — it opens the thing, and everything
 * you can do about it is there.
 */
@Composable
internal fun StockRow(item: Item, stats: ItemStats, piecesLeft: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ItemThumb(item.photo)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium)
            if (item.splittable) {
                // A half-sold lot says what is left rather than what it started as:
                // that is the number you are deciding against when you pick it up.
                Text(
                    if (piecesLeft < item.quantity) {
                        "zostało $piecesLeft z ${item.quantity} szt."
                    } else {
                        "${item.quantity} szt. · na sztuki"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                boughtForLabel(stats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

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
 * What we gave for a thing, said the same way everywhere it is read.
 *
 * A share of a box is marked "ok.", and a thing with no buy behind it says we do
 * not know rather than quietly reading as free — the whole point of tolerating a
 * shortcut sale is that the gap stays visible.
 */
internal fun boughtForLabel(stats: ItemStats): String = when {
    stats.cost == null -> "nie wiemy, za ile kupiliśmy"
    stats.costIsEstimated -> "kupiliśmy za ok. ${stats.cost.format()}"
    else -> "kupiliśmy za ${stats.cost.format()}"
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
