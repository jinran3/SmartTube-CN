#!/usr/bin/env bash
# SmartTube-CN patch set installer.
#
# Usage:  bash patches/apply.sh
# Run from the repo root on a PRISTINE upstream checkout (like the sync workflow does).
# Applies every patch in order; aborts loudly if any patch no longer applies
# (upstream code moved -> manual adaptation needed, see docs/SYNC_GUIDE.md).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

apply_patch() {
  local patch="$1"
  local target_dir="$2"
  echo "==> Applying ${patch} in ${target_dir}"
  # --ignore-space-change: the upstream repo stores sources with CRLF line
  # endings; our patch files use LF. Without this flag git apply would reject
  # every hunk on the CRLF checkout.
  if ! (cd "${target_dir}" && git apply --ignore-space-change --check "${ROOT}/${patch}" 2>/dev/null); then
    echo
    echo "ERROR: ${patch} does not apply cleanly on upstream master."
    echo "       Upstream code changed -> needs manual adaptation."
    echo "       See docs/SYNC_GUIDE.md for the workflow."
    exit 1
  fi
  (cd "${target_dir}" && git apply --ignore-space-change "${ROOT}/${patch}")
  echo "    OK"
}

apply_patch patches/01-okhttp-media-client-common.patch     "${ROOT}"
apply_patch patches/02-cf-workers-403-recovery.patch        "${ROOT}"
apply_patch patches/03-debug-info-zh-hdr.patch              "${ROOT}"
apply_patch patches/04-okhttp-media-client-sharedutils.patch "${ROOT}/SharedModules"
apply_patch patches/05-update-source-cn.patch           "${ROOT}"

echo
echo "All patches applied successfully."
