package pl.starocie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The flavour is the only thing that differs between the two builds, and
        // this is where it arrives: prod keeps the real books, the test build its
        // own workspace, and no screen below here can tell which it is in.
        setContent { App(workspaceId = BuildConfig.WORKSPACE_ID) }
    }
}
