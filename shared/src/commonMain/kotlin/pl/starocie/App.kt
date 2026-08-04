package pl.starocie

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.compose.KoinApplication
import pl.starocie.data.FirebaseAuthRepository
import pl.starocie.di.appModule
import pl.starocie.domain.AuthRepository
import pl.starocie.ui.BuyBoxScreen
import pl.starocie.ui.BuyOneScreen
import pl.starocie.ui.HomeScreen
import pl.starocie.ui.SellScreen
import pl.starocie.ui.SignInScreen
import pl.starocie.ui.theme.AppTheme

// Type-safe routes: string routes would need Bundle access to read the buy id,
// and Bundle is Android-only — it compiles there and breaks the iOS build.
@Serializable private data object Home
@Serializable private data object BuyOneRoute
@Serializable private data object BuyBoxRoute
@Serializable private data class BuyBoxItems(val buyId: String)
@Serializable private data object SellRoute

@Composable
fun App() {
    // Auth bootstraps the graph, so it is created before Koin rather than by it.
    val authScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val auth: AuthRepository = remember { FirebaseAuthRepository(scope = authScope) }
    val user by auth.user.collectAsState()

    AppTheme {
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

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onBuyOne = { navController.navigate(BuyOneRoute) },
                onBuyBox = { navController.navigate(BuyBoxRoute) },
                onSell = { navController.navigate(SellRoute) },
            )
        }

        composable<BuyOneRoute> { BuyOneScreen(onDone = { navController.popBackStack() }) }

        composable<BuyBoxRoute> {
            BuyBoxScreen(
                // Replace the price step so "Gotowe" returns home rather than
                // stepping back into a box that has already been created.
                onOpened = { buyId ->
                    navController.navigate(BuyBoxItems(buyId)) {
                        popUpTo(BuyBoxRoute) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable<BuyBoxItems> { entry ->
            BuyOneScreen(
                buyId = entry.toRoute<BuyBoxItems>().buyId,
                onDone = { navController.popBackStack() },
            )
        }

        composable<SellRoute> { SellScreen(onDone = { navController.popBackStack() }) }
    }
}
