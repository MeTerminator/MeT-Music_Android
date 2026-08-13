package top.met6.music.mobile

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MeT-Music_Android",
    ) {
        App(Unit)
    }
}
