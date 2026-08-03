package pl.starocie.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * `Event -> Buy -> Item -> Sell`, each arrow one-to-many.
 *
 * Foreign keys live on the "many" side without exception, so there is no `sellId`
 * on [Item]: an item has many sells and one id could not represent them.
 */

enum class ItemStatus {
    IN_STOCK,

    /** Broken, lost, given away or kept. Resolves the item without proceeds. */
    REMOVED,

    SOLD,
    ;

    val isResolved: Boolean get() = this != IN_STOCK
}

/** A market day or trip. Auto-created per day, with the ISO date as its id. */
data class Event(
    val id: String,
    val date: LocalDate,
    val name: String? = null,
    val note: String? = null,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** One payment. May cover a single thing or a whole box. */
data class Buy(
    val id: String,
    val eventId: String,
    val date: LocalDate,
    val price: Money? = null,
    val name: String? = null,
    val note: String? = null,
    val photoUrls: List<String> = emptyList(),
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Item(
    val id: String,
    /** Null when invented at point of sale, which makes its cost genuinely unknown. */
    val buyId: String? = null,
    val date: LocalDate,
    val name: String? = null,
    val note: String? = null,
    val photoUrls: List<String> = emptyList(),
    /**
     * The **asking** price, never what the item cost. There is no cost field on an
     * item at all — cost lives on [Buy]. Also drives the allocation of a box price
     * across its contents.
     */
    val price: Money? = null,
    val splittable: Boolean = false,
    val status: ItemStatus = ItemStatus.IN_STOCK,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Sell(
    val id: String,
    val itemId: String,
    val eventId: String,
    val date: LocalDate,
    val price: Money,
    val note: String? = null,
    /** True flips the item to [ItemStatus.SOLD]. Always true for non-splittables. */
    val soldCompletely: Boolean = true,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
