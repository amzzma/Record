package com.yutaca.record.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.yutaca.record.data.entity.RecordEntity
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import kotlinx.coroutines.launch

private const val INITIAL_DISPLAY_COUNT = 6

data class SearchResultItem(
    val recordId: Long,
    val title: String,
    val contentSnippet: String,
    val notebookName: String,
    val path: String
) : java.io.Serializable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onRecordClick: (Long) -> Unit = {},
    recordRepository: RecordRepository? = null,
    treeNodeRepository: TreeNodeRepository? = null,
    notebookRepository: NotebookRepository? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("search_prefs", Context.MODE_PRIVATE) }
    val historyItems = remember {
        val saved = prefs.getString("history", "") ?: ""
        mutableStateListOf<String>().also {
            if (saved.isNotBlank()) it.addAll(saved.split("|||"))
        }
    }

    fun saveHistory() {
        prefs.edit().putString("history", historyItems.joinToString("|||")).apply()
    }
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var searchResults by rememberSaveable { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val displayItems = if (isExpanded) historyItems
                       else historyItems.take(INITIAL_DISPLAY_COUNT)
    val hasMore = historyItems.size > INITIAL_DISPLAY_COUNT

    fun performSearch(keyword: String) {
        if (keyword.isBlank() || recordRepository == null || treeNodeRepository == null || notebookRepository == null) return

        // 添加搜索历史
        if (keyword !in historyItems) {
            historyItems.add(0, keyword)
            saveHistory()
        }

        scope.launch {
            isSearching = true
            hasSearched = true
            try {
                val records = recordRepository.searchByContent("%$keyword%")
                val results = records.mapNotNull { record ->
                    val treeNode = treeNodeRepository.getNodeByRecordId(record.id)
                    if (treeNode == null) {
                        // 记录可能未关联到树节点，跳过
                        return@mapNotNull null
                    }
                    val notebook = notebookRepository.getNotebookById(treeNode.notebookId)
                    val notebookName = notebook?.name ?: "未知记录集"

                    // 构建内容片段
                    val snippet = buildContentSnippet(record.content, keyword)

                    SearchResultItem(
                        recordId = record.id,
                        title = record.title,
                        contentSnippet = snippet,
                        notebookName = notebookName,
                        path = notebookName
                    )
                }
                searchResults = results
            } catch (e: Exception) {
                searchResults = emptyList()
            } finally {
                isSearching = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasSearched) {
                            // 有搜索结果时清空搜索状态回到历史
                            hasSearched = false
                            searchResults = emptyList()
                            query = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入关键字") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { performSearch(query.trim()) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            HorizontalDivider()

            if (hasSearched) {
                // ======== 搜索结果区域 ========
                if (isSearching) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("搜索中...", style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "未找到相关内容",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "找到 ${searchResults.size} 条结果",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(searchResults) { result ->
                            SearchResultCard(
                                item = result,
                                keyword = query.trim(),
                                onClick = { onRecordClick(result.recordId) }
                            )
                        }
                    }
                }
            } else {
                // ======== 搜索历史区域 ========
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "搜索历史",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            historyItems.clear()
                            saveHistory()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "清空搜索历史",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        displayItems.forEach { item ->
                            SearchHistoryChip(
                                text = item,
                                onClick = {
                                    query = item
                                    performSearch(item)
                                },
                                onDelete = {
                                    historyItems.remove(item)
                                    saveHistory()
                                }
                            )
                        }
                    }

                    // 展开/收起按钮
                    if (hasMore) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                if (isExpanded) "收起 ▲" else "展开更多 ▼",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 构建内容片段：截取关键词附近的一段文本
 */
private fun buildContentSnippet(content: String, keyword: String): String {
    if (content.isBlank()) return ""
    val lowerContent = content.lowercase()
    val lowerKeyword = keyword.lowercase()
    val index = lowerContent.indexOf(lowerKeyword)
    if (index < 0) {
        // 关键词不在内容中（可能只在标题中匹配），返回开头
        return content.take(100)
    }
    // 截取关键词前后各 40 个字符
    val start = maxOf(0, index - 40)
    val end = minOf(content.length, index + keyword.length + 40)
    val snippet = content.substring(start, end).replace("\n", " ")
    return if (start > 0) "...$snippet..." else snippet
}

/**
 * 搜索结果卡片
 */
@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    keyword: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 记录集名 + 路径
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.notebookName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(4.dp))

            // 标题（高亮关键词）
            Text(
                text = highlightKeyword(item.title, keyword, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 内容片段（高亮关键词）
            if (item.contentSnippet.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = highlightKeyword(item.contentSnippet, keyword, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 构建高亮指定关键词的 AnnotatedString
 */
@Composable
private fun highlightKeyword(text: String, keyword: String, highlightColor: Color) = buildAnnotatedString {
    if (keyword.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    var currentIndex = 0
    val lowerText = text.lowercase()
    val lowerKeyword = keyword.lowercase()
    while (currentIndex < text.length) {
        val matchIndex = lowerText.indexOf(lowerKeyword, currentIndex)
        if (matchIndex < 0) {
            append(text.substring(currentIndex))
            break
        }
        // 匹配前的文本
        if (matchIndex > currentIndex) {
            append(text.substring(currentIndex, matchIndex))
        }
        // 高亮匹配部分
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(text.substring(matchIndex, matchIndex + keyword.length))
        }
        currentIndex = matchIndex + keyword.length
    }
}

@Composable
private fun SearchHistoryChip(
    text: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}