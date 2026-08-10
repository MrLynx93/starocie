package pl.starocie.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Recording a buy or a sell merges a stamp into that day's event so the day is
 * certainly there. A merge writes every field it is handed — so what that stamp
 * *has* is the whole of what it can destroy.
 *
 * Sending a whole `EventDoc` is what the bug was: its `name` defaults to null,
 * GitLive encodes defaults, and a giełda named on the way home was blanked by the
 * next sale of the day. Json here stands in for that encoder, `encodeDefaults`
 * matching what GitLive uses, because the question is only which keys go out.
 */
class EventStubTest {

    private val encoder = Json { encodeDefaults = true }

    @Test
    fun the_day_stamp_carries_no_name_to_overwrite_one_with() {
        val stamp = encoder.encodeToString(
            EventStubDoc(
                id = "2026-08-01",
                date = "2026-08-01",
                createdBy = "u",
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        assertFalse(stamp.contains("name"), "a merge that names the field overwrites it")
    }

    /** It still has to say which day it is, or the merge creates nothing usable. */
    @Test
    fun the_day_stamp_still_identifies_the_day() {
        val stamp = encoder.encodeToString(
            EventStubDoc(
                id = "2026-08-01",
                date = "2026-08-01",
                createdBy = "u",
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        assertTrue(stamp.contains("\"id\":\"2026-08-01\""))
        assertTrue(stamp.contains("\"date\":\"2026-08-01\""))
    }

    /**
     * The whole document is what a real write of an event uses, and it must keep
     * carrying the name — that is how naming a giełda reaches Firestore at all.
     */
    @Test
    fun the_whole_document_still_carries_the_name() {
        val doc = encoder.encodeToString(
            EventDoc(
                id = "2026-08-01",
                date = "2026-08-01",
                name = "Wolumen",
                createdBy = "u",
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        assertTrue(doc.contains("\"name\":\"Wolumen\""))
    }
}
