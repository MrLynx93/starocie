package pl.starocie.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Credential Manager rather than the old `GoogleSignInClient`: that API is
 * deprecated and on its way out, and this one is what the system account picker
 * already uses, so there is no separate sign-in screen to design.
 */
@Composable
actual fun rememberGoogleSignIn(onResult: (GoogleSignInResult) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The web client id is generated into resources by the google-services plugin,
    // and only when Google is actually enabled on the Firebase project. Looked up
    // by name rather than referenced as R.string, so a project without it still
    // compiles — same leniency the missing google-services.json already gets.
    val webClientId = remember(context) { context.webClientId() } ?: return null

    return {
        scope.launch {
            runCatching {
                val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val response = CredentialManager.create(context).getCredential(
                    // The chooser is a UI: it needs the activity, not the
                    // application context Compose may hand out.
                    context = context.findActivity() ?: context,
                    request = request,
                )
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else {
                    null
                }
            }.onSuccess { token ->
                onResult(
                    token?.let { GoogleSignInResult.Token(it) }
                        ?: GoogleSignInResult.Failed("Google nie dało nam tokenu"),
                )
            }.onFailure { e ->
                // Whatever else happens, the reason lands in logcat. Credential
                // Manager's own messages are often null and its failures reach
                // the screen as one sentence, so without this the only trace of
                // a misconfigured project is a DEVELOPER_ERROR logged by Play
                // services under a tag that has nothing to do with this app.
                Log.w(TAG, "Google sign-in failed", e)
                onResult(
                    if (e is GetCredentialCancellationException) {
                        GoogleSignInResult.Cancelled
                    } else {
                        GoogleSignInResult.Failed(e.describe())
                    },
                )
            }
        }
        Unit
    }
}

private const val TAG = "starocie/google"

/**
 * Credential Manager's exceptions usually carry a null message, so `e.message`
 * alone reaches the screen as an empty line. The class name is not friendly, but
 * a name we can look up beats a blank.
 */
private fun Throwable.describe(): String =
    message?.takeIf { it.isNotBlank() }
        ?: "Nie udało się zalogować przez Google (${this::class.simpleName})"

private fun Context.webClientId(): String? {
    val id = resources.getIdentifier("default_web_client_id", "string", packageName)
    return if (id == 0) null else getString(id).takeIf { it.isNotBlank() }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
