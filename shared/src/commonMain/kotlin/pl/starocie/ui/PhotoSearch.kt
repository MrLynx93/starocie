package pl.starocie.ui

import androidx.compose.runtime.Composable

/**
 * Hands the photo to Google, so a thing can be looked up without typing a word.
 *
 * This is the question a stall raises constantly — what *is* this, and what do
 * people ask for one — and it is a question a picture answers far better than a
 * name we made up ourselves while holding the object.
 *
 * Null when the platform cannot do it, exactly as `rememberGoogleSignIn` is null
 * when the build cannot honour it: a button that always fails is worse than a
 * button that is not there.
 *
 * **It is not a plain browser URL, and cannot be.** Google's search-by-image takes
 * a *public URL* or an upload, and this photo is neither — it lives as Base64
 * inside the item document, deliberately, because Cloud Storage needs a paid plan.
 * So the picture is handed over as an image instead: Lens on Android, the share
 * sheet on iOS. That is the same search, arriving by the only door open to a
 * photograph that exists nowhere but in our own document. If the photos ever do
 * move to Cloud Storage, `lens.google.com/uploadbyurl` becomes possible and this
 * is the one place that has to change.
 */
@Composable
expect fun rememberPhotoSearch(): ((String) -> Unit)?
