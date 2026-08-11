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
/**
 * The never-recorded form, opened from today's giełda rather than from the sell list.
 * It carries the day's id only so the screen behind it can be found again, that entry
 * being where the form's state lives.
 */
@Serializable private data class SellingSessionSellNewRoute(val eventId: String)
/**
 * [selling] is false only from a giełda that has been and gone: the item screen is
 * the same either way, minus the one button that would write a new sale into today.
 * Today's own giełda keeps it — there the sale lands in the very day being read.
 */
@Serializable private data class StockItemRoute(val itemId: String, val selling: Boolean = true)
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
                // Straight onto the day itself, past the list it would be found in:
                // the card is only there while that day is the one being had.
                onTodaySession = { eventId -> navController.navigate(SellingSessionRoute(eventId)) },
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
            val eventId = entry.toRoute<SellingSessionRoute>().eventId
            SellingSessionDetailScreen(
                eventId = eventId,
                onOpenStockItem = { itemId, selling ->
                    navController.navigate(StockItemRoute(itemId, selling = selling))
                },
                onOpenSoldItem = { itemId -> navController.navigate(SoldItemRoute(itemId)) },
                // The very list the home screen's "Sprzedaj" opens — what goes at a
                // giełda is mostly what we bought at another one, so the whole
                // magazyn is what has to be searchable from the stall.
                onSell = { navController.navigate(SellRoute) },
                onAddNew = { navController.navigate(SellingSessionSellNewRoute(eventId)) },
                onDone = { navController.popBackStack() },
            )
        }

        // The same form the sell list opens, sharing its view model with the day
        // behind it for the same reason: the screen that pressed the button is the
        // one holding the half-filled form, and a sale has to clear it there.
        composable<SellingSessionSellNewRoute> { entry ->
            val eventId = entry.toRoute<SellingSessionSellNewRoute>().eventId
            val sessionEntry = remember(entry) {
                navController.getBackStackEntry(SellingSessionRoute(eventId))
            }
            SellNewItemScreen(
                viewModel = koinViewModel(viewModelStoreOwner = sessionEntry),
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
            val route = entry.toRoute<StockItemRoute>()
            StockItemScreen(
                itemId = route.itemId,
                onDone = { navController.popBackStack() },
                selling = route.selling,
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
