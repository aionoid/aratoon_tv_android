package com.aionoid.aratoons.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aionoid.aratoons.ui.screens.CatalogScreen
import com.aionoid.aratoons.ui.screens.PlayerScreen
import com.aionoid.aratoons.ui.screens.RecentlyWatchedScreen
import com.aionoid.aratoons.ui.screens.SearchScreen
import com.aionoid.aratoons.ui.screens.ServerSelectScreen
import com.aionoid.aratoons.ui.screens.SeriesScreen
import com.aionoid.aratoons.ui.screens.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog?path={path}&title={title}"
    const val SERIES = "series?slug={slug}"
    const val SEARCH = "search?query={query?}"
    const val SERVERS = "servers?play={play}&title={title}&poster={poster}"
    const val PLAYER = "player?url={url}&title={title}&poster={poster}&server={server?}"
    const val SETTINGS = "settings"
    const val RECENTLY = "recently"

    fun servers(play: String, title: String, poster: String): String =
        "servers?play=${URLEncoder.encode(play, "UTF-8")}" +
            "&title=${URLEncoder.encode(title, "UTF-8")}" +
            "&poster=${URLEncoder.encode(poster, "UTF-8")}"

    fun catalog(path: String, title: String): String =
        "catalog?path=${URLEncoder.encode(path, "UTF-8")}&title=${URLEncoder.encode(title, "UTF-8")}"

    fun series(slug: String): String =
        "series?slug=${URLEncoder.encode(slug, "UTF-8")}"

    fun search(query: String): String =
        "search?query=${URLEncoder.encode(query, "UTF-8")}"

    fun player(url: String, title: String, poster: String, server: String? = null): String =
        "player?url=${URLEncoder.encode(url, "UTF-8")}" +
            "&title=${URLEncoder.encode(title, "UTF-8")}" +
            "&poster=${URLEncoder.encode(poster, "UTF-8")}" +
            (if (server.isNullOrEmpty()) "" else "&server=${URLEncoder.encode(server, "UTF-8")}")

    internal fun dec(s: String?): String =
        if (s == null) "" else URLDecoder.decode(s, "UTF-8")
}

@Composable
fun AraToonsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            // AraToonsShell hosts the tab bar (tabs) + the selected tab's
            // content, which slides between tabs. Item-level navigation
            // (servers/player/series) pushes on top of the shell.
            AraToonsShell(navController)
        }
        composable(Routes.CATALOG) { back ->
            CatalogScreen(
                path = Routes.dec(back.arguments?.getString("path")),
                title = Routes.dec(back.arguments?.getString("title")),
                navController = navController
            )
        }
        composable(Routes.SERIES) { back ->
            SeriesScreen(
                slug = Routes.dec(back.arguments?.getString("slug")),
                navController = navController
            )
        }
        composable(Routes.SEARCH) { back ->
            SearchScreen(
                navController = navController,
                initialQuery = Routes.dec(back.arguments?.getString("query"))
            )
        }
        composable(Routes.RECENTLY) {
            RecentlyWatchedScreen(navController)
        }
        composable(Routes.SERVERS) { back ->
            val play = Routes.dec(back.arguments?.getString("play"))
            val title = Routes.dec(back.arguments?.getString("title"))
            val poster = Routes.dec(back.arguments?.getString("poster"))
            ServerSelectScreen(
                playUrl = play,
                title = title,
                poster = poster,
                onBack = { navController.popBackStack() },
                onServerSelected = { server ->
                    navController.navigate(Routes.player(play, title, poster, server))
                }
            )
        }
        composable(Routes.PLAYER) { back ->
            PlayerScreen(
                url = Routes.dec(back.arguments?.getString("url")),
                title = Routes.dec(back.arguments?.getString("title")),
                poster = Routes.dec(back.arguments?.getString("poster")),
                // navigation 2.8 stores optional route params under the key
                // WITH the '?' suffix ("server?"), older versions without it
                server = back.arguments?.let { it.getString("server") ?: it.getString("server?") }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}
