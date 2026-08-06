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
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.data.FirebaseAuthRepository
import pl.starocie.di.appModule
import pl.starocie.domain.AuthRepository
import pl.starocie.ui.BuyBoxScreen
import pl.starocie.ui.BuyOneScreen
import pl.starocie.ui.HomeScreen
import pl.starocie.ui.SellNewItemScreen
import pl.starocie.ui.SellScreen
import pl.starocie.ui.SignInScreen
import pl.starocie.ui.SoldScreen
import pl.starocie.ui.StockItemScreen
import pl.starocie.ui.StockScreen
import pl.starocie.ui.theme.AppTheme
import pl.starocie.ui.theme.ThemeChoice
import pl.starocie.ui.theme.rememberThemeChoice

// Type-safe routes: string routes would need Bundle access to read the buy id,
// and Bundle is Android-only — it compiles there and breaks the iOS build.
@Serializable private data object Home
@Serializable private data object BuyOneRoute
@Serializable private data object BuyBoxRoute
@Serializable private data class BuyBoxItems(val buyId: String)
@Serializable private data object SellRoute
@Serializable private data object SellNewRoute
@Serializable private data object StockRoute
@Serializable private data object SoldRoute
@Serializable private data class StockItemRoute(val itemId: String)

@Composable
fun App() {
    // Auth bootstraps the graph, so it is created before Koin rather than by it.
    val authScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val auth: AuthRepository = remember { FirebaseAuthRepository(scope = authScope) }
    val user by auth.user.collectAsState()

    val theme = rememberThemeChoice()

    AppTheme(dark = theme.mode.isDark) {
        Surface {
            val signedIn = user
            if (signedIn == null) {
                SignInScreen(auth)
            } else {
                // Keyed on uid so signing in as the other person rebuilds the graph
                // rather than leaving repositories pointed at the previous user.
                key(signedIn.uid) {
                    KoinApplication(application = { modules(appModule(signedIn.uid)) }) {
                        MainNavigation(theme)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNavigation(theme: ThemeChoice) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onBuyOne = { navController.navigate(BuyOneRoute) },
                onBuyBox = { navController.navigate(BuyBoxRoute) },
                onSell = { navController.navigate(SellRoute) },
                onStock = { navController.navigate(StockRoute) },
                onSold = { navController.navigate(SoldRoute) },
                isDark = theme.mode.isDark,
                onToggleTheme = theme.toggle,
            )
        }

        composable<StockRoute> {
            StockScreen(
                onOpenItem = { itemId -> navController.navigate(StockItemRoute(itemId)) },
                onDone = { navController.popBackStack() },
            )
        }

        composable<SoldRoute> { SoldScreen(onDone = { navController.popBackStack() }) }

        // Only the id travels: the item itself is read from the ledger, so the
        // screen follows every edit and every sale rather than showing a snapshot
        // taken when it was opened.
        composable<StockItemRoute> { entry ->
            StockItemScreen(
                itemId = entry.toRoute<StockItemRoute>().itemId,
                onDone = { navController.popBackStack() },
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

        composable<SellRoute> {
            SellScreen(
                onDone = { navController.popBackStack() },
                onAddNew = { navController.navigate(SellNewRoute) },
                onOpenItem = { itemId -> navController.navigate(StockItemRoute(itemId)) },
            )
        }

        // The form shares the sell screen's view model rather than owning one:
        // the search box that opened it seeds the name, and a completed sale has
        // to clear both. Resolving it against the sell entry is what makes the
        // two screens one flow instead of two.
        composable<SellNewRoute> {
            val sellEntry = remember(it) { navController.getBackStackEntry(SellRoute) }
            SellNewItemScreen(
                viewModel = koinViewModel(viewModelStoreOwner = sellEntry),
                onDone = { navController.popBackStack() },
            )
        }
    }
}
