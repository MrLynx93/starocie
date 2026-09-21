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
 * [bought] turns the row round for a day's "Co kupiliśmy", where the question is not
 * what we are asking but what the thing cost us: the total paid moves to the figure's
 * place on the right, under a word saying which figure it is, and the line under the
 * name goes with it rather than saying the same number twice. It is the *whole* cost —
 * a lot of three at 30,00 zł reads 90,00 zł — because a list of what a day cost is
 * read for the money that left our hands, and a per-piece price there would be a
 * fraction of it presented as the whole. The ask is a tap away on the thing itself.
 *
 * The whole row is one target, photo included — it opens the thing, and everything
 * you can do about it is there.
 *
 * **The text sets the height, not the photo.** At 52 dp the picture was the tallest
 * thing on a row whose two lines of writing needed 36, so every list was paying a
 * third of its height for a thumbnail — and a photo in this app is supplementary,
 * the name being what a thing is found by. At 40 dp it still does the one job it has,
 * which is helping you pick the right one of two similar names out of a list, and the
 * row is as tall as what it says: 56 dp for two lines, 68 for the three a lot or a
 * sold thing carries.
 */
@Composable
internal fun StockRow(
    item: Item,
    stats: ItemStats,
    piecesLeft: Int,
    bought: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
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
                        "Zostało $piecesLeft z ${item.quantity} szt."
                    } else {
                        "${item.quantity} szt. · na sztuki"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // On a day's list of what it cost us the figure has moved right, and one
            // number in two places on one row is a row read twice. An item whose cost
            // we do not know has nothing to put there, so the line stays and says so —
            // the unknown is never allowed to read as nothing at all.
            if (!bought || stats.cost == null) {
                Text(
                    // By the piece for a lot, which is the number beside it on the
                    // right: the ask on a lot is what one of them goes for, so a total
                    // cost against a per-piece ask would be a gap that is not there.
                    boughtForLabel(stats, pieces = item.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        if (bought) {
            // Drawn as the day's sales are drawn directly above it — the figure and a
            // word under it saying which figure it is — so the two sections of a day
            // read as one column of money rather than two habits.
            stats.cost?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${if (stats.costIsEstimated) "ok. " else ""}${it.format()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Kupiliśmy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item.price?.let {
                Text(
                    it.format(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What we gave for a thing, said the same way everywhere it is read.
 *
 * A share of a box is marked "ok.", and a thing with no buy behind it says we do
 * not know rather than quietly reading as free — the whole point of tolerating a
 * shortcut sale is that the gap stays visible.
 *
 * [pieces] above one makes it a lot, and then the figure is what *one* of them cost,
 * in the words the buy form and the item screen use for the same number. The cost on
 * the record is the lot's, so this is a division — and it has to be, because the
 * price beside it on the row is the ask for one piece. Read against a lot's whole
 * cost that ask looks like a disaster, which is the one thing a list of things to
 * sell must not say by accident.
 */
internal fun boughtForLabel(stats: ItemStats, pieces: Int = 1): String {
    val cost = stats.cost ?: return "Nie wiemy, za ile kupiliśmy"
    val approx = if (stats.costIsEstimated) "ok. " else ""
    return if (pieces > 1) {
        "Kupiliśmy po $approx${(cost / pieces).format()} za sztukę"
    } else {
        "Kupiliśmy za $approx${cost.format()}"
    }
}

/**
 * A photo helps you spot the thing among similarly named ones; the name still does
 * the finding, so a missing photo costs nothing. An empty square stands in for it
 * anyway, so the names stay on one line down the list instead of stepping in and
 * out.
 *
 * 40 dp, which is small enough that the writing beside it decides how tall the row
 * is. It was 52, and a list of things to sell is read for its names.
 */
@Composable
internal fun ItemThumb(photo: String?, onClick: (() -> Unit)? = null) {
    val thumb = remember(photo) { photo?.let { decodePhoto(it) } }
    // Clipped before the click so the ripple stays inside the rounded square, and
    // the click last of all so nothing is painted over the ripple afterwards.
    val square = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
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
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
