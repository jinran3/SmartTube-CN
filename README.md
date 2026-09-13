# SmartTube-CN

**English** | [简体中文](README_zh-CN.md)

**SmartTube-CN** is an open-source Android TV build of [SmartTube](https://github.com/yuliskov/smarttube) that keeps in sync with upstream and focuses on two things:

- **Stable playback through CDN-based proxies** (Cloudflare Workers/Pages and similar setups, such as the `edgetunnel` script) where the exit IP can change between requests.
- **A localized debug & HDR panel** that shows Dolby Vision / HDR10 / HLG data in Chinese.

> ⚠️ **Not affiliated with the official SmartTube project.** This fork is based on the MIT-licensed [`yuliskov/smarttube`](https://github.com/yuliskov/smarttube) and is released under the same **MIT License** (see [`LICENSE`](LICENSE)). Please don't mistake this fork for the official app; file bugs/feedback on the upstream repository.

📦 **Releases:** https://github.com/jinran3/SmartTube-CN/releases · **Changelog:** [`CHANGELOG.md`](CHANGELOG.md)

---

## Why does playback stall on Android TV?

YouTube `googlevideo` stream URLs are **bound to the exit IP** of the requesting connection. Proxies on Cloudflare Workers/Pages may route every outbound request through a **different Cloudflare edge IP**. When that IP changes mid-playback, the old stream URL dies instantly with `Response code: 403`. ExoPlayer then keeps retrying the same dead URL, which shows up as an endless spinner or an "unknown source" error on the TV.

Mobile/browser clients often transparently reopen connections and recover fast, which is why the *same proxy* can feel smooth there but stall on a TV. This project makes the TV player behave similarly.

## Features

**1. 403 auto-recovery for Cloudflare Workers/Pages proxies**
- On `Response code: 403`, the player immediately invalidates the format-info cache and fetches a **fresh stream URL bound to the current exit IP**, then silently reconnects — no waiting, no blind client switching.
- Only after **2 consecutive 403s on the same video** does it fall back to the upstream "switch client" logic.
- DASH segments returning 403 **fail fast** (`TIME_UNSET`) instead of retrying a dead URL, turning minutes of spinning into an instant reconnect.

**2. OkHttp streaming client**
- A dedicated `getMediaClient()` with HTTP/2 multiplexing, a long-lived connection pool, a 60s read timeout and connection reuse. Segments and range requests share one stable connection, reducing proxy exit-IP rotation.

**3. Localized debug panel + HDR / Dolby Vision info**
- The debug overlay is fully translated to Chinese (resolution, bitrate, codec, decoder, buffering, dropped frames, playback state, volume, device info, …).
- New lines for `HDR类型(DV)` (Dolby Vision / HDR10 / HLG / SDR) and `色彩范围` (Full / Limited); the resolution is suffixed with `(DV)` / `(HDR10)` / `(HLG)`.

**4. In-app updates from this repository**
- The built-in update source points at this repository's Releases (not upstream), so installed builds update from here and are **never overwritten by a higher official versionCode**.
- Every release ships `smarttube_stable2.json` with per-ABI download URLs and a Chinese changelog, so the TV can update in a single tap.

## Compared to upstream (files changed)

| File | Module | Change |
| --- | --- | --- |
| `common/.../exoplayer/ExoMediaSourceFactory.java` | main | media downloads use the streaming OkHttp client |
| `common/.../playback/controllers/ErrorFixerController.java` | main | 403 cache invalidation + auto-reconnect, client switch after 2 tries |
| `common/.../exoplayer/errors/DashDefaultLoadErrorHandlingPolicy.java` | main | fail fast on DASH 403 |
| `common/.../exoplayer/other/DebugInfoManager.java` | main | Chinese debug panel + HDR / Dolby Vision |
| `SharedModules/.../okhttp/OkHttpCommons.java` | submodule | streaming timeout / keep-alive / HTTP2 config |
| `SharedModules/.../okhttp/OkHttpManager.java` | submodule | new `getMediaClient()` |

All modifications are maintained as patches in [`patches/`](patches/) — see [`docs/SYNC_GUIDE.md`](docs/SYNC_GUIDE.md).

## Install

Grab the APK for your device's ABI from [Releases](https://github.com/jinran3/SmartTube-CN/releases):

- `armeabi-v7a` — older ARM TVs
- `arm64-v8a` — most modern phones / TVs
- `x86` — emulators / x86 devices
- `universal` — works everywhere

Sideload it: `adb install -r <apk>`

### Build from source

```bash
# Requires: JDK 17 (Temurin), Android SDK (compileSdk 34 / build-tools 30.0.3)
bash patches/apply.sh
./gradlew :smarttubetv:assembleStstableDebug      # or gradlew.bat on Windows
```

Output: `smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_<version>_armeabi-v7a.apk`

> Note: a local build uses the `GITHUB_REPOSITORY` placeholder for the update source. Replace it with your own `owner/repo` (for example with `scripts/init-fork.ps1`) if you want in-app updates to point at your fork.

## Auto-sync with upstream

This repository tracks upstream `master` through GitHub Actions:

- Scheduled checks + manual trigger ("Run workflow" in the Actions tab).
- When upstream advances: rebase → apply patches → build all-ABI Debug APKs → publish a `-cn` Release (versionName suffixed `-cn-<date>`).
- If upstream changes break a patch, the workflow fails and asks for manual adaptation (see [`docs/SYNC_GUIDE.md`](docs/SYNC_GUIDE.md)).

## License & compliance

- Based on the MIT-licensed [`yuliskov/smarttube`](https://github.com/yuliskov/smarttube). The full copyright notice lives in [`LICENSE`](LICENSE) (`Copyright (c) 2020-present yuliskov`); our changes are documented here and in [`CHANGELOG.md`](CHANGELOG.md).
- This project is also released under the MIT License — source and patches are fully public.
- Please don't present this fork as the official SmartTube.