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

/** Both users share one workspace; there is no multi-tenancy to configure. */
const val WORKSPACE_ID = "starocie"

/**
 * The repository is bound behind its interface, so which implementation is in use
 * is the only thing that changes between running against Firestore and running
 * against memory. No screen knows the difference.
 */
fun appModule(userId: String) = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<LedgerRepository> {
        FirestoreLedgerRepository(
            firestore = Firebase.firestore,
            workspaceId = WORKSPACE_ID,
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
