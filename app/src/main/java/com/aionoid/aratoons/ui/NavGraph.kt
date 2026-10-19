package com.aionoid.aratoons.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aionoid.aratoons.ui.screens.CatalogScreen
import com.aionoid.aratoons.ui.screens.HomeScreen
import com.aionoid.aratoons.ui.screens.PlayerScreen
import com.aionoid.aratoons.ui.screens.SearchScreen
import com.aionoid.aratoons.ui.screens.SeriesScreen
import com.aionoid.aratoons.ui.screens.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog?path={path}&title={title}"
    const val SERIES = "series?slug={slug}"
    const val SEARCH = "search"
    const val PLAYER = "player?url={url}&title={title}&poster={poster}"
    const val SETTINGS = "settings"

    fun catalog(path: String, title: String): String =
        "catalog?path=${URLEncoder.encode(path, "UTF-8")}&title=${URLEncoder.encode(title, "UTF-8")}"

    fun series(slug: String): String =
        "series?slug=${URLEncoder.encode(slug, "UTF-8")}"

    fun player(url: String, title: String, poster: String): String =
        "player?url=${URLEncoder.encode(url, "UTF-8")}" +
            "&title=${URLEncoder.encode(title, "UTF-8")}" +
            "&poster=${URLEncoder.encode(poster, "UTF-8")}"

    internal fun dec(s: String?): String =
        if (s == null) "" else URLDecoder.decode(s, "UTF-8")
}

@Composable
fun AraToonsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(navController)
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
        composable(Routes.SEARCH) {
            SearchScreen(navController)
        }
        composable(Routes.PLAYER) { back ->
            PlayerScreen(
                url = Routes.dec(back.arguments?.getString("url")),
                title = Routes.dec(back.arguments?.getString("title")),
                poster = Routes.dec(back.arguments?.getString("poster"))
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}
