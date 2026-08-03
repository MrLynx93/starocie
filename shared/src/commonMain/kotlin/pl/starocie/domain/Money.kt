package pl.starocie.domain

import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An amount of money in minor units (grosz). Never a float, never a bare [Long].
 *
 * The type is what enforces the unit, which is why the fields that hold it are
 * named plainly (`price`, not `priceMinor`).
 */
@Serializable(with = MoneySerializer::class)
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(minor + other.minor)

    operator fun minus(other: Money): Money = Money(minor - other.minor)

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    companion object {
        val ZERO: Money = Money(0)
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.minor })

/** Polish formatting: a comma for the decimal separator. Only ever at the UI edge. */
fun Money.format(): String {
    val sign = if (minor < 0) "-" else ""
    val abs = if (minor < 0) -minor else minor
    val grosz = (abs % 100).toString().padStart(2, '0')
    return "$sign${abs / 100},$grosz zł"
}

/**
 * For pre-filling an editable price field. Whole amounts lose the decimals, so a
 * 50 zł ask offers "50" rather than "50.0".
 */
fun Money.toInputText(): String {
    val grosz = minor % 100
    return if (grosz == 0L) "${minor / 100}" else format().removeSuffix(" zł")
}

/**
 * Parses user input such as "12", "12,50" or "12.5". Returns null when it is not a
 * number, so the caller can simply leave the value unset rather than guess.
 */
fun parseMoney(input: String): Money? {
    val cleaned = input.trim().replace(',', '.').replace(" ", "")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value < 0) return null
    // Round rather than truncate so "0.999" does not silently become 0.99.
    return Money((value * 100).let { if (it < 0) it - 0.5 else it + 0.5 }.toLong())
}

/** Stored as a plain integer, so the Firestore field is just `price`. */
object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("pl.starocie.domain.Money", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Money) = encoder.encodeLong(value.minor)

    override fun deserialize(decoder: Decoder): Money = Money(decoder.decodeLong())
}
