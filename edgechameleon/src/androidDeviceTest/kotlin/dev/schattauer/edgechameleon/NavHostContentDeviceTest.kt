package dev.schattauer.edgechameleon

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The screens of a navigation host, the way most apps put the modifier around them. */
class NavHostContentDeviceTest {
    private val state = EdgeChameleonState()
    private var navController: NavHostController? = null
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.setContent {
                val controller = rememberNavController().also { navController = it }
                Box(Modifier.fillMaxSize().edgeChameleon(state = state)) {
                    NavHost(controller, startDestination = "light") {
                        composable("light") { Box(Modifier.fillMaxSize().background(Color.White)) }
                        composable("dark") { Box(Modifier.fillMaxSize().background(Color.Black)) }
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun navigate(route: String) {
        scenario.onActivity { checkNotNull(navController).navigate(route) }
    }

    private fun awaitStatusIcons(icons: BarIcons, screen: String) {
        val reached = awaitCondition(TimeoutMillis) { state.statusBarIcons == icons }
        assertEquals(
            "Status bar icons on the $screen screen",
            icons,
            if (reached) icons else state.statusBarIcons,
        )
    }

    @Test
    fun screensOfANavigationHostAreSampled() {
        awaitStatusIcons(BarIcons.Dark, "light")
        repeat(3) {
            navigate("dark")
            awaitStatusIcons(BarIcons.Light, "dark")
            navigate("light")
            awaitStatusIcons(BarIcons.Dark, "light")
        }
    }

    private companion object {
        const val TimeoutMillis = 5_000L
    }
}
