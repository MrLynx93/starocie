package pl.starocie.domain

import kotlinx.coroutines.flow.StateFlow

data class AppUser(val uid: String, val email: String?)

sealed interface SignInResult {
    data object Success : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Two known people share one workspace. Sign-in exists so the security rules have a
 * uid to check against `members` — not to support arbitrary users.
 */
interface AuthRepository {
    val user: StateFlow<AppUser?>
    suspend fun signIn(email: String, password: String): SignInResult
    suspend fun signOut()
}
