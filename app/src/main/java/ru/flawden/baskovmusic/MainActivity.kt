package ru.flawden.baskovmusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.flawden.baskovmusic.ui.BaskovApp
import ru.flawden.baskovmusic.ui.BaskovViewModel

class MainActivity : ComponentActivity() {
    private var openNowPlayingRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openNowPlayingRequest = intent.requestsNowPlaying()
        setContent {
            val viewModel: BaskovViewModel = viewModel()
            BaskovApp(
                viewModel = viewModel,
                openNowPlayingRequest = openNowPlayingRequest,
                onNowPlayingRequestConsumed = { openNowPlayingRequest = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.requestsNowPlaying()) {
            openNowPlayingRequest = true
        }
    }

    private fun Intent?.requestsNowPlaying(): Boolean =
        this?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "ru.flawden.baskovmusic.OPEN_NOW_PLAYING"
    }
}
