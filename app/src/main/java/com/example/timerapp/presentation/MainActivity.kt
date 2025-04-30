package com.example.timerapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberSwipeDismissableNavController()
            val vm: TimerViewModel = viewModel()
            LaunchedEffect(Unit) { vm.reAttachIfServiceRunning() }

            MaterialTheme {
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            vm,
                            onCreate = { navController.navigate("create") },
                            onEdit   = { id -> navController.navigate("edit/$id") },
                            onStart  = { id -> navController.navigate("active/$id") }
                        )
                    }
                    composable("create")  {
                        EditTimerScreen(vm, null) { navController.popBackStack() }
                    }
                    composable("edit/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull()
                        EditTimerScreen(vm, id) { navController.popBackStack() }
                    }
                    composable("active/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull()
                        ActiveTimerScreen(vm, id) { navController.popBackStack() }
                    }
                }
            }
        }
    }
}
