package pl.starocie.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
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

    /**
     * A Google token that could not sign in on its own because the e-mail already
     * has a password. Held until the password proves who this is, then linked — so
     * the person ends up on the account they already had rather than a second one.
     */
    private var pendingGoogleIdToken: String? = null

    init {
        scope.launch {
            auth.authStateChanged.collect { _user.value = it?.toAppUser() }
        }
    }

    override suspend fun signIn(email: String, password: String): SignInResult =
        try {
            auth.signInWithEmailAndPassword(email.trim(), password)
            linkPendingGoogle()
            SignInResult.Success
        } catch (e: Exception) {
            // Surfaced verbatim: with two known users a precise message is more use
            // than a friendly one, and there is nobody to leak account existence to.
            SignInResult.Failed(e.message ?: "Nie udało się zalogować")
        }

    override suspend fun signInWithGoogle(idToken: String): SignInResult =
        try {
            auth.signInWithCredential(GoogleAuthProvider.credential(idToken, null))
            SignInResult.Success
        } catch (e: Exception) {
            if (e.isAccountCollision()) {
                // Not a failure — the account exists, it just has to be recognised
                // by its password once before Google can be attached to it.
                pendingGoogleIdToken = idToken
                SignInResult.NeedsPassword(null)
            } else {
                SignInResult.Failed(e.message ?: "Nie udało się zalogować przez Google")
            }
        }

    override suspend fun signOut() {
        pendingGoogleIdToken = null
        auth.signOut()
    }

    /**
     * Attaches a held-back Google credential to the user who just proved a
     * password. A failure here is deliberately swallowed: the sign-in itself
     * worked, and the only cost of an unlinked credential is being asked for the
     * password again next time.
     */
    private suspend fun linkPendingGoogle() {
        val token = pendingGoogleIdToken ?: return
        pendingGoogleIdToken = null
        runCatching {
            auth.currentUser?.linkWithCredential(GoogleAuthProvider.credential(token, null))
        }
    }
}

/**
 * Firebase reports the collision by code rather than by type through GitLive, and
 * the wording differs between platforms — so match on the code, which does not.
 */
private fun Exception.isAccountCollision(): Boolean {
    val text = message.orEmpty().lowercase()
    return "account-exists-with-different-credential" in text ||
        "accountexistswithdifferentcredential" in text ||
        "an account already exists with the same email" in text
}

private fun dev.gitlive.firebase.auth.FirebaseUser.toAppUser() = AppUser(uid = uid, email = email)
