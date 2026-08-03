package pl.starocie.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.starocie.domain.AppUser
import pl.starocie.domain.AuthRepository
import pl.starocie.domain.SignInResult

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    scope: CoroutineScope,
) : AuthRepository {

    private val _user = MutableStateFlow(auth.currentUser?.toAppUser())
    override val user: StateFlow<AppUser?> = _user.asStateFlow()

    init {
        scope.launch {
            auth.authStateChanged.collect { _user.value = it?.toAppUser() }
        }
    }

    override suspend fun signIn(email: String, password: String): SignInResult =
        try {
            auth.signInWithEmailAndPassword(email.trim(), password)
            SignInResult.Success
        } catch (e: Exception) {
            // Surfaced verbatim: with two known users a precise message is more use
            // than a friendly one, and there is nobody to leak account existence to.
            SignInResult.Failed(e.message ?: "Nie udało się zalogować")
        }

    override suspend fun signOut() = auth.signOut()
}

private fun dev.gitlive.firebase.auth.FirebaseUser.toAppUser() = AppUser(uid = uid, email = email)
