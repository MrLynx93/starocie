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
import pl.starocie.ui.SellingSessionDetailScreen
import pl.starocie.ui.SellingSessionScreen
import pl.starocie.ui.SignInScreen
import pl.starocie.ui.SoldItemScreen
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
@Serializable private data object SellingSessionsRoute
@Serializable private data class SellingSessionRoute(val eventId: String)
@Serializable private data class StockItemRoute(val itemId: String)
@Serializable private data class SoldItemRoute(val itemId: String)

/**
 * @param workspaceId which books this build keeps — the real ones or the test
 *   ones. It has no default on purpose: the host decides, and a build that forgot
 *   to say would otherwise quietly join the workspace we rely on.
 */
@Composable
fun App(workspaceId: String) {
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
                    KoinApplication(application = { modules(appModule(signedIn.uid, workspaceId)) }) {
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
                onSessions = { navController.navigate(SellingSessionsRoute) },
                isDark = theme.mode.isDark,
                onToggleTheme = theme.toggle,
            )
        }

        // One screen, two doors. The route is the whole difference: arriving from
        // "Sprzedaj" adds the button for a thing that was never recorded, and
        // arriving from the magazyn card does not.
        composable<StockRoute> {
            StockScreen(
                selling = false,
                onOpenItem = { itemId -> navController.navigate(StockItemRoute(itemId)) },
                onAddNew = {},
                onDone = { navController.popBackStack() },
            )
        }

        composable<SoldRoute> {
            SoldScreen(
                onOpenItem = { itemId -> navController.navigate(SoldItemRoute(itemId)) },
                onDone = { navController.popBackStack() },
            )
        }

        composable<SellingSessionsRoute> {
            SellingSessionScreen(
                onOpenSession = { eventId -> navController.navigate(SellingSessionRoute(eventId)) },
                onDone = { navController.popBackStack() },
            )
        }

        // A day is a way into the records rather than a separate reading of them, so
        // its rows land on the same two item screens the other lists open.
        composable<SellingSessionRoute> { entry ->
            SellingSessionDetailScreen(
                eventId = entry.toRoute<SellingSessionRoute>().eventId,
                onOpenStockItem = { itemId -> navController.navigate(StockItemRoute(itemId)) },
                onOpenSoldItem = { itemId -> navController.navigate(SoldItemRoute(itemId)) },
                onDone = { navController.popBackStack() },
            )
        }

        // The counterpart of the magazyn's item screen: a thing that has gone still
        // has four numbers that can be wrong, and this is where they are corrected.
        composable<SoldItemRoute> { entry ->
            SoldItemScreen(
                itemId = entry.toRoute<SoldItemRoute>().itemId,
                onDone = { navController.popBackStack() },
            )
        }

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
            StockScreen(
                selling = true,
                onOpenItem = { itemId -> navController.navigate(StockItemRoute(itemId)) },
                onAddNew = { navController.navigate(SellNewRoute) },
                onDone = { navController.popBackStack() },
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
