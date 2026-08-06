package pl.starocie.domain

import kotlinx.coroutines.flow.StateFlow

data class AppUser(val uid: String, val email: String?)

sealed interface SignInResult {
    data object Success : SignInResult

    /**
     * Google was accepted, but this e-mail already has a password on it. Firebase
     * will not merge the two by itself, so the password is asked for once and the
     * Google account is attached to the same user — after which either route works.
     */
    data class NeedsPassword(val email: String?) : SignInResult

    data class Failed(val message: String) : SignInResult
}

/**
 * Two known people share one workspace. Sign-in exists so the security rules have a
 * uid to check against `members` — not to support arbitrary users.
 *
 * **One person is one account, however they sign in.** The uid is what the rules
 * check and what `createdBy` records, so a second uid for the same human would look
 * like a third member of a two-person workspace. That is why the Google credential
 * is linked onto the existing user rather than allowed to open a new one.
 */
interface AuthRepository {
    val user: StateFlow<AppUser?>

    suspend fun signIn(email: String, password: String): SignInResult

    /**
     * Signs in with an ID token obtained from the platform's Google flow. Returns
     * [SignInResult.NeedsPassword] when the e-mail is already held by a password
     * account; the token is remembered, and the next successful [signIn] links it.
     */
    suspend fun signInWithGoogle(idToken: String): SignInResult

    suspend fun signOut()
}
