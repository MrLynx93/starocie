package pl.starocie.ui

import androidx.compose.runtime.Composable

/**
 * Not offered on iOS yet.
 *
 * Google sign-in there needs Apple's `GoogleSignIn` SDK added to the Xcode project
 * over Swift Package Manager, a reversed-client-id URL scheme in `Info.plist`, and
 * a Swift entry point to present the sheet from — none of which can be added or
 * verified without the `GoogleService-Info.plist` this repo deliberately does not
 * carry. Returning null hides the button rather than offering one that cannot work.
 */
@Composable
actual fun rememberGoogleSignIn(onResult: (GoogleSignInResult) -> Unit): (() -> Unit)? = null
