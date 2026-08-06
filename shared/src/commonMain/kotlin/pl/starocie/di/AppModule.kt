package pl.starocie.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.starocie.data.FirestoreLedgerRepository
import pl.starocie.data.InMemoryLedgerRepository
import pl.starocie.domain.LedgerRepository
import pl.starocie.ui.BuyBoxViewModel
import pl.starocie.ui.BuyOneViewModel
import pl.starocie.ui.SellViewModel

/**
 * The real books. Both users share one workspace — this is not multi-tenancy, it
 * is the one place two people's things are kept.
 *
 * A second workspace exists beside it so that trying something out cannot land in
 * the figures we actually rely on. Which one a build talks to is *passed in*, from
 * `BuildConfig.WORKSPACE_ID` on Android, and never defaulted along the way: a
 * default is how a test build ends up writing to the real books by omission.
 */
const val PROD_WORKSPACE_ID = "starocie-prod"

/**
 * The repository is bound behind its interface, so which implementation is in use
 * is the only thing that changes between running against Firestore and running
 * against memory. No screen knows the difference.
 */
fun appModule(userId: String, workspaceId: String) = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<LedgerRepository> {
        FirestoreLedgerRepository(
            firestore = Firebase.firestore,
            workspaceId = workspaceId,
            userId = userId,
            scope = get(),
        )
    }

    viewModel { BuyBoxViewModel(get()) }
    viewModel { BuyOneViewModel(get()) }
    viewModel { SellViewModel(get()) }
}

/** Same graph without Firebase, for running the UI on a machine with no config. */
fun offlineModule() = module {
    single<LedgerRepository> { InMemoryLedgerRepository() }
    viewModel { BuyBoxViewModel(get()) }
    viewModel { BuyOneViewModel(get()) }
    viewModel { SellViewModel(get()) }
}
