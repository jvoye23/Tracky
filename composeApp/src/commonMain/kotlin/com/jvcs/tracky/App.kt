package com.jvcs.tracky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.navigation.NavigationRoot
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App() {
    TrackyTheme {
        val mainViewModel: MainViewModel = koinViewModel()
        val mainState by mainViewModel.state.collectAsStateWithLifecycle()
        NavigationRoot(
            mainState = mainState,
            onLogout = mainViewModel::logout
        )
    }
}
