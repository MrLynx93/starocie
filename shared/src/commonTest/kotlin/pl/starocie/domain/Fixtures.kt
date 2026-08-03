package pl.starocie.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

internal val T0: Instant = Instant.fromEpochSeconds(1_785_000_000)
internal val D0: LocalDate = LocalDate(2026, 8, 1)
internal val D1: LocalDate = LocalDate(2026, 8, 2)

internal fun event(id: String = "2026-08-01", date: LocalDate = D0) =
    Event(id = id, date = date, createdBy = "u", createdAt = T0, updatedAt = T0)

internal fun buy(
    id: String,
    price: Long?,
    eventId: String = "2026-08-01",
) = Buy(
    id = id,
    eventId = eventId,
    date = D0,
    price = price?.let(::Money),
    createdBy = "u",
    createdAt = T0,
    updatedAt = T0,
)

internal fun item(
    id: String,
    price: Long? = null,
    buyId: String? = null,
    status: ItemStatus = ItemStatus.IN_STOCK,
    quantity: Int = 1,
    name: String = id,
) = Item(
    id = id,
    buyId = buyId,
    name = name,
    date = D0,
    price = price?.let(::Money),
    quantity = quantity,
    status = status,
    createdBy = "u",
    createdAt = T0,
    updatedAt = T0,
)

internal fun sell(
    id: String,
    itemId: String,
    price: Long,
    eventId: String = "2026-08-02",
    soldCompletely: Boolean = true,
) = Sell(
    id = id,
    itemId = itemId,
    eventId = eventId,
    date = D1,
    price = Money(price),
    soldCompletely = soldCompletely,
    createdBy = "u",
    createdAt = T0,
    updatedAt = T0,
)
