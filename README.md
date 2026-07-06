# Record 记录集

<div align="center">

一款基于 **Jetpack Compose** 构建的 Android 记录本应用，支持多层树形章节管理、富文本记录、附件管理、导入导出等功能。

</div>

---

## 功能特性

### 📚 记录集（Notebook）
- 创建、重命名、删除记录集
- 支持自定义封面图片（从图库选择）
- 支持自定义描述文本
- 可设置封面背景色
- 首页网格视图展示所有记录集

### 🌲 三级树形结构
| 层级 | 名称 | 说明 |
|------|------|------|
| Level 1 | 大章节 | 可展开/折叠，支持收藏、排序 |
| Level 2 | 文件夹 / 直达记录 | 作为中间层级组织内容 |
| Level 3 | 具体记录 | 记录正文的入口节点 |

- 各级节点均支持 **长按菜单** 进行：重命名、移动、删除
- 章节（Level 1）支持 **收藏** 标记
- 章节和文件夹支持展开/折叠动画

### 📝 记录编辑
- 标题 + 富文本内容编辑
- **版本管理**（每次编辑自动递增版本号）
- 保存后自动记录修改历史

### 📜 修改历史
- 记录每次变更的时间戳和备注信息
- 完整的变更追溯

### 📎 附件管理
- 支持附加任意类型的文件
- 支持查看已添加的附件列表
- 支持通过文件选择器添加多个附件
- 可打开/删除附件

### 🏷️ 自定义元数据（Custom MetaData）
- 键值对形式扩展记录属性
- 支持添加、编辑、删除


### ⬆️⬇️ 导入/导出
- **导出格式**：`.recordbook`（标准 ZIP 包）
  - 包含完整的记录集结构、所有记录内容、附件文件
  - 采用 JSON 配置 + 附件文件的打包方式
- **导入**：支持从 `.recordbook` 文件恢复完整记录集

### 🔍 搜索
- 快速搜索记录集
---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | **Kotlin** |
| UI 框架 | **Jetpack Compose** + **Material 3** |
| 数据库 | **Room**（SQLite）+ **KSP** 编译处理器 |
| 导航 | **Navigation Compose** |
| 图片加载 | **Coil** |
| 序列化 | **Gson** |
| 最低 SDK | **24**（Android 7.0） |
| 目标 SDK | **36** |

---

## 项目结构

```
app/src/main/java/com/yutaca/record/
├── MainActivity.kt                  # 主入口 Activity
├── navigation/
│   └── NavGraph.kt                  # 导航图定义
├── data/
│   ├── database/
│   │   └── AppDatabase.kt           # Room 数据库
│   ├── entity/
│   │   ├── NotebookEntity.kt        # 记录集
│   │   ├── TreeNodeEntity.kt        # 树形节点
│   │   ├── RecordEntity.kt          # 记录
│   │   ├── AttachmentEntity.kt      # 附件
│   │   ├── CustomMetaDataEntity.kt  # 自定义元数据
│   │   └── ModificationHistoryEntity.kt  # 修改历史
│   ├── dao/
│   │   ├── NotebookDao.kt
│   │   ├── TreeNodeDao.kt
│   │   ├── RecordDao.kt
│   │   ├── AttachmentDao.kt
│   │   ├── CustomMetaDataDao.kt
│   │   └── ModificationHistoryDao.kt
│   ├── repository/
│   │   ├── NotebookRepository.kt
│   │   ├── TreeNodeRepository.kt
│   │   └── RecordRepository.kt
│   └── export/
│       ├── NotebookExporter.kt      # .recordbook 导出
│       └── NotebookImporter.kt      # .recordbook 导入
└── ui/
    ├── home/
    │   ├── HomeScreen.kt            # 首页 - 记录集列表
    │   └── HomeViewModel.kt
    ├── directory/
    │   ├── DirectoryScreen.kt       # 目录 - 树形章节管理
    │   └── DirectoryViewModel.kt
    └── record/
        ├── RecordDetailScreen.kt    # 记录详情 - 编辑/附件/历史
        └── RecordDetailViewModel.kt
```

### 数据模型关系

```
Notebook (1)
   │
   └── TreeNode (N) — 树形节点（自引用外键 parentId）
          │
          ├── 非叶子节点：继续包含子节点
          └── 叶子节点 ──→ Record (1)
                              │
                              ├── Attachment (N)
                              ├── CustomMetaData (N)
                              └── ModificationHistory (N)
```

---

## 快速开始

### 前置要求

- **Android Studio** Hedgehog 或更高版本
- **JDK 17**
- **Gradle 8.x**

### 构建运行

```bash
# 克隆项目
git clone https://github.com/amzzma/Record.git

# 用 Android Studio 打开项目根目录
# 等待 Gradle 同步完成

# 或命令行构建调试 APK
./gradlew assembleDebug
```

### 构建 Release APK

```bash
./gradlew assembleRelease
```

> 注意：Release 构建启用了 ProGuard 混淆与资源压缩，可通过 `app/proguard-rules.pro` 自定义规则。

---

## 使用指南

### 1. 首页 — 记录集管理
- 打开应用进入「记录集列表」页
- 点击 **+** 或空状态下的加号按钮创建新记录集
- 长按记录集卡片可重命名、导出、删除
- 点击右上角菜单可导入 `.recordbook` 文件

### 2. 目录 — 章节组织
- 进入记录集后默认显示「章节」Tab
- 点击 **添加章节或记录** 创建新节点
- 选择层级：添加到一级章节 / 二级文件夹下
- 节点操作：点击展开/折叠，长按显示上下文菜单

### 3. 记录 — 编辑与附件
- 点击任意记录节点进入编辑页面
- 编辑标题和正文内容，点击保存按钮
- 展开「附件」面板管理文件
- 展开「自定义元数据」面板管理键值对
- 查看「修改历史」了解变更记录

### 4. 导出/导入
- **导出**：目录页菜单 → 导出记录本，生成 `.recordbook` 文件
- **导入**：首页菜单 → 导入，选择 `.recordbook` 文件还原

---

## 导出文件格式

`.recordbook` 文件是一个标准 ZIP 包，内部结构如下：

```
notebook.json          # 记录集元数据 + 树结构 + 所有记录
attachments/
  ├── {refId}_{filename}   # 附件文件
  └── ...
```

`notebook.json` 包含完整的结构信息，支持版本兼容（当前版本 `v1`）。

---

## 开发

### 代码风格
- 遵循 Kotlin 官方编码规范
- UI 层采用 Compose 函数式组件
- 数据层采用 Repository 模式
- 使用 Flow + StateFlow 实现响应式数据流

### 数据库升级
Room 数据库通过 `@Database` 注解的 `version` 管理迁移，详见 `AppDatabase.kt`。

---

## License

```
Copyright 2026 Yutaca

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 贡献

欢迎提交 Issue 和 Pull Request ~