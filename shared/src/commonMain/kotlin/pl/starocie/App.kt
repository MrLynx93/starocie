package pl.starocie

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.compose.KoinApplication
import pl.starocie.data.FirebaseAuthRepository
import pl.starocie.di.appModule
import pl.starocie.domain.AuthRepository
import pl.starocie.ui.BuyScreen
import pl.starocie.ui.HomeScreen
import pl.starocie.ui.SellScreen
import pl.starocie.ui.SignInScreen

private const val HOME = "home"
private const val BUY = "buy"
private const val SELL = "sell"

@Composable
fun App() {
    // Auth bootstraps the graph, so it is created before Koin rather than by it.
    val authScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val auth: AuthRepository = remember { FirebaseAuthRepository(scope = authScope) }
    val user by auth.user.collectAsState()

    MaterialTheme {
        Surface {
            val signedIn = user
            if (signedIn == null) {
                SignInScreen(auth)
            } else {
                // Keyed on uid so signing in as the other person rebuilds the graph
                // rather than leaving repositories pointed at the previous user.
                key(signedIn.uid) {
                    KoinApplication(application = { modules(appModule(signedIn.uid)) }) {
                        MainNavigation()
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(
                onBuy = { navController.navigate(BUY) },
                onSell = { navController.navigate(SELL) },
            )
        }
        composable(BUY) { BuyScreen(onDone = { navController.popBackStack() }) }
        composable(SELL) { SellScreen(onDone = { navController.popBackStack() }) }
    }
}
