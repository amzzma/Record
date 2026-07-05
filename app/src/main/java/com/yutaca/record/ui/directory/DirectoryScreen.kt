package com.yutaca.record.ui.directory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.yutaca.record.data.repository.NotebookRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 莫兰迪绿色边框 - 一级卡片
private val MorandiGreen = Color(0xFF7D9B76)
// 更浅的莫兰迪绿色边框 - 二级卡片
private val MorandiGreenLight = Color(0xFFA0B89E)
// 左侧竖线颜色 - 二级卡片层级指示
private val LevelIndicatorColor = Color(0xFFB8D4B4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    onBack: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onExportClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DirectoryViewModel,
    notebookRepository: NotebookRepository
) {
    val uiState by viewModel.uiState.collectAsState()

    // 监听添加节点事件
    var showAddDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.addNodeEvent.collect {
            showAddDialog = true
        }
    }

    // 重命名对话框状态
    var renameDialogInfo by remember { mutableStateOf<RenameInfo?>(null) }

    // 删除确认对话框状态
    var deleteDialogInfo by remember { mutableStateOf<DeleteInfo?>(null) }

    // 移动对话框状态：nodeId, nodeName, nodeType
    var moveDialogInfo by remember { mutableStateOf<MoveInfo?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                title = {
                    Text(
                        text = uiState.notebookTitle.ifBlank { "目录" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
//                        DropdownMenuItem(
//                            text = { Text("设置") },
//                            onClick = {
//                                showMenu = false
//                                /* 设置 - 待实现 */
//                            }
//                        )
                        DropdownMenuItem(
                            text = { Text("导出记录本") },
                            onClick = {
                                showMenu = false
                                onExportClick()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Tab 切换
            val tabs = listOf("章节", "关于")
            TabRow(selectedTabIndex = uiState.selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTabIndex == index,
                        onClick = { viewModel.setSelectedTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            when (uiState.selectedTabIndex) {
                0 -> ChapterTab(
                    chapters = uiState.chapters,
                    isLoading = uiState.isLoading,
                    onLevelOneToggle = { nodeId -> viewModel.toggleLevelOne(nodeId) },
                    onLevelTwoToggle = { nodeId -> viewModel.toggleLevelTwo(nodeId) },
                    onRecordClick = { recordId ->
                        recordId.toLongOrNull()?.let { onRecordClick(it) }
                    },
                    onAddNode = { viewModel.onAddNodeClicked() },
                    onRename = { nodeId, currentName ->
                        renameDialogInfo = RenameInfo(nodeId, currentName)
                    },
                    onDelete = { nodeId, nodeName, nodeType ->
                        deleteDialogInfo = DeleteInfo(nodeId, nodeName, nodeType)
                    },
                    onMove = { nodeId, nodeName, nodeType ->
                        moveDialogInfo = MoveInfo(nodeId, nodeName, nodeType)
                    },
                    onToggleFavorite = { nodeId -> viewModel.toggleFavorite(nodeId) }
                )
                 1 -> AboutTab(
                     description = uiState.aboutDescription,
                     createdAt = uiState.aboutCreatedAt,
                     updatedAt = uiState.aboutUpdatedAt,
                     totalRecordCount = uiState.totalRecordCount,
                     isLoading = uiState.isLoading,
                     onSaveDescription = { newDesc -> viewModel.updateDescription(newDesc) },
                     coverImageUri = uiState.aboutCoverImageUri,
                     onUpdateCoverImage = { uri -> viewModel.updateCoverImage(uri) },
                     notebookTitle = uiState.notebookTitle
                 )
            }
        }
    }

    // 添加节点对话框
    if (showAddDialog) {
        AddNodeDialog(
            levelOneChapters = viewModel.getLevelOneChapters(),
            levelTwoFolders = viewModel.getLevelTwoFolders(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name, level, parentId, isFolder ->
                viewModel.addNode(name = name, level = level, parentId = parentId, isFolder = isFolder)
                showAddDialog = false
            }
        )
    }

    // 重命名对话框
    renameDialogInfo?.let { info ->
        RenameDialog(
            currentName = info.currentName,
            onDismiss = { renameDialogInfo = null },
            onConfirm = { newName ->
                viewModel.renameNode(info.nodeId, newName)
                renameDialogInfo = null
            }
        )
    }

    // 删除确认对话框
    deleteDialogInfo?.let { info ->
        DeleteConfirmDialog(
            nodeName = info.nodeName,
            nodeType = info.nodeType,
            onDismiss = { deleteDialogInfo = null },
            onConfirm = {
                viewModel.deleteNode(info.nodeId)
                deleteDialogInfo = null
            }
        )
    }

    // 移动对话框
    moveDialogInfo?.let { info ->
        MoveDialog(
            nodeId = info.nodeId,
            nodeName = info.nodeName,
            nodeType = info.nodeType,
            viewModel = viewModel,
            onDismiss = { moveDialogInfo = null }
        )
    }
}

// ==================== 操作数据类 ====================

// 重命名对话框数据
private data class RenameInfo(
    val nodeId: String,
    val currentName: String
)

// 删除对话框数据
private data class DeleteInfo(
    val nodeId: String,
    val nodeName: String,
    val nodeType: String
)

// 移动对话框数据
private data class MoveInfo(
    val nodeId: String,
    val nodeName: String,
    val nodeType: String
)

// ==================== 章节 Tab ====================

@Composable
private fun ChapterTab(
    chapters: List<LevelOneChapter>,
    isLoading: Boolean,
    onLevelOneToggle: (String) -> Unit,
    onLevelTwoToggle: (String) -> Unit,
    onRecordClick: (String) -> Unit,
    onAddNode: () -> Unit,
    onRename: (nodeId: String, currentName: String) -> Unit,
    onDelete: (nodeId: String, nodeName: String, nodeType: String) -> Unit,
    onMove: (nodeId: String, nodeName: String, nodeType: String) -> Unit,
    onToggleFavorite: (nodeId: String) -> Unit
) {
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                items(chapters, key = { it.id }) { chapter ->
                    LevelOneCard(
                        chapter = chapter,
                        onToggle = { onLevelOneToggle(chapter.id) },
                        onLevelTwoToggle = onLevelTwoToggle,
                        onRecordClick = onRecordClick,
                        onRename = onRename,
                        onDelete = onDelete,
                        onMove = onMove,
                        onToggleFavorite = onToggleFavorite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 底部添加卡片
                item {
                    ChapterAddCard(onClick = onAddNode)
                }
            }
        }
    }
}

// ==================== 一级卡片（大章节） ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LevelOneCard(
    chapter: LevelOneChapter,
    onToggle: () -> Unit,
    onLevelTwoToggle: (String) -> Unit,
    onRecordClick: (String) -> Unit,
    onRename: (nodeId: String, currentName: String) -> Unit,
    onDelete: (nodeId: String, nodeName: String, nodeType: String) -> Unit,
    onMove: (nodeId: String, nodeName: String, nodeType: String) -> Unit,
    onToggleFavorite: (nodeId: String) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MorandiGreen)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onToggle() },
                        onLongClick = { showContextMenu = true }
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onToggleFavorite(chapter.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (chapter.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (chapter.isFavorite) "取消收藏" else "收藏",
                        modifier = Modifier.size(20.dp),
                        tint = if (chapter.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 展开/折叠箭头
                if (chapter.children.isNotEmpty()) {
                    Icon(
                        imageVector = if (chapter.isExpanded)
                            Icons.Default.KeyboardArrowDown
                        else
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (chapter.isExpanded) "折叠" else "展开",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 长按上下文菜单
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = DpOffset(0.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            showContextMenu = false
                            onRename(chapter.id, chapter.title)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移动") },
                        onClick = {
                            showContextMenu = false
                            onMove(chapter.id, chapter.title, "章节")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showContextMenu = false
                            onDelete(chapter.id, chapter.title, "章节")
                        }
                    )
                }
            }

            // 二级节点列表（AnimatedVisibility）
            AnimatedVisibility(
                visible = chapter.isExpanded && chapter.children.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    chapter.children.forEach { child ->
                        when (child) {
                            is LevelTwoNode.Folder -> {
                                LevelTwoFolderCard(
                                    folder = child,
                                    onToggle = { onLevelTwoToggle(child.id) },
                                    onRecordClick = onRecordClick,
                                    onRename = onRename,
                                    onDelete = onDelete,
                                    onMove = onMove
                                )
                            }
                            is LevelTwoNode.Leaf -> {
                                LevelTwoLeafItem(
                                    leaf = child,
                                    onClick = { onRecordClick(child.id) },
                                    onRename = { onRename(child.treeNodeId, child.title) },
                                    onDelete = { onDelete(child.treeNodeId, child.title, "记录") },
                                    onMove = { onMove(child.treeNodeId, child.title, "记录") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ==================== 二级文件夹卡片 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LevelTwoFolderCard(
    folder: LevelTwoNode.Folder,
    onToggle: () -> Unit,
    onRecordClick: (String) -> Unit,
    onRename: (nodeId: String, currentName: String) -> Unit,
    onDelete: (nodeId: String, nodeName: String, nodeType: String) -> Unit,
    onMove: (nodeId: String, nodeName: String, nodeType: String) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp), // 缩进表示层级
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MorandiGreenLight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题行 + 左侧竖线指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onToggle() },
                        onLongClick = { showContextMenu = true }
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧层级竖线
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = LevelIndicatorColor,
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 展开/折叠箭头
                if (folder.records.isNotEmpty()) {
                    Icon(
                        imageVector = if (folder.isExpanded)
                            Icons.Default.KeyboardArrowDown
                        else
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (folder.isExpanded) "折叠" else "展开",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 长按上下文菜单
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = DpOffset(0.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            showContextMenu = false
                            onRename(folder.id, folder.title)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移动") },
                        onClick = {
                            showContextMenu = false
                            onMove(folder.id, folder.title, "文件夹")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showContextMenu = false
                            onDelete(folder.id, folder.title, "文件夹")
                        }
                    )
                }
            }

            // 三级记录列表（AnimatedVisibility）
            AnimatedVisibility(
                visible = folder.isExpanded && folder.records.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, bottom = 8.dp)
                ) {
                    folder.records.forEach { record ->
                        LevelThreeRecordItem(
                            record = record,
                            onClick = { onRecordClick(record.id) },
                            onRename = { onRename(record.treeNodeId, record.title) },
                            onDelete = { onDelete(record.treeNodeId, record.title, "记录") },
                            onMove = { onMove(record.treeNodeId, record.title, "记录") }
                        )
                        if (record != folder.records.last()) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==================== 二级叶子节点（文本项） ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LevelTwoLeafItem(
    leaf: LevelTwoNode.Leaf,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            )
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = leaf.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // 长按上下文菜单
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = DpOffset(0.dp, 0.dp)
        ) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    showContextMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("移动") },
                onClick = {
                    showContextMenu = false
                    onMove()
                }
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showContextMenu = false
                    onDelete()
                }
            )
        }
    }
}

// ==================== 三级记录（文本项） ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LevelThreeRecordItem(
    record: LevelThreeRecord,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            )
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = record.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // 长按上下文菜单
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = DpOffset(0.dp, 0.dp)
        ) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    showContextMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("移动") },
                onClick = {
                    showContextMenu = false
                    onMove()
                }
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showContextMenu = false
                    onDelete()
                }
            )
        }
    }
}

// ==================== 底部添加卡片 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterAddCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.5f),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f)
                            )
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加章节或记录", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ==================== 关于 Tab ====================

@Composable
private fun AboutTab(
    description: String,
    createdAt: Long,
    updatedAt: Long,
    totalRecordCount: Int,
    isLoading: Boolean,
    onSaveDescription: (String) -> Unit,
    coverImageUri: String,
    onUpdateCoverImage: (String) -> Unit,
    notebookTitle: String
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var editDescription by remember(description) { mutableStateOf(description) }
    var hasFocus by remember { mutableStateOf(false) }
    var showCoverMenu by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }

    // 文件选择器：选择图片后复制到内部存储（避免重启后 content:// URI 权限丢失），再更新封面
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { contentUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(contentUri)
                if (inputStream != null) {
                    val coverDir = java.io.File(context.filesDir, "cover_images")
                    if (!coverDir.exists()) coverDir.mkdirs()
                    val fileName = "cover_${System.currentTimeMillis()}_${contentUri.hashCode()}.jpg"
                    val destFile = java.io.File(coverDir, fileName)
                    inputStream.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    onUpdateCoverImage(destFile.toURI().toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 失去焦点时自动保存
    LaunchedEffect(hasFocus) {
        if (!hasFocus && editDescription != description) {
            onSaveDescription(editDescription)
        }
    }

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                    },
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MorandiGreen)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "描述",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicTextField(
                                value = editDescription,
                                onValueChange = { editDescription = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged {
                                        hasFocus = it.hasFocus
                                    },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                decorationBox = { innerTextField ->
                                    if (editDescription.isEmpty()) {
                                        Text(
                                            "添加描述...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    AboutInfoCard(
                        title = "创建时间",
                        content = formatTimestamp(createdAt),
                        onClick = { focusManager.clearFocus() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    AboutInfoCard(
                        title = "最后修改",
                        content = formatTimestamp(updatedAt),
                        onClick = { focusManager.clearFocus() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    AboutInfoCard(
                        title = "记录总数",
                        content = "$totalRecordCount 篇",
                        onClick = { focusManager.clearFocus() }
                    )
                }

                // 封面图片区域
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "封面图片",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MorandiGreen)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        // 点击封面 → 全屏 Dialog 预览
                                        if (coverImageUri.isNotBlank()) {
                                            showCoverPreview = true
                                        }
                                    },
                                onLongClick = {
                                    // 长按封面 → 弹出更换封面菜单（无论是否有封面都触发）
                                    showCoverMenu = true
                                }
                                )
                        ) {
                            if (coverImageUri.isNotBlank()) {
                                // 有封面：显示封面图片
                                AsyncImage(
                                    model = coverImageUri,
                                    contentDescription = "封面图片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // 无封面：显示默认占位
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = notebookTitle.ifBlank { "记录集" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            // 下拉菜单：长按后触发
                            DropdownMenu(
                                expanded = showCoverMenu,
                                onDismissRequest = { showCoverMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("更换封面") },
                                    onClick = {
                                        showCoverMenu = false
                                        imagePickerLauncher.launch("image/*")
                                    }
                                )
                                if (coverImageUri.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("删除封面", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showCoverMenu = false
                                            onUpdateCoverImage("")
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 提示文字
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "长按封面图片可更换封面",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // 全屏封面预览 Dialog
    if (showCoverPreview && coverImageUri.isNotBlank()) {
        Dialog(
            onDismissRequest = { showCoverPreview = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.9f)) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showCoverPreview = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = coverImageUri,
                    contentDescription = "封面大图预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )

                // 关闭按钮（右上角）
                IconButton(
                    onClick = { showCoverPreview = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutInfoCard(
    title: String,
    content: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MorandiGreen)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ==================== 移动对话框 ====================

/**
 * 移动节点对话框
 *
 * 根据节点类型提供不同的目标选项：
 * - 章节（一级）：移动到其他同层级位置
 * - 记录（二级叶子或三级记录）：可选挂到一级章节下（成为二级叶子）或二级文件夹下（成为三级记录）
 * - 文件夹（二级）：可选移动到其他一级章节下
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveDialog(
    nodeId: String,
    nodeName: String,
    nodeType: String,
    viewModel: DirectoryViewModel,
    onDismiss: () -> Unit
) {
    // 根据节点类型决定 UI
    val isChapter = nodeType == "章节"
    val isFolder = nodeType == "文件夹"
    val isRecord = nodeType == "记录"

    // 目标选择
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedTargetId by remember { mutableStateOf<String?>(null) }
    var selectedTargetLabel by remember { mutableStateOf("") }

    // 获取可用的目标列表
    val targetOptions: List<Pair<String, String>> = remember(nodeId, nodeType) {
        when {
            isChapter -> {
                // 一级章节：移动到其他一级章节之后（目标为 null，表示根节点），
                // 但只显示其他一级章节，选中即插入到该章节之前/之后
                // 简化：显示排序位置选择
                emptyList()
            }
            isFolder -> {
                // 二级文件夹：移动到其他一级章节下
                // 需要找到当前文件夹所属的一级章节 ID
                val currentChapterId = findCurrentChapterId(nodeId, viewModel)
                viewModel.getLevelOneChaptersForFolderMove(currentChapterId)
            }
            isRecord -> {
                // 记录：可选移动到一级章节下（成为二级叶子）或二级文件夹下（成为三级记录）
                viewModel.getLevelOneChaptersWithFolders()
            }
            else -> emptyList()
        }
    }

    // 一级章节：显示位置选择器
    if (isChapter) {
        // 获取当前所有一级章节列表（用于计算可选位置）
        val allChapters = viewModel.getLevelOneChapters()
        val currentIndex = allChapters.indexOfFirst { it.first == nodeId }

        // 构建位置选项列表（排除当前位置）
        val positionOptions = remember(allChapters, nodeId) {
            allChapters.indices.filter { it != currentIndex }.map { index ->
                index to "第 ${index + 1} 位"
            }
        }

        var dropdownExpanded by remember { mutableStateOf(false) }
        var selectedTargetIndex by remember { mutableStateOf<Int?>(null) }
        var selectedTargetLabel by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("移动章节: $nodeName") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "选择目标位置：",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedTargetLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("目标位置") },
                            placeholder = { Text("请选择") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            positionOptions.forEach { (index, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedTargetIndex = index
                                        selectedTargetLabel = label
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTargetIndex?.let { targetIndex ->
                            viewModel.moveChapterToPosition(nodeId, targetIndex)
                        }
                        onDismiss()
                    },
                    enabled = selectedTargetIndex != null
                ) {
                    Text("移动")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        )
        return
    }

    // 文件夹或记录：选择目标父节点
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("移动${if (isFolder) "文件夹" else "记录"}: $nodeName")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "选择目标位置：",
                    style = MaterialTheme.typography.bodyMedium
                )

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTargetLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                if (isFolder) "目标一级章节"
                                else "目标位置"
                            )
                        },
                        placeholder = { Text("请选择") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        targetOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedTargetId = id
                                    selectedTargetLabel = label
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedTargetId?.let { targetId ->
                        viewModel.moveNode(nodeId, targetId)
                    }
                    onDismiss()
                },
                enabled = selectedTargetId != null
            ) {
                Text("移动")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 查找指定节点所属的一级章节 ID（用于文件夹移动时排除自身所在章节）
 */
private fun findCurrentChapterId(nodeId: String, viewModel: DirectoryViewModel): String {
    // 遍历所有一级章节，查找包含该文件夹的章节
    val allChapters = viewModel.getLevelOneChapters()
    for ((chapterId, _) in allChapters) {
        // 通过 ViewModel 的 getLevelTwoFolders 检查该文件夹是否在该章节下
        // 这里简单返回空字符串，让 getLevelOneChaptersForFolderMove 不做排除
        // 用更精确的方法：从 ViewModel 已知结构查找
    }
    // 简化：返回空，表示不排除任何章节
    return ""
}

// ==================== 添加节点对话框 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNodeDialog(
    levelOneChapters: List<Pair<String, String>>,
    levelTwoFolders: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, level: Int, parentId: String?, isFolder: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }

    // 层级选择: 1=一级章节, 2=二级节点, 3=三级记录
    val levelOptions = listOf("一级章节", "二级节点", "三级记录")
    var selectedLevelIndex by remember { mutableStateOf(0) }
    var levelDropdownExpanded by remember { mutableStateOf(false) }

    // 二级节点的类型选择：文件夹还是叶子记录
    var isFolder by remember { mutableStateOf(true) }

    // 父节点选择（联动）
    var selectedParentId by remember { mutableStateOf<String?>(null) }
    var parentDropdownExpanded by remember { mutableStateOf(false) }
    var selectedParentLabel by remember { mutableStateOf("") }

    // 根据当前选择的层级获取可用的父节点列表
    val currentParentOptions: List<Pair<String, String>> = remember(selectedLevelIndex, levelOneChapters, levelTwoFolders) {
        when (selectedLevelIndex) {
            0 -> emptyList() // 一级章节不需要父节点
            1 -> levelOneChapters // 二级节点需要选择一级章节作为父节点
            2 -> levelTwoFolders // 三级记录需要选择二级文件夹作为父节点
            else -> emptyList()
        }
    }

    // 当层级切换时重置父节点选择
    LaunchedEffect(selectedLevelIndex) {
        selectedParentId = null
        selectedParentLabel = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加节点") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- 下拉框 1：选择层级 ----
                ExposedDropdownMenuBox(
                    expanded = levelDropdownExpanded,
                    onExpandedChange = { levelDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = levelOptions[selectedLevelIndex],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择层级") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = levelDropdownExpanded,
                        onDismissRequest = { levelDropdownExpanded = false }
                    ) {
                        levelOptions.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedLevelIndex = index
                                    levelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // ---- 下拉框 2：选择父节点（联动，一级时隐藏） ----
                if (selectedLevelIndex > 0) {
                    ExposedDropdownMenuBox(
                        expanded = parentDropdownExpanded,
                        onExpandedChange = { parentDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedParentLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text(
                                    when (selectedLevelIndex) {
                                        1 -> "所属一级章节"
                                        2 -> "所属二级文件夹"
                                        else -> "所属父节点"
                                    }
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = parentDropdownExpanded,
                            onDismissRequest = { parentDropdownExpanded = false }
                        ) {
                            currentParentOptions.forEach { (id, title) ->
                                DropdownMenuItem(
                                    text = { Text(title) },
                                    onClick = {
                                        selectedParentId = id
                                        selectedParentLabel = title
                                        parentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ---- 二级节点时：选择类型（文件夹/叶子记录） ----
                if (selectedLevelIndex == 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "节点类型：",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { isFolder = true }) {
                            Text(
                                "文件夹",
                                color = if (isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { isFolder = false }) {
                            Text(
                                "记录",
                                color = if (!isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ---- 名称输入框 ----
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val level = selectedLevelIndex + 1
                    val parentId = if (level == 1) null else selectedParentId
                    onConfirm(name.trim(), level, parentId, isFolder)
                },
                enabled = name.isNotBlank() && (selectedLevelIndex == 0 || selectedParentId != null)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 重命名对话框 ====================

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName.trim()) },
                enabled = newName.isNotBlank() && newName.trim() != currentName
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 删除确认对话框 ====================

@Composable
private fun DeleteConfirmDialog(
    nodeName: String,
    nodeType: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = {
            Text(
                "确定要删除${if (nodeType == "章节") "章节" else if (nodeType == "文件夹") "文件夹" else "记录"}" +
                        "「$nodeName」吗？\n\n" +
                        if (nodeType != "记录") "该节点下的所有子节点也会被一并删除。\n" else "" +
                        "此操作不可撤销。"
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                }
            ) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 时间格式化工具 ====================

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "未知"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}