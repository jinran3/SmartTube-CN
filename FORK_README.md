# SmartTube-CN

基于 [yuliskov/smarttube](https://github.com/yuliskov/smarttube) 的非官方改版（Fork），针对中国大陆用户在使用
Cloudflare Workers/Pages 代理（如 edgetunnel）时遇到的**播放断流、403、转圈**问题做了优化，并为中文用户改造了
调试信息面板。

> ⚠️ 本项目是**个人改版**，与 SmartTube 官方无任何关联。请勿将本项目冒充官方版本。
> 原项目版权归 yuliskov 所有，许可证为 **MIT License**，本项目同样以 MIT License 发布（见 `LICENSE`）。

## 核心优化点

### 1. CF Workers/Pages 代理断流修复（403 自动恢复）
- 原因：YouTube `googlevideo` 流地址是**绑定出口 IP** 的；CF Workers/Pages 每个出站请求可能从不同的
  Cloudflare 边缘 IP 发出，出口 IP 一变，旧的流地址立刻失效（`Response code: 403`），ExoPlayer 却还在重试同一
  个死地址，表现为长时间转圈与“未知来源错误”。
- 修复：
  - 403 时**立刻失效格式信息缓存并重新获取绑定当前出口 IP 的全新播放地址**，静默自动重连，不再死等/盲目切换客户端；
  - 同一视频连续 2 次 403 才升级为切换客户端（保留官方兜底逻辑）；
  - DASH 分段 403 快速失败（不再用死地址重试），把数秒转圈压缩成即时重连。

### 2. OkHttp 流媒体专用客户端
- 新增独立 `getMediaClient()`（HTTP/2 多路复用 + 长连接池 + 60 秒读超时 + 连接复用），ExoPlayer 分段/范围请求
  共用一条稳定长连接，降低代理出口 IP 轮换概率（附 HTTP/1.1 回退）。

### 3. 中文调试面板 + HDR/杜比视界信息
- 调试信息覆盖层全部中文化（分辨率/码率/编码/解码器/缓冲/丢帧/播放状态/音量/设备信息等）；
- 新增 HDR 信息行：`HDR类型(DV)`（杜比视界 / HDR10 / HLG / SDR）、`色彩范围`（Full/Limited），分辨率末尾自动
  标注 `(DV)` / `(HDR10)` / `(HLG)`。

## 4. 本地电视端内置更新推送（重点）

- 官方内置更新源硬编码在 `update_urls.xml`（指向官方 yuliskov 的 Release），**不改的话 TV 端不但收不到你 fork 的更新，
  反而会被官方更高 versionCode 的版本“劫持”覆盖**。
- 本改版已把更新源指向**你自己这个 fork 仓库**的 Release；每次同步+编译发布时自动生成并上传
  `smarttube_stable2.json`（含每个 ABI 的下载地址 + 中文更新日志）。
- 电视端安装 fork 版后，启动或手动“检查更新”即可看到新版本并**在线一键升级**，无需再连电脑装 APK。
- 首次使用：请**手动安装一版 fork APK**，之后内置检查就只认你的 fork、不会再被官方带跑。

## 与官方版本的差异（改动文件）

| 文件 | 模块 | 改动 |
| --- | --- | --- |
| `common/.../exoplayer/ExoMediaSourceFactory.java` | 主仓 | 媒体下载改用流媒体专用 OkHttp 客户端 |
| `common/.../playback/controllers/ErrorFixerController.java` | 主仓 | 403 自动失效缓存重连，连续 2 次才切客户端 |
| `common/.../exoplayer/errors/DashDefaultLoadErrorHandlingPolicy.java` | 主仓 | DASH 403 快速失败 |
| `common/.../exoplayer/other/DebugInfoManager.java` | 主仓 | 中文调试面板 + HDR/杜比视界显示 |
| `SharedModules/.../okhttp/OkHttpCommons.java` | 子模块 | 新增流媒体专用超时/长连接/HTTP2 配置 |
| `SharedModules/.../okhttp/OkHttpManager.java` | 子模块 | 新增 `getMediaClient()` |

所有改动以补丁形式保存在 [`patches/`](patches/)（详见 [`docs/SYNC_GUIDE.md`](docs/SYNC_GUIDE.md)）。

## 本地构建（armeabi-v7a 中文面板版）

```bash
# 环境：JDK 17（Temurin）、Android SDK（compileSdk 34 / buildTools 30.0.3）、Gradle 7.5 wrapper
# linux/mac
bash patches/apply.sh
./gradlew :smarttubetv:assembleStstableDebug

# windows (powershell)
bash patches/apply.sh   # 或安装 Git Bash/msys2 后执行
.\gradlew.bat :smarttubetv:assembleStstableDebug
```

产物：`smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_<版本>_armeabi-v7a.apk`

> 本地 build 时内置更新源用的是占位符 `GITHUB_REPOSITORY`，会被 `scripts/init-fork.ps1` 自动替换为你自己的仓库；
> 若跳过该脚本纯手编，请自行把 `common/src/ststable|stbeta/res/values/update_urls.xml` 里的
> `GITHUB_REPOSITORY` 改成 `<你的用户名>/<仓库名>` 后再编译。
电视安装：`adb install -r <apk路径>`

## 自动同步官方更新

本仓通过 GitHub Actions 自动跟踪官方主仓 `master`：

- 定时检查 + 手动触发（Actions 页面 “Run workflow”）；
- 发现新提交后：拉取官方最新代码 → 自动应用本仓补丁 → 编译 `armeabi-v7a`（及全部 ABI）Debug APK → 发布到
  GitHub Release（带 `-cn` 后缀与同步日期标记）；
- 若官方代码改动导致补丁无法自动应用，workflow 会失败并提示人工适配（见 [`docs/SYNC_GUIDE.md`](docs/SYNC_GUIDE.md)）。

## 许可证与合规说明

- 本项目基于 MIT License 的 [yuliskov/smarttube](https://github.com/yuliskov/smarttube) 修改，完整版权声明见
  `LICENSE`（`Copyright (c) 2020-present yuliskov`），改动内容见本文档与 `CHANGELOG.md`；
- 本项目以 **MIT License** 发布，源代码与补丁全部公开，符合 MIT 的版权声明保留义务；
- 请勿将本改版错误地标识为官方 SmartTube；正式反馈请前往官方仓库。
