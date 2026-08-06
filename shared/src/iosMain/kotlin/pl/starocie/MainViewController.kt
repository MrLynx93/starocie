package pl.starocie

import androidx.compose.ui.window.ComposeUIViewController
import pl.starocie.di.PROD_WORKSPACE_ID

/**
 * iOS has no test build yet — the split lives in Android's product flavours, and
 * the phones we sell from are the Android ones. So this names the real workspace
 * outright rather than pretending to choose: anything running here, simulator
 * included, is writing to the books we rely on.
 */
@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController { App(workspaceId = PROD_WORKSPACE_ID) }
