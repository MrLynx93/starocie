package pl.starocie.data

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import pl.starocie.domain.Buy
import pl.starocie.domain.Event
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.Money
import pl.starocie.domain.Sell

/**
 * The Firestore wire format, kept separate from the domain models so no
 * serialization concern leaks into them.
 *
 * Dates are ISO strings and timestamps epoch milliseconds — explicit primitives
 * rather than relying on whichever serializers the datetime library exposes.
 * [Money] serialises as a plain integer through its own serializer, so the stored
 * field really is just `price`.
 */

@Serializable
internal data class WorkspaceDoc(
    val members: List<String> = emptyList(),
    val currency: String = "PLN",
)

@Serializable
internal data class EventDoc(
    val id: String = "",
    val date: String = "",
    val name: String? = null,
    val note: String? = null,
    val createdBy: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
internal data class BuyDoc(
    val id: String = "",
    val eventId: String = "",
    val date: String = "",
    val price: Money? = null,
    val name: String? = null,
    val note: String? = null,
    val photoUrls: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
internal data class ItemDoc(
    val id: String = "",
    val buyId: String? = null,
    val date: String = "",
    val name: String = "",
    val note: String? = null,
    val photoUrls: List<String> = emptyList(),
    val photo: String? = null,
    val price: Money? = null,
    val quantity: Int = 1,
    val status: String = "IN_STOCK",
    val createdBy: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
internal data class SellDoc(
    val id: String = "",
    val itemId: String = "",
    val eventId: String = "",
    val date: String = "",
    val price: Money = Money.ZERO,
    val note: String? = null,
    val quantity: Int = 1,
    val soldCompletely: Boolean = true,
    val createdBy: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

internal fun Event.toDoc() = EventDoc(
    id = id,
    date = date.toString(),
    name = name,
    note = note,
    createdBy = createdBy,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

internal fun EventDoc.toDomain() = Event(
    id = id,
    date = LocalDate.parse(date),
    name = name,
    note = note,
    createdBy = createdBy,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun Buy.toDoc() = BuyDoc(
    id = id,
    eventId = eventId,
    date = date.toString(),
    price = price,
    name = name,
    note = note,
    photoUrls = photoUrls,
    createdBy = createdBy,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

internal fun BuyDoc.toDomain() = Buy(
    id = id,
    eventId = eventId,
    date = LocalDate.parse(date),
    price = price,
    name = name,
    note = note,
    photoUrls = photoUrls,
    createdBy = createdBy,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun Item.toDoc() = ItemDoc(
    id = id,
    buyId = buyId,
    date = date.toString(),
    name = name,
    note = note,
    photoUrls = photoUrls,
    photo = photo,
    price = price,
    quantity = quantity,
    status = status.name,
    createdBy = createdBy,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

internal fun ItemDoc.toDomain() = Item(
    id = id,
    buyId = buyId,
    date = LocalDate.parse(date),
    name = name,
    note = note,
    photoUrls = photoUrls,
    photo = photo,
    price = price,
    quantity = quantity.coerceAtLeast(1),
    // An unrecognised status must not drop the item out of sight; treat it as stock.
    status = ItemStatus.entries.firstOrNull { it.name == status } ?: ItemStatus.IN_STOCK,
    createdBy = createdBy,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun Sell.toDoc() = SellDoc(
    id = id,
    itemId = itemId,
    eventId = eventId,
    date = date.toString(),
    price = price,
    note = note,
    quantity = quantity,
    soldCompletely = soldCompletely,
    createdBy = createdBy,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

internal fun SellDoc.toDomain() = Sell(
    id = id,
    itemId = itemId,
    eventId = eventId,
    date = LocalDate.parse(date),
    price = price,
    note = note,
    // A sale that took nothing cannot have happened; records written before the
    // field existed have no value here at all and come back as one.
    quantity = quantity.coerceAtLeast(1),
    soldCompletely = soldCompletely,
    createdBy = createdBy,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)
