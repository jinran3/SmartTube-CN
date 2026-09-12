# 自动同步与发布指南（Sync & Release）

本仓通过 GitHub Actions 自动跟踪官方上游 `https://github.com/yuliskov/smarttube`，把官方新代码与本仓补丁
合并编译后发布。

## 工作机制

工作流 `.github/workflows/sync-and-release.yml` 在 `main` 分支上运行：

1. **触发**：`schedule`（默认每 6 小时）+ `workflow_dispatch`（手动 “Run workflow”，可勾选
   `Force rebuild` 强制重新构建）；
2. **拉取上游**：`git fetch https://github.com/yuliskov/smarttube.git master`；
3. **判断是否更新**：对比 `LAST_SYNC` 文件中记录的上游 commit；无更新直接退出；
4. **重建分支**：以上游最新 commit 为基线新建 `sync-build` 分支（干净官方代码），
   再从 `main` 恢复本仓专属文件（`.github/`、`patches/`、`docs/`、`FORK_README.md`、`CHANGELOG.md`、`LAST_SYNC`）；
5. **初始化子模块**：`git submodule update --init --recursive`；
6. **应用补丁**：执行 `patches/apply.sh`（主仓 3 个补丁 + `SharedModules` 子模块 1 个补丁）；
7. **编译**：JDK 17 + Android SDK（compileSdk 34 / buildTools 30.0.3），`./gradlew :smarttubetv:assembleStstableDebug`
   产出全部 ABI（armeabi-v7a / arm64-v8a / x86 / universal）；
8. **提交**：将“官方新代码 + 补丁 + 本仓文件”整体提交并**强制更新 `main`**（保持源码可直接查看）；
   `SharedModules` 子模块的改动**只以补丁形式在构建时应用**（`patches/04-…`），不修改子模块指针，
   保证任何人 clone 本仓（含子模块）都能成功；
9. **发布**：以 `v<版本>-cn-<日期>` 标签创建 GitHub Release，附加全部 ABI 的 APK 与 `smarttube_stable2.json`（内置更新源），更新 `LAST_SYNC`。

> 首次运行即使上游没有新提交也会构建（因为还没有任何 `-cn` 发布）；之后只有上游有更新（或勾选
> `Force rebuild`）才会重新构建。

## 补丁冲突时的处理（人工适配）

官方改动如果碰了补丁涉及的文件（常见：`ErrorFixerController`、`ExoMediaSourceFactory`、`DebugInfoManager`、
`OkHttpCommons`、`OkHttpManager`），`git apply --check` 会失败，workflow 终止并提示错误。此时在本地适配：

```bash
git fetch https://github.com/yuliskov/smarttube.git master
git checkout -B sync-build FETCH_HEAD
git checkout main -- .github patches docs FORK_README.md CHANGELOG.md LICENSE LAST_SYNC
git submodule update --init --recursive
bash patches/apply.sh            # 查看哪个补丁失败
# 手动把该补丁的改动“手工移植”到新代码上（通常改动很小），然后：
git add -A && git commit -m "Adapt patch xxx for upstream <sha>"
git diff main > patches/新补丁.patch   # 重新生成新补丁
# 更新 patches/apply.sh 后提交，再触发 workflow
```

## 仓库初始化（首次推送）

1. 在 GitHub 新建空仓库（例如 `SmartTube-CN`），**不要**勾选自动生成 README；
2. 运行 `scripts/init-fork.ps1 <你的用户名>/<仓库名>`（会自动创建并推送 `main` 分支，`master` 保留官方代码）；
3. 打开仓库 **Settings → Actions → General → Workflow permissions**，选择 **Read and write permissions**，
   并允许 GitHub Actions 创建/更新（force push）`main` 分支；
4. 到 **Actions** 页手动运行一次 `Sync & Release`，确认绿色；之后每 6 小时自动同步。

## 版本号规则

- 沿用官方 `smarttubetv/build.gradle` 的 `versionCode` / `versionName`；
- Release 标签 `v32.40-cn-20260905`，APK 名带 `-cn` 后缀与日期，避免与官方版本混淆。

## 电视端内置更新

- `update_urls.xml`（stable/beta）用占位符 `GITHUB_REPOSITORY`，构建前由 workflow 用 `${{ github.repository }}`
  替换（`scripts/init-fork.ps1` 推送时也会替成你的仓库），保证任何 fork 编译产物都指向它自己的 Release。
- 每个 Release 都会附带 `smarttube_stable2.json`：`package` 节点含各 ABI（armeabi-v7a / arm64-v8a / x86 /
  universal）的下载地址，版本节点含 `versionCode` 与 `changelog` / `changelog_zh`（中文 TV 端显示中文日志）。
- fork 沿用官方 `versionCode`（单调递增），`versionName` 追加 `-cn-日期`。内置检查按 `versionCode` 判断；
  所以电视端请**至少手动安装一版 fork APK**，之后每次同步发布都会自动推送升级。

## 合规提醒

- 上游为 MIT License，保留 `LICENSE`（版权归 yuliskov）即可合法分发；
- 本仓名与 Release 均明确标注 `-cn` 改版、非官方；
- 不要把本仓库冒充官方 SmartTube 发布渠道。

## 构建产物位置（CI）

`smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_<版本>-cn_<ABI>.apk`
