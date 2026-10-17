package com.medcompanion.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medcompanion.app.R
import com.medcompanion.app.feature.assistant.AssistantScreen
import com.medcompanion.app.feature.history.HistoryScreen
import com.medcompanion.app.feature.medications.MedicationsScreen
import com.medcompanion.app.feature.profile.ProfileScreen
import com.medcompanion.app.feature.symptoms.SymptomsScreen
import com.medcompanion.app.feature.today.TodayScreen
import com.medcompanion.app.ui.navigation.PROFILE_ROUTE
import com.medcompanion.app.ui.navigation.TopLevelDestination
import com.medcompanion.app.ui.navigation.topLevelDestinationFromRoute

/**
 * App shell: a top bar with the profile action, the five-tab bottom bar,
 * and the navigation host with placeholder screens for v1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedCompanionRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = topLevelDestinationFromRoute(currentRoute)
    val isProfile = currentRoute == PROFILE_ROUTE

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text =
                            stringResource(
                                if (isProfile) R.string.profile_title else currentTab.labelRes,
                            ),
                    )
                },
                navigationIcon = {
                    if (isProfile) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_profile),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (!isProfile) {
                        IconButton(onClick = { navController.navigate(PROFILE_ROUTE) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_profile),
                                contentDescription = stringResource(R.string.action_open_profile),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected =
                        currentTab == destination &&
                            backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected || (currentRoute == null && destination == TopLevelDestination.TODAY),
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TODAY.route,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            composable(TopLevelDestination.TODAY.route) { TodayScreen() }
            composable(TopLevelDestination.MEDICATIONS.route) { MedicationsScreen() }
            composable(TopLevelDestination.SYMPTOMS.route) { SymptomsScreen() }
            composable(TopLevelDestination.HISTORY.route) { HistoryScreen() }
            composable(TopLevelDestination.ASSISTANT.route) { AssistantScreen() }
            composable(PROFILE_ROUTE) { ProfileScreen() }
        }
    }
}
