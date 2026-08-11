package ru.flawden.baskovmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.flawden.baskovmusic.ui.BaskovApp
import ru.flawden.baskovmusic.ui.BaskovViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: BaskovViewModel = viewModel()
            BaskovApp(viewModel)
        }
    }
}
