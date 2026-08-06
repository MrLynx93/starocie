package pl.starocie.ui

import androidx.compose.runtime.Composable

sealed interface GoogleSignInResult {
    /** An ID token to hand to Firebase. */
    data class Token(val idToken: String) : GoogleSignInResult

    /** The sheet was dismissed. Not an error, and nothing to report. */
    data object Cancelled : GoogleSignInResult

    data class Failed(val message: String) : GoogleSignInResult
}

/**
 * Opens the platform's own Google chooser and hands back an ID token.
 *
 * **Returns null when this build cannot offer it**, and the sign-in screen then
 * shows no Google button at all — a button that always fails is worse than one that
 * is not there. That happens when Google is not enabled on the Firebase project (no
 * OAuth web client reaches `google-services.json`), and on iOS, where the Google
 * SDK is not part of the Xcode project yet.
 */
@Composable
expect fun rememberGoogleSignIn(onResult: (GoogleSignInResult) -> Unit): (() -> Unit)?
