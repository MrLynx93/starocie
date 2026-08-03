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

/** Stored as a plain integer, so the Firestore field is just `price`. */
object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("pl.starocie.domain.Money", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Money) = encoder.encodeLong(value.minor)

    override fun deserialize(decoder: Decoder): Money = Money(decoder.decodeLong())
}
