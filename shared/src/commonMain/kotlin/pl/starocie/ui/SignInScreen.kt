package pl.starocie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.starocie.domain.AuthRepository
import pl.starocie.domain.SignInResult

/**
 * The way in, and the first thing either of us sees.
 *
 * The stall stands in the middle at a size worth looking at — it is the only screen
 * with room for it, and an app that opens on a bare pair of fields could be
 * anything. Google sits under the password rather than above it: the e-mail pair is
 * what has always worked here, and the button only appears at all on a build that
 * can honour it.
 */
@Composable
fun SignInScreen(auth: AuthRepository) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val signInWithEmail = {
        busy = true
        error = null
        scope.launch {
            when (val result = auth.signIn(email, password)) {
                is SignInResult.Success -> notice = null
                // Only reachable from the Google path; a password sign-in that
                // needed a password would be a contradiction.
                is SignInResult.NeedsPassword -> Unit
                is SignInResult.Failed -> error = result.message
            }
            busy = false
        }
        Unit
    }

    val signInWithGoogle = rememberGoogleSignIn { result ->
        when (result) {
            is GoogleSignInResult.Cancelled -> busy = false
            is GoogleSignInResult.Failed -> {
                error = result.message
                busy = false
            }
            is GoogleSignInResult.Token -> scope.launch {
                when (val signIn = auth.signInWithGoogle(result.idToken)) {
                    is SignInResult.Success -> notice = null
                    is SignInResult.NeedsPassword -> {
                        signIn.email?.let { email = it }
                        notice = "Na ten e-mail mamy już hasło. Zalogujmy się nim raz, " +
                            "a Google podepniemy do tego samego konta."
                    }
                    is SignInResult.Failed -> error = signIn.message
                }
                busy = false
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))

            StallMark(modifier = Modifier.size(112.dp))

            Spacer(Modifier.height(20.dp))

            Text(
                "Nasze starocie",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Wspólny zeszyt na to, co kupujemy i sprzedajemy. " +
                    "Zalogujmy się i ruszamy na targ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(36.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                singleLine = true,
                label = { Text("E-mail") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                singleLine = true,
                label = { Text("Hasło") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = signInWithEmail,
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Zaloguj", fontWeight = FontWeight.Medium)
                }
            }

            signInWithGoogle?.let { start ->
                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        "albo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = { busy = true; error = null; start() },
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    // Google's own mark, their path data unaltered — not something
                    // of ours that resembles it. Their terms are specific about the
                    // logo not being redrawn or recoloured.
                    Image(
                        imageVector = GoogleLogo,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Zaloguj przez Google")
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
