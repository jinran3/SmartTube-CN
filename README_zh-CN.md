# SmartTube-CN

[English](README.md) | **简体中文**

**SmartTube-CN** 是基于 MIT 官方仓库 [yuliskov/smarttube](https://github.com/yuliskov/smarttube) 的 Android TV 开源改版，跟随官方持续同步。它重点解决两类问题：

- **使用 CDN 类代理（Cloudflare Workers/Pages，如 edgetunnel）看 YouTube 时播放转圈、断流、403**；
- **更清晰的中文调试信息与 HDR/杜比视界显示**。

> ⚠️ 本项目与官方 SmartTube 无任何关联，是基于 MIT 协议官方仓库的改版，同样以 **MIT License** 发布（见 [`LICENSE`](LICENSE)）。请勿将本改版冒充官方版本；反馈、报错请前往官方仓库。

📦 **下载**：[Releases](https://github.com/jinran3/SmartTube-CN/releases) · **升级日志**：[`CHANGELOG.md`](CHANGELOG.md)

---

## 为什么电视端会转圈/断流？

YouTube `googlevideo` 流地址是**绑定出口 IP** 的。Cloudflare Workers/Pages 每次出站请求可能走不同边缘 IP，出口 IP 一变，旧流地址立即失效（`Response code: 403`）。ExoPlayer 却仍不断重试这个死地址，电视端就表现为长时间转圈或“未知来源错误”。

手机/浏览器客户端通常会透明地重开连接快速自愈，所以同样的代理在手机电脑上不卡、在电视上却卡——本项目就是让电视端也具备这种快速恢复能力。

## 核心特性

**1. CF Workers/Pages 代理 403 自动恢复**
- 遇到 `Response code: 403` 立即失效格式信息缓存，重新获取绑定当前出口 IP 的全新播放地址并静默重连，不死等、不盲目切换客户端；
- 仅当**同一视频连续 2 次 403** 才回退到官方“切换客户端”逻辑；
- DASH 分段 403 快速失败（`TIME_UNSET`），不再用死地址反复重试，把几分钟转圈压缩为即时重连。

**2. OkHttp 流媒体专用客户端**
- 独立的 `getMediaClient()`：HTTP/2 多路复用 + 长连接池 + 60 秒读超时 + 连接复用；分段/范围请求共用一条稳定连接，降低代理出口 IP 轮换概率。

**3. 中文调试面板 + HDR/杜比视界信息**
- 调试覆盖层全中文化（分辨率、码率、编码、解码器、缓冲、丢帧、播放状态、音量、设备信息等）；
- 新增 `HDR类型(DV)`（杜比视界 / HDR10 / HLG / SDR）、`色彩范围`（Full/Limited），分辨率末尾自动标注 `(DV)/(HDR10)/(HLG)`。

**4. 内置更新指向本仓库**
- 内置更新源指向本仓库 Releases（而非官方），已安装版本从本仓库更新，**不会被官方更高 versionCode 覆盖**；
- 每次发布自带 `smarttube_stable2.json`（各 ABI 下载地址 + 中文更新日志），电视端可一键在线升级。

## 与官方版本的差异（改动文件）

| 文件 | 模块 | 改动 |
| --- | --- | --- |
| `common/.../exoplayer/ExoMediaSourceFactory.java` | 主仓 | 媒体下载改用流媒体专用 OkHttp 客户端 |
| `common/.../playback/controllers/ErrorFixerController.java` | 主仓 | 403 自动失效缓存重连，连续 2 次才切客户端 |
| `common/.../exoplayer/errors/DashDefaultLoadErrorHandlingPolicy.java` | 主仓 | DASH 403 快速失败 |
| `common/.../exoplayer/other/DebugInfoManager.java` | 主仓 | 中文调试面板 + HDR/杜比视界 |
| `SharedModules/.../okhttp/OkHttpCommons.java` | 子模块 | 流媒体超时/长连接/HTTP2 配置 |
| `SharedModules/.../okhttp/OkHttpManager.java` | 子模块 | 新增 `getMediaClient()` |

所有改动以补丁形式维护在 [`patches/`](patches/)，详见 [`docs/SYNC_GUIDE.md`](docs/SYNC_GUIDE.md)。

## 安装

按设备 ABI 从 [Releases](https://github.com/jinran3/SmartTube-CN/releases) 下载对应 APK：
- `armeabi-v7a`：较老的 ARM 电视
- `arm64-v8a`：多数现代手机/电视
- `x86`：模拟器 / x86 设备
- `universal`：通用

侧载安装：`adb install -r <apk>`

### 本地构建

```bash
# 环境：JDK 17（Temurin）、Android SDK（compileSdk 34 / build-tools 30.0.3）
bash patches/apply.sh
./gradlew :smarttubetv:assembleStstableDebug      # Windows 用 gradlew.bat
```

产物：`smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_<版本>_armeabi-v7a.apk`

> 注意：本地构建的内置更新源使用 `GITHUB_REPOSITORY` 占位符；如需内置更新指向你自己的 fork，请替换为你的 `owner/repo`（例如用 `scripts/init-fork.ps1`）。

## 自动同步官方更新

本仓库通过 GitHub Actions 自动跟踪官方主仓 `master`：

- 定时检查 + 手动触发（Actions 页 “Run workflow”）；
- 官方有提交时：rebase → 应用补丁 → 编译全部 ABI 的 Debug APK → 发布带 `-cn-<日期>` 后缀的 Release；
- 若官方改动导致补丁无法自动应用，workflow 会失败并提示人工适配（见 [`docs/SYNC_GUIDE.md`](docs/SYNC_GUIDE.md)）。

## 许可与合规

- 基于 MIT 协议的 [`yuliskov/smarttube`](https://github.com/yuliskov/smarttube)。完整版权声明见 [`LICENSE`](LICENSE)（`Copyright (c) 2020-present yuliskov`）；改动见本文档与 [`CHANGELOG.md`](CHANGELOG.md)；
- 本项目同样以 MIT License 发布，源码与补丁全部公开；
- 请勿将本改版标注为官方 SmartTube。

完整升级日志见 [`CHANGELOG.md`](CHANGELOG.md)。