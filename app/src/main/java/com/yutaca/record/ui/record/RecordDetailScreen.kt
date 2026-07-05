package com.yutaca.record.ui.record

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.yutaca.record.data.entity.AttachmentEntity
import com.yutaca.record.data.entity.CustomMetaDataEntity
import com.yutaca.record.data.entity.ModificationHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // 展开/折叠面板状态
    var showAttachments by remember { mutableStateOf(false) }
    var showMetaData by remember { mutableStateOf(false) }

    // 文件选择器
    val context = LocalContext.current
    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val name = if (nameIndex >= 0) c.getString(nameIndex) else "unknown"
                    val mimeType = context.contentResolver.getType(it) ?: ""
                    viewModel.addAttachment(name, it.toString(), mimeType)
                }
            }
        }
    }

    // 对话框状态
    var showAddMetaDialog by remember { mutableStateOf(false) }
    var showEditMetaDialog by remember { mutableStateOf<CustomMetaDataEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) } // 存储待删除的附件 ID

    // 标题编辑状态
    var isEditingTitle by remember { mutableStateOf(false) }
    var editedTitle by remember(uiState.title) { mutableStateOf(uiState.title) }

    // 内容编辑器本地状态
    var localContent by remember(uiState.content) { mutableStateOf(uiState.content) }

    // 未保存标记：内容或标题是否有未保存的修改
    val hasContentChanged = localContent != uiState.content
    val hasTitleChanged = isEditingTitle && editedTitle != uiState.title
    val hasUnsavedChanges = hasContentChanged || hasTitleChanged

    // 退出确认对话框
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // 拦截系统返回键
    BackHandler {
        if (hasUnsavedChanges) {
            showExitConfirmDialog = true
        } else {
            onBack()
        }
    }

    // 保存所有更改
    fun saveAll() {
        if (hasContentChanged) {
            viewModel.saveContent(localContent)
        }
        if (hasTitleChanged) {
            viewModel.saveTitle(editedTitle)
            isEditingTitle = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showExitConfirmDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    if (isEditingTitle) {
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // 未保存指示圆点
                            if (hasUnsavedChanges) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .padding(top = 2.dp)
                                ) {
                                    // 使用一个小红点
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "未保存",
                                        modifier = Modifier.size(8.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // 编辑标题按钮
                    if (isEditingTitle) {
                        IconButton(onClick = {
                            if (editedTitle.isNotBlank()) {
                                viewModel.saveTitle(editedTitle)
                            }
                            isEditingTitle = false
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "保存标题")
                        }
                    } else {
                        IconButton(onClick = { isEditingTitle = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑标题")
                        }
                    }
                    IconButton(onClick = { /* 更多操作 - 待实现 */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                }
            )
        }
    ) { innerPadding ->
        val focusManager = LocalFocusManager.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(!hasUnsavedChanges) {
                    if (!hasUnsavedChanges) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                focusManager.clearFocus()
                            }
                        }
                    }
                }
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中...", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 文本内容编辑器（手动保存）
                item {
                    ContentEditor(
                        content = localContent,
                        onContentChange = { localContent = it },
                        onSave = { viewModel.saveContent(localContent) },
                        isSaved = !hasContentChanged
                    )
                }

                // 2. 修改历史
                item {
                    ModificationHistoryPanel(
                        history = uiState.modificationHistory
                    )
                }

                // 3. 附件管理
                item {
                    val openFile: (AttachmentEntity) -> Unit = { attachment ->
                        val uri = Uri.parse(attachment.fileUri)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, attachment.fileType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "打开方式"))
                    }
                    AttachmentsPanel(
                        attachments = uiState.attachments,
                        isExpanded = showAttachments,
                        onToggle = { showAttachments = !showAttachments },
                        onAdd = { attachmentLauncher.launch(arrayOf("*/*")) },
                        onDelete = { id -> showDeleteConfirm = id },
                        onOpen = openFile
                    )
                }

                // 4. 自定义元数据
                item {
                    MetaDataPanel(
                        metaDataList = uiState.metaData,
                        isExpanded = showMetaData,
                        onToggle = { showMetaData = !showMetaData },
                        onAdd = { showAddMetaDialog = true },
                        onEdit = { item -> showEditMetaDialog = item },
                        onDelete = { id -> viewModel.deleteMetaData(id) }
                    )
                }
            }
        }
    }
    }

    // ---- 退出确认对话框 ----
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("未保存的更改") },
            text = { Text("您有未保存的内容更改，是否在退出前保存？") },
            confirmButton = {
                TextButton(onClick = {
                    saveAll()
                    showExitConfirmDialog = false
                    onBack()
                }) {
                    Text("保存并退出")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitConfirmDialog = false
                        onBack()
                    }) {
                        Text("不保存", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showExitConfirmDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    // ---- 其他对话框 ----

    // 添加自定义元数据对话框
    if (showAddMetaDialog) {
        AddMetaDataDialog(
            onDismiss = { showAddMetaDialog = false },
            onConfirm = { key, value ->
                viewModel.addMetaData(key, value)
                showAddMetaDialog = false
            }
        )
    }

    // 编辑自定义元数据对话框
    showEditMetaDialog?.let { meta ->
        EditMetaDataDialog(
            metaData = meta,
            onDismiss = { showEditMetaDialog = null },
            onConfirm = { key, value ->
                viewModel.updateMetaData(meta.id, key, value)
                showEditMetaDialog = null
            }
        )
    }

    // 删除确认对话框
    showDeleteConfirm?.let { attachmentId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除此附件吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAttachment(attachmentId)
                    showDeleteConfirm = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }
}

// ==================== 子组件 ====================

/**
 * 文本内容编辑器（手动保存，无自动保存）
 */
@Composable
private fun ContentEditor(
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    isSaved: Boolean
) {
    val focusManager = LocalFocusManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(isSaved) {
                detectTapGestures {
                    if (isSaved) {
                        focusManager.clearFocus()
                    }
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "内容编辑",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                // 保存状态指示
                Text(
                    text = if (isSaved) "已保存" else "未保存",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSaved)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("在此输入记录内容...") }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    onSave()
                    focusManager.clearFocus()
                },
                enabled = !isSaved,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("保存")
            }
        }
    }
}

/**
 * 修改历史面板
 */
@Composable
private fun ModificationHistoryPanel(
    history: List<ModificationHistoryEntity>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "修改历史 (${history.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    history.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimestamp(entry.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "：${entry.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (history.isEmpty()) {
                        Text(
                            "暂无修改记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 附件管理面板
 */
@Composable
private fun AttachmentsPanel(
    attachments: List<AttachmentEntity>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onOpen: (AttachmentEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "附件 (${attachments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "添加附件")
                }
                TextButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (attachments.isEmpty()) {
                        Text(
                            "暂无附件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        attachments.forEach { attachment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { onOpen(attachment) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        attachment.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (attachment.fileType.isNotBlank()) {
                                        Text(
                                            attachment.fileType,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDelete(attachment.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除附件",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自定义元数据面板
 */
@Composable
private fun MetaDataPanel(
    metaDataList: List<CustomMetaDataEntity>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CustomMetaDataEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "自定义元数据 (${metaDataList.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "添加元数据")
                }
                TextButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (metaDataList.isEmpty()) {
                        Text(
                            "暂无自定义元数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        metaDataList.forEach { meta ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // key 标签
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = meta.key,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    // value 文本
                                    Text(
                                        text = meta.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // 编辑按钮
                                    IconButton(
                                        onClick = { onEdit(meta) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "编辑",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // 删除按钮
                                    IconButton(
                                        onClick = { onDelete(meta.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 对话框 ====================

/**
 * 添加自定义元数据对话框
 */
@Composable
private fun AddMetaDataDialog(
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义元数据") },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("键 (Key)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("值 (Value)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key, value) },
                enabled = key.isNotBlank() && value.isNotBlank()
            ) {
                Text("添加")
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
 * 编辑自定义元数据对话框
 */
@Composable
private fun EditMetaDataDialog(
    metaData: CustomMetaDataEntity,
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit
) {
    var key by remember { mutableStateOf(metaData.key) }
    var value by remember { mutableStateOf(metaData.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑元数据") },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("键 (Key)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("值 (Value)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key, value) },
                enabled = key.isNotBlank() && value.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}