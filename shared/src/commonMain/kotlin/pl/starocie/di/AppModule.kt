package pl.starocie.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.starocie.data.InMemoryLedgerRepository
import pl.starocie.domain.LedgerRepository
import pl.starocie.ui.BuyViewModel
import pl.starocie.ui.SellViewModel

/**
 * The repository is bound behind its interface so the Firestore implementation can
 * replace the in-memory one without touching a screen.
 */
val appModule = module {
    single<LedgerRepository> { InMemoryLedgerRepository() }
    viewModel { BuyViewModel(get()) }
    viewModel { SellViewModel(get()) }
}
