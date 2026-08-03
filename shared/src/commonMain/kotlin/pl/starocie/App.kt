package pl.starocie

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.compose.KoinApplication
import pl.starocie.di.appModule
import pl.starocie.ui.BuyScreen
import pl.starocie.ui.HomeScreen
import pl.starocie.ui.SellScreen

private const val HOME = "home"
private const val BUY = "buy"
private const val SELL = "sell"

@Composable
fun App() {
    KoinApplication(application = { modules(appModule) }) {
        MaterialTheme {
            Surface {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = HOME) {
                    composable(HOME) {
                        HomeScreen(
                            onBuy = { navController.navigate(BUY) },
                            onSell = { navController.navigate(SELL) },
                        )
                    }
                    composable(BUY) {
                        BuyScreen(onDone = { navController.popBackStack() })
                    }
                    composable(SELL) {
                        SellScreen(onDone = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
