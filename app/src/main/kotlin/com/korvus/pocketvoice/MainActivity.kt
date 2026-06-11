package com.korvus.pocketvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.korvus.pocketvoice.ui.RecordScreen
import com.korvus.pocketvoice.ui.SettingsScreen
import com.korvus.pocketvoice.ui.VoiceHubScreen
import com.korvus.pocketvoice.ui.VoicesScreen
import com.korvus.pocketvoice.ui.theme.PocketVoiceTheme
import com.korvus.pocketvoice.ui.theme.VioletPrimary

private data class Tab(val route: String, val label: String, val out: ImageVector, val fill: ImageVector)

private val TABS = listOf(
    Tab("record", "Запись", Icons.Outlined.Mic, Icons.Rounded.Mic),
    Tab("voices", "Голоса", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    Tab("hub", "VoiceHub", Icons.Outlined.Storefront, Icons.Rounded.Storefront),
    Tab("settings", "Профиль", Icons.Outlined.PersonOutline, Icons.Rounded.Person),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketVoiceTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomBar(
                current = current,
                onTab = { route ->
                    if (current != route) {
                        nav.navigate(route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "record",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("record")   { RecordScreen() }
            composable("voices")   { VoicesScreen() }
            composable("hub")      { VoiceHubScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onTab: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .height(72.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            TABS.forEach { t ->
                val selected = current == t.route
                val tint = if (selected) VioletPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onTab(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) t.fill else t.out,
                        contentDescription = t.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
