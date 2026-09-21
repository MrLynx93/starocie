package pl.starocie

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
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
import pl.starocie.ui.SessionDetailScreen
import pl.starocie.ui.SessionsScreen
import pl.starocie.ui.SignInScreen
import pl.starocie.ui.SoldItemScreen
import pl.starocie.ui.SoldScreen
import pl.starocie.ui.StockItemScreen
import pl.starocie.ui.StockScreen
import pl.starocie.ui.WriteErrorOverlay
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
/**
 * The two lists of days, which are one screen: [buying] false is the giełdy, true the
 * days we only shopped on. A parameter rather than a second route, because the same
 * card in the same place on the home screen opens each of them.
 */
@Serializable private data class SessionsRoute(val buying: Boolean = false)
@Serializable private data class SessionRoute(val eventId: String)
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
                        WriteErrorOverlay { MainNavigation(theme) }
                    }
                }
            }
        }
    }
}

/**
 * Everything this app navigates goes through here, and every one of them is a
 * [goOnce] or a [backOnce] rather than a bare `navigate` / `popBackStack`.
 *
 * **A screen keeps its buttons live while it is leaving.** Navigating does not
 * remove the old destination from the composition: it stays drawn, and clickable,
 * for as long as the transition between the two takes. So a second tap on "Wstecz"
 * — the tap a phone that is thinking invites, and the one a thumb gives anyway —
 * arrives at a screen that has already been popped, and pops the screen underneath
 * it as well.
 *
 * One screen underneath Home is nothing, and a `NavHost` whose back stack is empty
 * **draws nothing at all**: no destination, no content, just the surface colour. The
 * app is still running, still signed in and still syncing, and the screen is a blank
 * rectangle that nothing but a restart comes back from — black on the dark palette
 * and white on the light one, which is the whole of what is left when the last
 * screen goes.
 *
 * Nothing protects against it by itself, either. The system's own back is safe —
 * the navigation library disables its handler once there is a single entry left, so
 * the press finishes the activity like any other app — but a `popBackStack()` from
 * a button of our own goes straight past that.
 *
 * The guard is the entry rather than a lifecycle state or a flag: a destination may
 * only navigate while it is still the one on top, which it stops being the instant
 * the first tap lands. That drops the second tap and leaves everything else exactly
 * as it was, including the pops that come from a `LaunchedEffect` rather than a
 * finger — a thing leaving the magazyn takes its screen with it while that screen
 * is still the current one, so it is never what is being dropped.
 */
@Composable
private fun MainNavigation(theme: ThemeChoice) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> { entry ->
            HomeScreen(
                onBuyOne = { navController.goOnce(entry, BuyOneRoute) },
                onBuyBox = { navController.goOnce(entry, BuyBoxRoute) },
                onSell = { navController.goOnce(entry, SellRoute) },
                onStock = { navController.goOnce(entry, StockRoute) },
                onSold = { navController.goOnce(entry, SoldRoute) },
                onSessions = { navController.goOnce(entry, SessionsRoute()) },
                onBuyingSessions = { navController.goOnce(entry, SessionsRoute(buying = true)) },
                // Straight onto the day itself, past the list it would be found in:
                // the card is only there while that day is the one being had.
                onTodaySession = { eventId -> navController.goOnce(entry, SessionRoute(eventId)) },
                isDark = theme.mode.isDark,
                onToggleTheme = theme.toggle,
            )
        }

        // One screen, two doors. The route is the whole difference: arriving from
        // "Sprzedaj" adds the button for a thing that was never recorded, and
        // arriving from the magazyn card does not.
        composable<StockRoute> { entry ->
            StockScreen(
                selling = false,
                onOpenItem = { itemId -> navController.goOnce(entry, StockItemRoute(itemId)) },
                onAddNew = {},
                onDone = { navController.backOnce(entry) },
            )
        }

        composable<SoldRoute> { entry ->
            SoldScreen(
                onOpenItem = { itemId -> navController.goOnce(entry, SoldItemRoute(itemId)) },
                onDone = { navController.backOnce(entry) },
            )
        }

        composable<SessionsRoute> { entry ->
            SessionsScreen(
                buying = entry.toRoute<SessionsRoute>().buying,
                onOpenSession = { eventId -> navController.goOnce(entry, SessionRoute(eventId)) },
                onDone = { navController.backOnce(entry) },
            )
        }

        // A day is a way into the records rather than a separate reading of them, so
        // its rows land on the same two item screens the other lists open.
        composable<SessionRoute> { entry ->
            val eventId = entry.toRoute<SessionRoute>().eventId
            SessionDetailScreen(
                eventId = eventId,
                onOpenStockItem = { itemId, selling ->
                    navController.goOnce(entry, StockItemRoute(itemId, selling = selling))
                },
                onOpenSoldItem = { itemId -> navController.goOnce(entry, SoldItemRoute(itemId)) },
                // The very list the home screen's "Sprzedaj" opens — what goes at a
                // giełda is mostly what we bought at another one, so the whole
                // magazyn is what has to be searchable from the stall.
                onSell = { navController.goOnce(entry, SellRoute) },
                onDone = { navController.backOnce(entry) },
            )
        }

        // The counterpart of the magazyn's item screen: a thing that has gone still
        // has four numbers that can be wrong, and this is where they are corrected.
        composable<SoldItemRoute> { entry ->
            SoldItemScreen(
                itemId = entry.toRoute<SoldItemRoute>().itemId,
                onDone = { navController.backOnce(entry) },
            )
        }

        // Only the id travels: the item itself is read from the ledger, so the
        // screen follows every edit and every sale rather than showing a snapshot
        // taken when it was opened.
        composable<StockItemRoute> { entry ->
            val route = entry.toRoute<StockItemRoute>()
            StockItemScreen(
                itemId = route.itemId,
                onDone = { navController.backOnce(entry) },
                selling = route.selling,
            )
        }

        composable<BuyOneRoute> { entry ->
            BuyOneScreen(onDone = { navController.backOnce(entry) })
        }

        composable<BuyBoxRoute> { entry ->
            BuyBoxScreen(
                // Replace the price step so "Gotowe" returns home rather than
                // stepping back into a box that has already been created.
                onOpened = { buyId ->
                    navController.goOnce(entry, BuyBoxItems(buyId)) {
                        popUpTo(BuyBoxRoute) { inclusive = true }
                    }
                },
                onCancel = { navController.backOnce(entry) },
            )
        }

        composable<BuyBoxItems> { entry ->
            BuyOneScreen(
                buyId = entry.toRoute<BuyBoxItems>().buyId,
                onDone = { navController.backOnce(entry) },
            )
        }

        composable<SellRoute> { entry ->
            StockScreen(
                selling = true,
                onOpenItem = { itemId -> navController.goOnce(entry, StockItemRoute(itemId)) },
                onAddNew = { navController.goOnce(entry, SellNewRoute) },
                onDone = { navController.backOnce(entry) },
            )
        }

        // The form shares the sell screen's view model rather than owning one:
        // the search box that opened it seeds the name, and a completed sale has
        // to clear both. Resolving it against the sell entry is what makes the
        // two screens one flow instead of two.
        composable<SellNewRoute> { entry ->
            val sellEntry = remember(entry) { navController.getBackStackEntry(SellRoute) }
            SellNewItemScreen(
                viewModel = koinViewModel(viewModelStoreOwner = sellEntry),
                onDone = { navController.backOnce(entry) },
            )
        }
    }
}

/**
 * Go to [route], but only if [from] is still the screen being looked at.
 *
 * The tap that opened the next screen leaves this one composed and live behind it,
 * so without this a thumb landing twice on "Sprzedaj" opens the sell list twice and
 * the two taps back that follow only undo one of them each. See [MainNavigation] for
 * what that costs at the bottom of the stack.
 */
private fun NavController.goOnce(
    from: NavBackStackEntry,
    route: Any,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (currentBackStackEntry !== from) return
    navigate(route, builder)
}

/**
 * Back out of [from], but only once, and never off the end of the stack.
 *
 * Two guards rather than one, because they answer different halves of the same
 * accident. The first is that [from] has to still be the current screen: a second
 * tap arriving while the first one is still sliding away is a tap on a screen that
 * has already gone, and it would pop whatever is underneath. The second is the floor
 * — with nothing to go back to there is nothing to do, Home being where "Wstecz"
 * stops and the phone's own back takes over — and it is what makes a blank screen
 * unreachable however the taps land, rather than merely unlikely.
 */
private fun NavController.backOnce(from: NavBackStackEntry) {
    if (currentBackStackEntry !== from) return
    if (previousBackStackEntry == null) return
    popBackStack()
}
