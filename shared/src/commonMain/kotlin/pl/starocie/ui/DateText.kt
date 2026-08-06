package pl.starocie.ui

import kotlinx.datetime.LocalDate
import pl.starocie.domain.isLongAgo

/**
 * How a date is said on screen.
 *
 * The sentinel behind a never-recorded purchase is not a date anybody chose, so it
 * is never shown as one. Every screen goes through here rather than calling
 * `toString()`, so there is one place to be wrong instead of six.
 */
fun LocalDate.asText(): String = if (isLongAgo()) "Dawno temu" else toString()
