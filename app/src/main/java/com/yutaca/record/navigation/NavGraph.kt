package com.yutaca.record.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.yutaca.record.data.database.AppDatabase
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import com.yutaca.record.ui.directory.DirectoryScreen
import com.yutaca.record.ui.directory.DirectoryViewModel
import com.yutaca.record.ui.favorites.FavoritesScreen
import com.yutaca.record.ui.favorites.FavoritesViewModel
import com.yutaca.record.ui.home.HomeScreen
import com.yutaca.record.ui.home.HomeViewModel
import com.yutaca.record.ui.profile.ProfileScreen
import com.yutaca.record.ui.search.SearchScreen
import com.yutaca.record.ui.record.RecordDetailScreen
import com.yutaca.record.ui.record.RecordDetailViewModel

object Routes {
    const val HOME = "home"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"
    const val DIRECTORY = "directory/{notebookId}"
    const val RECORD_DETAIL = "record/{recordId}"
    const val SEARCH = "search"

    fun directory(notebookId: Long) = "directory/$notebookId"
    fun recordDetail(recordId: Long) = "record/$recordId"
}

@Composable
fun RecordNavGraph(
    navController: NavHostController,
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    // 创建 Repository 实例（在 Activity 级别共享）
    val notebookRepository = remember { NotebookRepository(database.notebookDao()) }
    val treeNodeRepository = remember { TreeNodeRepository(database.treeNodeDao()) }
    val recordRepository = remember { RecordRepository(database.recordDao(), database.attachmentDao(), database.customMetaDataDao(), database.modificationHistoryDao()) }

    // 主页 ViewModel Factory（可复用）
    val homeViewModelFactory = remember { HomeViewModel.Factory(notebookRepository) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        // 首页 - 记录集列表
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(factory = homeViewModelFactory)
            HomeScreen(
                onNotebookClick = { notebookId ->
                    navController.navigate(Routes.directory(notebookId))
                },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                viewModel = homeViewModel
            )
        }

        // 收藏页
        composable(Routes.FAVORITES) {
            val favoritesViewModelFactory = remember {
                FavoritesViewModel.Factory(treeNodeRepository, notebookRepository)
            }
            val favoritesViewModel: FavoritesViewModel = viewModel(factory = favoritesViewModelFactory)
            FavoritesScreen(
                viewModel = favoritesViewModel,
                onNodeClick = { node ->
                    if (node.isLeaf && node.recordId != null) {
                        navController.navigate(Routes.recordDetail(node.recordId))
                    } else {
                        navController.navigate(Routes.directory(node.notebookId))
                    }
                }
            )
        }

        // 更多页
        composable(Routes.PROFILE) {
            ProfileScreen(notebookRepository = notebookRepository)
        }

        // 记录集目录页
        composable(
            route = Routes.DIRECTORY,
            arguments = listOf(navArgument("notebookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getLong("notebookId") ?: return@composable
            val directoryViewModelFactory = remember(notebookId) {
                DirectoryViewModel.Factory(
                    notebookId = notebookId,
                    notebookRepository = notebookRepository,
                    treeNodeRepository = treeNodeRepository,
                    recordRepository = recordRepository
                )
            }
            val directoryViewModel: DirectoryViewModel = viewModel(factory = directoryViewModelFactory)

            DirectoryScreen(
                onBack = { navController.popBackStack() },
                onRecordClick = { recordId ->
                    navController.navigate(Routes.recordDetail(recordId))
                },
                viewModel = directoryViewModel,
                notebookRepository = notebookRepository
            )
        }

        // 搜索页
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onRecordClick = { recordId ->
                    navController.navigate(Routes.recordDetail(recordId))
                },
                recordRepository = recordRepository,
                treeNodeRepository = treeNodeRepository,
                notebookRepository = notebookRepository
            )
        }

        // 记录详情页
        composable(
            route = Routes.RECORD_DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: return@composable
            val recordDetailViewModelFactory = remember(recordId) {
                RecordDetailViewModel.Factory(
                    recordId = recordId,
                    recordRepository = recordRepository,
                    treeNodeRepository = treeNodeRepository,
                    notebookRepository = notebookRepository
                )
            }
            val recordDetailViewModel: RecordDetailViewModel = viewModel(factory = recordDetailViewModelFactory)

            RecordDetailScreen(
                onBack = { navController.popBackStack() },
                viewModel = recordDetailViewModel
            )
        }
    }
}
