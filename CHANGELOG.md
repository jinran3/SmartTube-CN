# 变更日志 (Changelog)

本改版的所有改动均在官方代码之上以补丁形式维护，以下是相对于官方 `yuliskov/smarttube` 的完整改动记录。

## [32.40-cn.1] - 2026-09-05
基于官方 `32.40`（commit `25b7def`）修改。

### 新增：CF Workers/Pages 代理 403 自动恢复
- `ErrorFixerController`：
  - 捕获流加载 `Response code: 403`（典型场景：Cloudflare 出口 IP 轮换导致 googlevideo 判定流地址失效）；
  - 修复后行为：失效格式信息缓存 → 重新获取绑定当前出口 IP 的全新播放地址 → 静默自动重连；
  - 同一视频连续 2 次 403 失败才回退到官方“切换客户端”逻辑（`switchNextClientNow`），避免无谓烧完客户端列表；
  - 计数器在恢复播放（`onPlay`）或切换新视频时自动复位。
- `DashDefaultLoadErrorHandlingPolicy`：DASH 分段/清单请求遇到 403 直接快速失败（`TIME_UNSET`），不再对已失效
  的死地址反复重试，缩短“转圈”时间。

### 新增：OkHttp 流媒体专用客户端
- `OkHttpManager`：新增 `getMediaClient()`，独立于常规 API 客户端；
- `OkHttpCommons`：新增 `setupMediaBuilder()` —— HTTP/2 多路复用（含 HTTP/1.1 回退）、长连接池
  （8 连接 / 30 分钟保活）、媒体读超时 60 秒、失败自动重连；
- `ExoMediaSourceFactory`：媒体下载（DASH/SABR/HLS）改用该流媒体客户端；默认 HTTP 引擎读超时也提升到 60 秒。

### 新增：中文调试面板 + HDR/杜比视界信息
- `DebugInfoManager`：调试信息覆盖层全部中文化（分辨率、码率、编码、解码器、缓冲、丢帧、播放状态、音量、
  分辨率/DPI、设备/安卓版本、磁盘缓存、内存、网页播放器、账号信息等）；
- 新增 `HDR类型(DV)` 行：杜比视界 / HDR10 / HLG / SDR（杜比视界通过 codecs `dvhe/dvh1/dvav/dolby` 或 mime
  `dolby` 识别）；
- 新增 `色彩范围`（Full/Limited）与 `传输/空间/范围`（PQ/HLG/BT.2020）中文显示；
- 视频分辨率末尾自动标注 `(DV)` / `(HDR10)` / `(HLG)`。

### 新增：本地电视端内置更新推送
- `common/src/ststable|stbeta/res/values/update_urls.xml`：把内置更新源从官方指向本 fork 仓库的 Release
  （URL 用 `GITHUB_REPOSITORY` 占位，由 `scripts/init-fork.ps1` 或 CI workflow 替换为实际仓库）；
- CI 发布时自动生成并上传 `smarttube_stable2.json`（含各 ABI 下载地址 + 中文更新日志 `changelog_zh`），
  电视端启动/手动检查即可在线升级，避免被官方更高 versionCode 的版本覆盖；
- 版本语义：fork 沿用官方 `versionCode`，`versionName` 追加 `-cn-<日期>`；请至少手动安装一版 fork APK 使
  内置检查生效。

## 协议
- 本改版基于 MIT License 的官方仓库修改，版权声明见根目录 `LICENSE`；
- 本改版同样以 MIT License 发布。
