# Hear 项目 5 项问题修复 — 需求文档

> 产品负责人：product-manager
> 日期：2026-06-24
> 状态：待开发

---

## 优先级总览

| # | 问题 | 优先级 | 复杂度 | 预估工时 |
|---|------|--------|--------|----------|
| 3 | 定时停止去掉 90 分钟 | P0 | 低 | 5 min |
| 2 | QQ 音乐歌单筛选按钮换行 | P0 | 低 | 15 min |
| 1 | 自动提取按钮不可见 | P1 | 中 | 1h |
| 4 | 版本页检查更新 | P1 | 中 | 2h |
| 5 | 版本号自动递增 | P2 | 中 | 1.5h |

---

## 1. 定时停止去掉 90 分钟

### 用户故事

作为用户，我希望定时停止的预设列表不包含 90 分钟选项，以便选项更精简实用。

### 当前代码

`SettingsPage.kt:290`
```kotlin
val presets = listOf(15, 30, 45, 60, 90, 0)
```

### 需求说明

- 从 `presets` 列表中移除 `90`，保留 `15, 30, 45, 60, 0`
- 0 代表"关闭"，保持不变
- 无其他 UI 或逻辑变更

### 验收标准

```
Given 用户打开设置页，查看「定时停止」区域
When 预设选项加载完成
Then 仅显示 15分钟、30分钟、45分钟、60分钟、关闭 共 5 个选项
And 不存在 90分钟 选项
```

```
Given 用户已选择 60 分钟定时停止
When 点击 60分钟
Then 定时器设置为 60 分钟，显示"60 分钟后"
```

### 技术实现建议

- 修改 `SettingsPage.kt` 第 290 行，移除 `90`
- 无需新增依赖或修改其他文件

### 影响范围

- 仅 `SettingsPage.kt` 一处改动
- 不影响已有的定时停止逻辑（`PlaybackManager` 层面支持任意分钟数）

---

## 2. QQ 音乐歌单筛选按钮换行

### 用户故事

作为用户，我希望「我的音乐」页面的平台筛选按钮（全部/本地/网易云音乐/QQ音乐）始终单行显示，屏幕不够宽时可以横向滚动，避免按钮折行影响视觉。

### 当前代码

`Components.kt:299`
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { (value, label) ->
        SegmentButton(...)
    }
}
```

问题：`Row` 不支持滚动，4 个按钮（尤其"网易云音乐"较长）在窄屏设备上会折行。

### 需求说明

- 将 `SourceSegment` 中的 `Row` 改为支持横向滚动的容器
- 使用 `Modifier.horizontalScroll(rememberScrollState())` 或 `LazyRow`
- 按钮顺序不变：全部 → 本地 → 网易云音乐 → QQ 音乐
- 同时影响「我的音乐」页和「聚合搜索」页（两处都使用 `SourceSegment`）

### 验收标准

```
Given 用户在 360dp 宽度的设备上打开「我的音乐」页
When 页面加载完成
Then 4 个筛选按钮全部可见，单行排列
And 按钮区域可横向滚动
And 按钮不会折行到第二行
```

```
Given 用户在「聚合搜索」页
When 查看平台切换按钮
Then 同样为单行横向滚动，不折行
```

### 技术实现建议

方案 A（推荐）：在 `Row` 上添加 `Modifier.horizontalScroll(rememberScrollState())`
```kotlin
Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) { ... }
```

方案 B：使用 `LazyRow` 替代 `Row`，适合按钮数量可能动态变化的场景。

需要新增 import：
```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
```

### 影响范围

- `Components.kt` 的 `SourceSegment` 函数
- `LibraryPage.kt` 和 `SearchPage.kt` 均调用该组件，自动受益

---

## 3. 自动提取按钮不可见

### 用户故事

作为用户，我希望在设置页的账号卡片中看到「自动提取」按钮（当 Shizuku 可用时），以便一键从 QQ 音乐/网易云音乐 APP 提取 Cookie，无需手动复制。

### 当前代码分析

调用链：
1. `HearApp.kt:137` — `isShizukuAvailable = viewModel.isShizukuAvailable()`
2. `HearViewModel.kt:188` — `fun isShizukuAvailable(): Boolean = ShizukuCookieExtractor.isAvailable()`
3. `SettingsPage.kt:84` — `onExtractCookie = if (isShizukuAvailable) { ... } else null`
4. `SettingsPage.kt:201-205` — `if (onExtractCookie != null) { OutlinedButton(...) }`

问题根因：`isShizukuAvailable` 在 Compose 组合时一次性求值，不是响应式的。Shizuku 服务可能在 APP 启动后才就绪（用户需要手动激活 Shizuku），此时已经组合完毕，按钮不会出现。

### 需求说明

- 将 Shizuku 可用性检查改为响应式，能够感知 Shizuku 状态变化
- 当 Shizuku 从不可用变为可用时，自动显示「自动提取」按钮
- 当 Shizuku 从可用变为不可用时，自动隐藏按钮
- 保持现有权限请求流程不变

### 验收标准

```
Given 用户已安装 Shizuku 但尚未激活
When 打开设置页
Then 账号卡片中不显示「自动提取」按钮
```

```
Given 用户已安装并激活 Shizuku，已授权 Hear 应用
When 打开设置页（或 Shizuku 激活后返回设置页）
Then 每个账号卡片的按钮行中显示「自动提取」按钮
And 按钮位于「校验并保存」之后、「同步歌单」之前
```

```
Given 用户点击「自动提取」按钮
When Shizuku 权限未授予
Then 弹出 Shizuku 权限请求
And 授权成功后执行 Cookie 提取
```

### 技术实现建议

**方案：将 `isShizukuAvailable` 改为 StateFlow**

1. 在 `HearViewModel` 中新增：
```kotlin
private val _isShizukuAvailable = MutableStateFlow(false)
val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()
```

2. 在 ViewModel init 中注册 Shizuku 状态监听：
```kotlin
init {
    // ... 现有初始化 ...
    
    // Shizuku 状态监听
    try {
        Shizuku.addBinderReceivedListener { refreshShizukuState() }
        Shizuku.addBinderDeadListener { _isShizukuAvailable.value = false }
        Shizuku.addRequestPermissionResultListener { _, _ -> refreshShizukuState() }
        refreshShizukuState()
    } catch (_: Exception) {
        // Shizuku 未安装，忽略
    }
}

private fun refreshShizukuState() {
    _isShizukuAvailable.value = ShizukuCookieExtractor.isAvailable()
}
```

3. 在 `HearApp.kt` 中改为收集 StateFlow：
```kotlin
val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsStateWithLifecycle()
```

4. 删除 `HearViewModel.isShizukuAvailable()` 方法（或保留作为内部辅助）

5. `SettingsPage` 签名和调用逻辑不变，仅数据源从一次性调用变为 StateFlow 收集

### 影响范围

- `HearViewModel.kt` — 新增 StateFlow + 监听
- `HearApp.kt` — 改为 collectAsStateWithLifecycle
- `SettingsPage.kt` — 无需修改（签名不变）
- 新增依赖：无（Shizuku API 已在 dependencies 中）

---

## 4. 版本页检查更新

### 用户故事

作为用户，我希望在设置页点击版本号时能检查 GitHub Release 是否有新版本，有新版本时提示我下载更新。

### 当前代码

`SettingsPage.kt:146-152`
```kotlin
SettingsRow(
    icon = Icons.Default.MusicNote,
    title = "版本号",
    subtitle = "V1.0.0",
    onClick = null,  // 当前不可点击
)
```

### 需求说明

- 版本号行变为可点击
- 点击后检查 GitHub Release 最新版本
- 与当前 APP 版本对比，判断是否有更新
- 有新版本：显示对话框，包含版本号、更新说明、「前往下载」按钮（打开浏览器）
- 无新版本：Toast 提示"已是最新版本"
- 检查中：显示加载状态（按钮 disabled 或 subtitle 显示"检查中..."）
- GitHub 仓库地址：从 build.yml 推断为 `qingyi/hear`（需确认实际仓库名）

### 验收标准

```
Given 用户打开设置页
When 查看「关于」区域
Then 版本号行显示"V{当前版本}"，可点击
```

```
Given 用户点击版本号行
When GitHub API 返回的最新 Release 版本号 > 当前版本
Then 弹出对话框，显示：
  - 标题："发现新版本"
  - 内容：最新版本号 + 更新说明摘要
  - 按钮："前往下载"（打开 Release 页面）+ "取消"
```

```
Given 用户点击版本号行
When GitHub API 返回的最新版本号 == 当前版本
Then Toast 提示"已是最新版本"
```

```
Given 用户点击版本号行
When 网络请求失败或超时
Then Toast 提示"检查更新失败，请稍后重试"
```

```
Given 用户在对话框中点击「前往下载」
When 跳转到浏览器
Then 打开 GitHub Release 页面的对应版本链接
```

### 技术实现建议

**1. 版本号来源**

当前版本硬编码在两处：
- `build.gradle.kts:18` — `versionName = "1.0"`
- `SettingsPage.kt:149` — `subtitle = "V1.0.0"`

统一方案：在 `BuildConfig` 中注入版本号，运行时读取。
```kotlin
// build.gradle.kts — 已有 versionName，无需额外配置
// 读取方式：
val versionName = context.packageManager
    .getPackageInfo(context.packageName, 0).versionName
```

**2. GitHub Release 检查**

新增 `UpdateChecker` 工具类：
```kotlin
object UpdateChecker {
    private const val REPO = "qingyi/hear"  // 需确认实际仓库名
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    
    suspend fun checkForUpdate(currentVersion: String): UpdateResult {
        // GET API_URL
        // 解析 tag_name（格式：v20260624-abc1234 或 v1.0.1）
        // 与 currentVersion 比较
    }
}

sealed class UpdateResult {
    data class Available(val version: String, val url: String, val body: String) : UpdateResult()
    object UpToDate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
```

**3. UI 交互**

- `SettingsPage` 的 `SettingsRow` `onClick` 回调触发检查
- 新增 `isCheckingUpdate` 状态到 `HearUiState`
- 检查完成后显示 Dialog 或 Toast

**4. 注意事项**

- GitHub API 有未认证 60 次/小时的限制，考虑缓存结果
- 当前 Release tag 格式为 `v{日期}-{sha}`（如 `v20260624-e9a7f47`），不是语义版本号
- 需要统一版本号格式（与 Issue #5 联动），使比较逻辑可靠

### 影响范围

- `SettingsPage.kt` — 版本行改为可点击，新增 Dialog
- `HearViewModel.kt` — 新增检查更新逻辑
- `HearUiState` — 新增 `isCheckingUpdate`、`updateResult` 字段
- 新增文件：`UpdateChecker.kt`（网络请求 + 版本比较）
- `network/Http.kt` — 可复用现有 OkHttpClient
- 需要 `INTERNET` 权限（已隐含在 APP 中）

### 依赖关系

- 与 Issue #5（版本号自动递增）强关联：版本号格式统一后比较逻辑才可靠
- 建议 #5 先行或同步实施

---

## 5. 版本号自动递增

### 用户故事

作为开发者，我希望推送到 GitHub 时 CI 自动递增版本号（格式 `major.minor.patch`），无需手动修改 build.gradle.kts。

### 当前代码

`app/build.gradle.kts:17-18`
```kotlin
versionCode = 1
versionName = "1.0"
```

`.github/workflows/build.yml:63`
```yaml
TAG="v$(date +%Y%m%d)-${GITHUB_SHA::7}"
```

问题：
- versionCode 和 versionName 硬编码，不随发布递增
- Release tag 使用日期+SHA 格式，不是语义版本号
- 无法通过 tag 自动比较版本大小

### 需求说明

- 版本号格式：`major.minor.patch`（如 `1.0.0` → `1.0.1` → `1.1.0` → `2.0.0`）
- CI 推送到 master 时自动递增 patch 版本（最后一位）
- versionCode 同步递增（整数，用于 Android 系统判断更新）
- Release tag 使用 `v{major}.{minor}.{patch}` 格式
- 支持手动指定 major/minor 跳版本（通过 commit message 或 workflow_dispatch 输入）

### 验收标准

```
Given 当前 master 分支版本为 1.0.3
When 推送新 commit 到 master
Then CI 自动构建，版本号变为 1.0.4
And versionCode 从当前值 +1
And Release tag 为 v1.0.4
```

```
Given 当前版本为 1.0.9
When 推送 commit，commit message 包含 [minor]
Then 版本号变为 1.1.0
And versionCode 递增
```

```
Given 当前版本为 1.9.9
When 推送 commit，commit message 包含 [major]
Then 版本号变为 2.0.0
```

```
Given 手动触发 workflow_dispatch
When 在 inputs 中指定 version_override 为 "2.0.0"
Then 使用指定版本号构建和发布
```

### 技术实现建议

**方案：CI 中从最新 Release tag 推算下一版本**

```yaml
# build.yml 新增步骤
- name: Determine next version
  if: success() && github.ref == 'refs/heads/master'
  id: version
  run: |
    # 获取最新 Release tag
    LATEST=$(gh release list --limit 1 --json tagName --jq '.[0].tagName' | sed 's/^v//')
    
    if [ -z "$LATEST" ]; then
      LATEST="1.0.0"
    fi
    
    # 解析 major.minor.patch
    IFS='.' read -r MAJOR MINOR PATCH <<< "$LATEST"
    
    # 根据 commit message 判断递增级别
    COMMIT_MSG=$(git log -1 --pretty=%B)
    if echo "$COMMIT_MSG" | grep -q '\[major\]'; then
      MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0
    elif echo "$COMMIT_MSG" | grep -q '\[minor\]'; then
      MINOR=$((MINOR + 1)); PATCH=0
    else
      PATCH=$((PATCH + 1))
    fi
    
    VERSION="${MAJOR}.${MINOR}.${PATCH}"
    VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
    
    echo "version=$VERSION" >> $GITHUB_OUTPUT
    echo "versionCode=$VERSION_CODE" >> $GITHUB_OUTPUT
    echo "tag=v$VERSION" >> $GITHUB_OUTPUT
```

**build.gradle.kts 动态注入版本号：**
```kotlin
// 从 local.properties 或环境变量读取 CI 注入的版本号
val ciVersionName = System.getenv("VERSION_NAME") ?: "1.0.0"
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

android {
    defaultConfig {
        versionCode = ciVersionCode
        versionName = ciVersionName
    }
}
```

**CI 中传递环境变量：**
```yaml
- name: Build Release APK
  run: ./gradlew assembleRelease
  env:
    VERSION_NAME: ${{ steps.version.outputs.version }}
    VERSION_CODE: ${{ steps.version.outputs.versionCode }}
```

### 影响范围

- `.github/workflows/build.yml` — 版本计算 + tag 格式 + 环境变量传递
- `app/build.gradle.kts` — 从环境变量读取版本号（保留 fallback）
- 需要 `GITHUB_TOKEN` 权限调用 `gh release list`（已有 `contents: write`）

### 依赖关系

- Issue #4（版本页检查更新）依赖本 Issue 的版本号格式统一
- 建议先实施本 Issue

---

## 实施顺序建议

```
Phase 1（快速修复，可并行）
  ├── #3 定时停止去掉 90 分钟（5 min）
  └── #2 筛选按钮换行（15 min）

Phase 2（功能增强）
  ├── #5 版本号自动递增（1.5h）
  └── #4 版本页检查更新（2h，依赖 #5 的版本格式）

Phase 3（体验优化）
  └── #1 自动提取按钮不可见（1h）
```

---

## 技术风险

| 风险 | 影响 Issue | 缓解措施 |
|------|-----------|----------|
| Shizuku API 在 Android 14+ 上行为变化 | #1 | 测试多个 Android 版本，增加 fallback 提示 |
| GitHub API 限流（60次/小时） | #4 | 本地缓存检查结果，限制检查频率 |
| CI 中 `gh` 命令不可用 | #5 | 使用 `curl` + GitHub API 替代 |
| 当前 Release tag 非语义版本 | #4, #5 | 首次部署时重置 tag 基线 |
