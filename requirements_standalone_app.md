# QQMusic_NetEaseCloudMusic 改造为独立 Python APP — 需求文档

> 产品负责人：product-manager
> 日期：2026-06-24
> 状态：待开发
> 项目地址：https://github.com/JianZhiHua/QQMusic_NetEaseCloudMusic

---

## 一、背景与目标

### 1.1 背景

QQMusic_NetEaseCloudMusic（「听见 · 音乐」）是一款基于 Python 3.12 + PySide6 的桌面音乐聚合播放器，支持 QQ 音乐和网易云音乐。当前项目以 pip 包形式分发（`pip install qqmusic-netease-player`），用户需要自行安装 Python 3.12、Node.js 18+、Rust 工具链，并手动编译 Rust 引擎和安装 Node.js 依赖。

**当前分发痛点**：
- 普通用户无法独立完成 Python 环境 + Node.js + Rust 的安装和编译
- pip 安装后还需手动启动 Node.js 桥接服务
- 无法通过控制面板卸载，缺乏标准 Windows 应用体验
- Rust 引擎需用户自行编译（`cargo build --release`）

### 1.2 目标

| # | 目标 | 验收标准 |
|---|------|----------|
| 1 | 转换为可直接运行的独立 APP | 双击 exe 即可启动，无需预装 Python/Node.js/Rust |
| 2 | 添加打包脚本 | 提供 PyInstaller 或 Nuitka 打包脚本，一键构建 |
| 3 | 打包为 Windows 安装包 | 输出标准 Windows 安装程序（.exe 或 .msi） |
| 4 | 支持完整卸载 | 控制面板可卸载，清除所有安装文件和注册表项 |
| 5 | 保留原有功能完整性 | 所有现有功能（双平台登录、歌单、搜索、播放、歌词）正常工作 |

---

## 二、用户画像

### 2.1 目标用户

| 特征 | 描述 |
|------|------|
| 技术水平 | 普通音乐爱好者，无编程背景 |
| 操作系统 | Windows 10/11（64-bit） |
| 核心诉求 | 双击安装、即装即用、干净卸载 |
| 使用场景 | 桌面端日常听歌，聚合 QQ 音乐 + 网易云音乐 |

### 2.2 用户故事

```
作为普通 Windows 用户，
我希望下载一个安装包，双击安装后直接使用「听见 · 音乐」，
以便不必折腾 Python、Node.js 等开发环境。
```

```
作为 Windows 用户，
我希望在「控制面板 → 程序和功能」中找到「听见 · 音乐」并卸载，
以便干净地移除应用及其所有文件。
```

```
作为开发者，
我希望有一键打包脚本，修改代码后能快速构建新的安装包，
以便高效迭代发布。
```

---

## 三、现状分析

### 3.1 项目架构

```
QQMusic_NetEaseCloudMusic/
├── src/music_player/          # Python 主程序（~5,112 行）
│   ├── app.py                 # 应用入口
│   ├── ui/main_window.py      # 主窗口（3,677 行，God Object）
│   ├── providers/             # QQ/网易云平台适配
│   ├── playback/              # 播放控制
│   ├── storage/               # 持久化存储
│   └── workers/               # 后台任务
├── engine/qq_resolver/        # Rust QQ 音乐解析器
│   └── Cargo.toml             # 依赖 unm_engine_qq
├── bridge/netease_api/        # Node.js 网易云桥接
│   ├── index.js               # 385 行
│   └── package.json           # 依赖 NeteaseCloudMusicApi
├── tests/                     # pytest 测试
├── pyproject.toml             # Python 打包配置
└── package.json               # Node.js workspace 配置
```

### 3.2 依赖分析

| 层级 | 依赖 | 类型 | 打包难度 |
|------|------|------|----------|
| Python 运行时 | Python 3.12 | 系统级 | 中（需嵌入或捆绑） |
| Python 包 | PySide6>=6.7 | pip | 中（Qt 库体积大） |
| Python 包 | requests>=2.32 | pip | 低 |
| Python 包 | platformdirs>=4.3 | pip | 低 |
| Python 包 | keyring>=25.0 | pip | 低 |
| Rust 二进制 | qq_resolver（unm_engine_qq） | 编译产物 | 高（需预编译） |
| Node.js 运行时 | Node.js 18+ | 系统级 | 高（需嵌入或捆绑） |
| Node.js 包 | NeteaseCloudMusicApi | npm | 中（需打包 node_modules） |

### 3.3 子进程通信机制

Python 主程序通过 **stdin/stdout JSON** 与两个外部进程通信：

1. **Rust qq_resolver**：Python 启动 Rust 编译产物进程，通过 stdin 发送 JSON 请求，stdout 读取 JSON 响应
2. **Node.js netease_api**：Python 启动 Node.js 进程运行 `bridge/netease_api/index.js`，同样通过 stdin/stdout JSON 通信

**关键约束**：打包后必须确保这两个子进程的可执行文件/运行时路径正确。

---

## 四、技术方案选型

### 4.1 Python 打包工具对比

| 维度 | PyInstaller | Nuitka | cx_Freeze |
|------|-------------|--------|-----------|
| **原理** | 解释器 + 依赖打包为 bundle | 编译为 C 后生成原生二进制 | 解释器 + 依赖复制到目录 |
| **打包速度** | 快（1-3 分钟） | 慢（10-30 分钟） | 快（1-2 分钟） |
| **启动速度** | 中（需解压 bundle） | 快（原生代码） | 中 |
| **产物体积** | 大（80-150 MB） | 中（50-100 MB） | 大（80-150 MB） |
| **PySide6 支持** | 成熟，有官方 hook | 需手动配置 | 基本支持 |
| **子进程支持** | 好（可捆绑外部二进制） | 好 | 好 |
| **调试难度** | 中（有 --onedir 模式） | 高（编译错误难定位） | 低 |
| **反编译保护** | 低（可提取 .pyc） | 高（编译为 C） | 低 |
| **社区活跃度** | 高 | 中 | 低 |
| **Windows 安装包** | 配合 NSIS/Inno Setup | 配合 NSIS/Inno Setup | 配合 NSIS/Inno Setup |

### 4.2 推荐方案：PyInstaller + NSIS

**选择理由**：

1. **PySide6 兼容性最好**：PyInstaller 对 Qt/PySide6 有成熟的 hook 支持，Nuitka 需要大量手动配置
2. **子进程捆绑方便**：PyInstaller 的 `--add-data` 可轻松捆绑 Rust 二进制和 Node.js 运行时
3. **调试友好**：`--onedir` 模式下可以直接检查打包产物结构
4. **社区资源丰富**：遇到问题容易找到解决方案
5. **配合 NSIS**：生成标准 Windows 安装程序，支持自定义安装路径、卸载程序、注册表项

**备选方案**：Nuitka（如果对启动速度和反编译保护有更高要求）

### 4.3 Node.js 运行时处理方案

| 方案 | 描述 | 体积 | 复杂度 |
|------|------|------|--------|
| **A. 捆绑 node.exe** | 将 Node.js 精简发行版嵌入安装包 | ~30 MB | 低 |
| B. pkg 打包为单文件 | 用 pkg 将 Node.js 脚本编译为单个 exe | ~50 MB | 中 |
| C. 重写为 Python | 将 NetEaseCloudMusicApi 的调用用 Python 重写 | 0 MB | 高 |

**推荐方案 A**：捆绑官方 Node.js 精简版（`node-v18.x.x-win-x64.zip` 中的 `node.exe`），体积可控且兼容性最好。

### 4.4 Rust 二进制处理

Rust qq_resolver 需要在构建安装包前预编译为 Windows x64 的 `.exe` 文件，然后由 PyInstaller 一起打包。

**构建流程**：
```
cargo build --release --manifest-path engine/qq_resolver/Cargo.toml
# 产物：engine/qq_resolver/target/release/qq_resolver.exe
```

---

## 五、功能需求（按优先级）

### P0 — 核心打包功能

#### F1. 打包脚本

**用户故事**：作为开发者，我希望运行一个脚本即可构建完整的 Windows 安装包，以便快速发布新版本。

**需求说明**：
- 提供 `build.py` 或 `build.bat` 一键打包脚本
- 自动检测并编译 Rust qq_resolver
- 自动下载 Node.js 精简运行时
- 自动安装 Python 依赖
- 调用 PyInstaller 打包 Python 主程序
- 调用 NSIS 生成安装程序

**验收标准**：
```
Given 开发者在 Windows 环境，已安装 Python 3.12、Rust 工具链、Node.js 18+
When 运行 build.py（或 build.bat）
Then 自动完成以下步骤：
  1. 编译 Rust qq_resolver 为 release 版本
  2. 下载 Node.js 精简运行时到 build/nodejs/
  3. 安装 Node.js 依赖到 build/bridge/
  4. 调用 PyInstaller 打包 Python 程序
  5. 调用 NSIS 生成安装程序
And 最终输出 dist/HearMusicSetup.exe
And 全程无手动干预
```

#### F2. PyInstaller 配置

**需求说明**：
- 编写 `.spec` 文件，配置 PyInstaller 打包参数
- 捆绑 Rust 二进制（`qq_resolver.exe`）
- 捆绑 Node.js 运行时和桥接脚本
- 捆绑 PySide6 Qt 插件（平台插件、多媒体插件）
- 设置应用图标
- 配置 Windows 版本信息（公司名、产品名、版本号）

**验收标准**：
```
Given PyInstaller .spec 文件已配置
When 执行 PyInstaller 打包
Then 产物目录包含：
  - HearMusic.exe（主程序）
  - _internal/qq_resolver.exe（Rust 二进制）
  - _internal/nodejs/node.exe（Node.js 运行时）
  - _internal/bridge/netease_api/（桥接脚本 + node_modules）
  - _internal/PySide6/（Qt 库）
And 双击 HearMusic.exe 可正常启动
```

#### F3. NSIS 安装程序

**需求说明**：
- 编写 NSIS 脚本（`installer.nsi`）
- 支持自定义安装路径（默认 `C:\Program Files\HearMusic`）
- 创建开始菜单快捷方式
- 创建桌面快捷方式（可选）
- 写入注册表卸载信息（`HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\HearMusic`）
- 生成 `uninstall.exe`

**验收标准**：
```
Given dist/HearMusicSetup.exe 已生成
When 用户双击运行安装程序
Then 显示安装向导（许可协议 → 选择路径 → 安装）
And 安装完成后开始菜单出现「听见 · 音乐」快捷方式
And 安装目录包含完整应用文件
And 注册表包含卸载信息
```

#### F4. 完整卸载支持

**需求说明**：
- 卸载程序删除安装目录下所有文件
- 删除开始菜单快捷方式
- 删除桌面快捷方式
- 清理注册表卸载项
- 可选：清除用户数据目录（`%APPDATA%\HearMusic`）中的配置和缓存

**验收标准**：
```
Given 「听见 · 音乐」已安装
When 用户通过「控制面板 → 程序和功能」卸载
Then 弹出卸载确认对话框
And 确认后删除安装目录所有文件
And 删除开始菜单和桌面快捷方式
And 删除注册表卸载项
And 可选勾选「同时删除用户数据」
And 卸载完成后控制面板不再显示该程序
```

### P1 — 子进程路径适配

#### F5. 子进程路径自适应

**需求说明**：
- 打包后 Rust 二进制和 Node.js 运行时位于 `_internal/` 目录
- Python 代码中的子进程启动路径需自适应：
  - 开发模式：使用项目目录下的相对路径
  - 打包模式：使用 `sys._MEIPASS` 或 `sys.executable` 同级目录
- 提供 `get_resource_path()` 工具函数统一处理路径查找

**验收标准**：
```
Given 应用已通过 PyInstaller 打包
When 用户启动应用并搜索歌曲
Then Python 主程序正确启动 qq_resolver.exe 子进程
And Python 主程序正确启动 node.exe + netease_api 子进程
And 搜索结果正常返回
And 无路径找不到的错误
```

#### F6. Node.js 桥接服务管理

**需求说明**：
- 打包后 Node.js 桥接服务由 Python 主程序自动管理
- 启动时自动启动 Node.js 进程
- 退出时自动终止 Node.js 进程
- Node.js 进程崩溃时自动重启（最多 3 次）
- node_modules 路径正确（打包后位于 `_internal/bridge/netease_api/`）

**验收标准**：
```
Given 应用已打包并安装
When 用户启动应用
Then Node.js 桥接服务自动启动（无需手动干预）
When 用户退出应用
Then Node.js 进程被正确终止
When Node.js 进程意外崩溃
Then 自动重启（最多 3 次），日志记录崩溃信息
```

### P2 — 构建优化

#### F7. 版本号管理

**需求说明**：
- 版本号统一管理（pyproject.toml + NSIS + 应用内显示）
- 支持 CI/CD 自动递增版本号
- 安装程序标题和「关于」页显示版本号

#### F8. 自动更新检查

**需求说明**：
- 应用启动时检查 GitHub Releases 是否有新版本
- 有新版本时提示用户下载（打开浏览器到下载页）
- 不实现自动更新（避免复杂的差分更新逻辑）

---

## 六、非功能需求

### 6.1 性能

| 指标 | 要求 |
|------|------|
| 安装包体积 | ≤ 200 MB（含 Node.js 运行时） |
| 安装时间 | ≤ 60 秒（SSD） |
| 冷启动时间 | ≤ 10 秒（从双击到主窗口显示） |
| 内存占用 | ≤ 300 MB（空闲状态） |

### 6.2 兼容性

| 平台 | 要求 |
|------|------|
| Windows 10 | 64-bit，版本 1809 及以上 |
| Windows 11 | 64-bit |
| 屏幕分辨率 | 最低 1280×720 |

### 6.3 安全

| 要求 | 说明 |
|------|------|
| 代码签名 | 可选，但推荐对 exe 进行代码签名以避免 Windows Defender 误报 |
| 杀毒误报 | PyInstaller 打包的 exe 常被误报，需提交白名单申请 |
| 用户数据 | Cookie 和凭据存储在 `%APPDATA%\HearMusic`，不随卸载删除（除非用户选择） |

---

## 七、验收标准总览

### 7.1 安装验收

```
Given 用户下载了 HearMusicSetup.exe
When 双击运行
Then 显示安装向导，支持自定义路径
And 安装完成后桌面和开始菜单有快捷方式
And 双击快捷方式可正常启动应用
```

### 7.2 功能验收

```
Given 应用已安装并启动
When 在设置页输入 QQ 音乐 Cookie 并校验
Then Cookie 校验成功，歌单同步正常

When 在设置页扫码登录网易云音乐
Then 登录成功，歌单同步正常

When 搜索周杰伦的歌曲
Then 返回双平台搜索结果

When 双击播放一首歌曲
Then 歌曲正常播放，歌词同步显示

When 切换播放模式（顺序/单曲/随机）
Then 播放行为符合预期
```

### 7.3 卸载验收

```
Given 应用已安装
When 通过控制面板卸载
Then 安装目录完全清除
And 快捷方式完全清除
And 注册表项完全清除
And 控制面板不再显示该程序
```

---

## 八、风险评估

| # | 风险 | 概率 | 影响 | 缓解措施 |
|---|------|------|------|----------|
| R1 | **PyInstaller + PySide6 打包失败** | 中 | 高 | PySide6 有成熟的 PyInstaller hook；失败时回退到 Nuitka |
| R2 | **Node.js 运行时路径问题** | 高 | 高 | 开发 `get_resource_path()` 统一管理；打包后充分测试子进程启动 |
| R3 | **杀毒软件误报** | 高 | 中 | PyInstaller 打包的 exe 常被误报；提交白名单申请；考虑代码签名 |
| R4 | **Rust 二进制跨平台编译** | 低 | 中 | 当前只需 Windows x64；在 CI 中用 `x86_64-pc-windows-msvc` target 编译 |
| R5 | **安装包体积过大** | 中 | 中 | PySide6 Qt 库体积大（~80MB）；可裁剪不需要的 Qt 模块 |
| R6 | **Windows Security 检测为 HackTool** | 中 | 高 | PROGRESS.md 已记录此问题；需在打包前确认检测路径并处理 |
| R7 | **Node.js 桥接进程泄漏** | 中 | 中 | 实现进程生命周期管理：启动时启动、退出时终止、崩溃时重启 |
| R8 | **keyring 在打包后不工作** | 低 | 中 | 测试打包后的 keyring 后端；必要时降级为文件存储 |
| R9 | **NSIS 安装程序 UAC 权限问题** | 低 | 低 | 安装到 Program Files 需管理员权限；NSIS 支持 `RequestExecutionLevel admin` |

---

## 九、排期建议

| 阶段 | 任务 | 工时 | 依赖 |
|------|------|------|------|
| **Phase 1** | F5. 子进程路径自适应 | 4h | 无 |
| **Phase 2** | F2. PyInstaller 配置 + 测试 | 6h | F5 |
| **Phase 3** | F6. Node.js 桥接服务管理 | 4h | F5 |
| **Phase 4** | F1. 一键打包脚本 | 4h | F2, F6 |
| **Phase 5** | F3. NSIS 安装程序 | 4h | F2 |
| **Phase 6** | F4. 卸载支持 | 2h | F3 |
| **Phase 7** | 集成测试 + 修复 | 6h | F1-F6 |
| **合计** | | **30h** | |

**建议里程碑**：
- **M1**（第 1 天）：子进程路径适配完成，开发模式下功能不退化
- **M2**（第 2-3 天）：PyInstaller 打包成功，exe 可独立运行
- **M3**（第 4 天）：安装程序 + 卸载功能完成
- **M4**（第 5 天）：集成测试通过，输出最终安装包

---

## 十、附录

### A. 当前 pyproject.toml 关键配置

```toml
[project]
name = "qqmusic-netease-player"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "PySide6>=6.7",
    "requests>=2.32",
    "platformdirs>=4.3",
    "keyring>=25.0",
]

[project.scripts]
music-player = "music_player.app:main"
```

### B. 子进程启动方式（当前）

- **Rust qq_resolver**：`subprocess.Popen(["qq_resolver"], stdin=PIPE, stdout=PIPE)`
- **Node.js bridge**：`subprocess.Popen(["node", "bridge/netease_api/index.js"], stdin=PIPE, stdout=PIPE)`

### C. 用户数据目录

- **Windows**：`%APPDATA%\HearMusic\`（由 platformdirs 决定）
- 包含：歌单缓存、播放队列、歌词设置、Cookie 存储
