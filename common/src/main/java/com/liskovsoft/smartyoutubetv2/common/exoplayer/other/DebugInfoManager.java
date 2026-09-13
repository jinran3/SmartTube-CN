package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.sharedutils.cronet.CronetManager;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.DeviceHelpers;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryStringFactory;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.UhdHelper;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.ExoUtils;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.app.models.AppInfo;
import com.liskovsoft.youtubeapi.common.helpers.AppClient;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// NOTE: original file taken from
// https://github.com/google/ExoPlayer/blob/release-v2/library/ui/src/main/java/com/google/android/exoplayer2/ui/DebugTextViewHelper.java

/**
 * A helper class for periodically updating a {@link TextView} with debug information obtained from
 * a {@link SimpleExoPlayer}.
 */
public final class DebugInfoManager implements Runnable, Player.EventListener {
    private static final String TAG = DebugInfoManager.class.getSimpleName();
    private static final int REFRESH_INTERVAL_MS = 1000;
    private static final String NOT_AVAILABLE = "无";
    private final float mTextSize;

    private final SimpleExoPlayer mPlayer;
    private final ViewGroup mDebugViewGroup;
    private final ExoPlayerInitializer mPlayerInitializer;
    private final Context mContext;

    private boolean mStarted;
    private LinearLayout column1;
    private LinearLayout column2;
    private UhdHelper mUhdHelper;
    private final List<Pair<String, String>> mVideoInfo = new ArrayList<>();
    private final List<Pair<String, String>> mDisplayModeId = new ArrayList<>();
    private final List<Pair<String, String>> mDisplayInfo = new ArrayList<>();
    private final String mAppVersion;

    /**
     * @param debugViewGroup The container that should be updated to display the information.
     * @param player      The {@link SimpleExoPlayer} from which debug information should be obtained.
     * @param playerInitializer The {@link ExoPlayerInitializer} from which debug information should be obtained.
     */
    public DebugInfoManager(ViewGroup debugViewGroup, SimpleExoPlayer player, ExoPlayerInitializer playerInitializer) {
        mContext = debugViewGroup.getContext();
        mDebugViewGroup = debugViewGroup;
        mPlayer = player;
        mPlayerInitializer = playerInitializer;
        mTextSize = mContext.getResources().getDimension(R.dimen.debug_text_size);
        mAppVersion = String.format("%s 版本", mContext.getString(R.string.app_name));
        inflate();
    }

    private void inflate() {
        mDebugViewGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(R.layout.debug_view, mDebugViewGroup, true);
        column1 = mDebugViewGroup.findViewById(R.id.debug_view_column1);
        column2 = mDebugViewGroup.findViewById(R.id.debug_view_column2);
    }

    public void show(boolean show) {
        if (show) {
            create();
        } else {
            destroy();
        }
    }

    public boolean isShown() {
        return mStarted;
    }

    /**
     * Starts periodic updates of the {@link TextView}. Must be called from the application's main
     * thread.
     */
    private void create() {
        if (mStarted) {
            return;
        }

        mStarted = true;
        mDebugViewGroup.setVisibility(View.VISIBLE);
        mUhdHelper = new UhdHelper(mContext);
        mPlayer.addListener(this);
        updateAndPost();
    }

    /**
     * Stops periodic updates of the {@link TextView}. Must be called from the application's main
     * thread.
     */
    private void destroy() {
        if (!mStarted) {
            return;
        }

        mStarted = false;
        mDebugViewGroup.setVisibility(View.GONE);
        mPlayer.removeListener(this);
        mDebugViewGroup.removeCallbacks(this);
        mUhdHelper = null;
    }

    // Player.EventListener implementation.

    @Override
    public void onLoadingChanged(boolean isLoading) {
        // NOP
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        // NOP
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // NOP
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        // NOP
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // NOP
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        // Do nothing.
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        // Do nothing.
    }

    @Override
    public void onTracksChanged(TrackGroupArray tracks, TrackSelectionArray selections) {
        // NOP
    }

    // Runnable implementation.

    @Override
    public void run() {
        updateAndPost();
    }

    // Private methods.

    @SuppressLint("SetTextI18n")
    private void updateAndPost() {
        column1.removeAllViews();
        column2.removeAllViews();

        updateRareChangedValues();

        appendVideoInfo();
        appendRuntimeInfo();
        appendPlayerState();
        appendDisplayInfo();
        appendDisplayModeId();
        //appendPlayerWindowIndex();
        appendVersion();
        appendDeviceNameSDKCache();
        appendMemoryInfo();
        //appendWebViewInfo();
        //appendClientType();
        appendWebClientInfo();
        appendPlayerVersion();
        appendAccountInfo();

        // Schedule next update
        mDebugViewGroup.removeCallbacks(this);
        mDebugViewGroup.postDelayed(this, REFRESH_INTERVAL_MS);
    }

    private void updateRareChangedValues() {
        updateVideoInfo();
        updateDisplayModeId();
        updateDisplayInfo();
    }

    private void appendVideoInfo() {
        for (Pair<String, String> pair : mVideoInfo) {
            appendRow(pair.first, pair.second);
        }
    }

    private void updateVideoInfo() {
        mVideoInfo.clear();

        Format video = mPlayer.getVideoFormat();
        Format audio = mPlayer.getAudioFormat();
        if (video == null || audio == null) {
            return;
        }

        String videoRes = getVideoResolution(video) + getHdrTag(video);

        mVideoInfo.add(new Pair<>("视频分辨率", videoRes));
        mVideoInfo.add(new Pair<>("视频/音频编码", String.format(
                "%s(%s)/%s(%s)",
                getFormatMimeType(video),
                getFormatId(video),
                getFormatMimeType(audio),
                getFormatId(audio)
        )));
        mVideoInfo.add(new Pair<>("视频/音频码率", String.format(
                "%s/%s",
                toHumanReadable(video.bitrate),
                toHumanReadable(audio.bitrate)
        )));
        // Aspect info is not valid since we're using custom views
        //String par = video.pixelWidthHeightRatio == Format.NO_VALUE ||
        //        video.pixelWidthHeightRatio == 1f ?
        //        DEFAULT : String.format(Locale.US, "%.02f", video.pixelWidthHeightRatio);
        //mVideoInfo.add(new Pair<>("Aspect Ratio", par));
        String videoCodecName = getVideoDecoderNameV2();
        mVideoInfo.add(new Pair<>("视频解码器", videoCodecName));
        //mVideoInfo.add(new Pair<>("Hardware accelerated", String.valueOf(DeviceHelpers.isHardwareAccelerated(videoCodecName))));
        
        if (video.colorInfo != null) {
            //String transferFunction = getColorTransferString(video.colorInfo.colorTransfer);
            //if (!transferFunction.equals(NOT_AVAILABLE)) {
            //    mVideoInfo.add(new Pair<>("Transfer function", transferFunction));
            //}
            //String colorSpace = getColorSpaceString(video.colorInfo.colorSpace);
            //if (!colorSpace.equals(NOT_AVAILABLE)) {
            //    mVideoInfo.add(new Pair<>("Color space", colorSpace));
            //}

            String transferFunction = getColorTransferString(video.colorInfo.colorTransfer);
            String colorSpace = getColorSpaceString(video.colorInfo.colorSpace);
            String colorRange = getColorRangeString(video.colorInfo.colorRange);
            String hdri = getHdriString(video.colorInfo.hdrStaticInfo);

            // HDR 信息（含杜比视界）
            mVideoInfo.add(new Pair<>("HDR类型(DV)", getHdrTypeString(video)));
            mVideoInfo.add(new Pair<>("传输/空间/范围", transferFunction + "/" + colorSpace + "/" + hdri));
            mVideoInfo.add(new Pair<>("色彩范围", colorRange));
        }
    }

    private void appendRuntimeInfo() {
        DecoderCounters counters = mPlayer.getVideoDecoderCounters();
        if (counters == null)
            return;

        counters.ensureUpdated();
        appendRow("丢帧/已渲染帧", counters.droppedBufferCount + "/" + counters.renderedOutputBufferCount);
        appendRow("缓冲时长(秒)", (int)(mPlayer.getBufferedPosition() - mPlayer.getCurrentPosition()) / 1_000);
    }

    private void appendPlayerState() {
        //appendRow("Player paused", !mPlayer.getPlayWhenReady());

        String state;
        switch (mPlayer.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                state = "缓冲中";
                break;
            case Player.STATE_ENDED:
                state = "已结束";
                break;
            case Player.STATE_IDLE:
                state = "空闲";
                break;
            case Player.STATE_READY:
                state = "就绪";
                break;
            default:
                state = "未知";
                break;
        }
        //appendRow("Playback state", state);
        float boost = mPlayerInitializer.getVolumeBoost();
        appendRow("播放信息", String.format("暂停=%s;状态=%s", !mPlayer.getPlayWhenReady() ? "是" : "否", state));
        appendRow("音量",
                String.format("原始=%s;标准化=%s", PlayerData.instance(mContext).getPlayerVolume(), Helpers.formatFloat(boost * mPlayer.getVolume())));
    }

    private void appendDisplayModeId() {
        for (Pair<String, String> pair : mDisplayModeId) {
            appendRow(pair.first, pair.second);
        }
    }

    private void updateDisplayModeId() {
        if (mUhdHelper == null) {
            return;
        }

        mDisplayModeId.clear();

        Mode currentMode = mUhdHelper.getCurrentMode();
        Mode[] supportedModes = mUhdHelper.getSupportedModes();

        String bootResolution = AppPrefs.instance(mContext).getBootResolution();
        String currentResolution = UhdHelper.toResolution(currentMode);

        mDisplayModeId.add(new Pair<>("界面分辨率", currentResolution != null ? currentResolution : NOT_AVAILABLE));
        mDisplayModeId.add(new Pair<>("开机分辨率", bootResolution != null ? bootResolution : NOT_AVAILABLE));

        //mDisplayModeId.add(new Pair<>("Display mode ID", currentMode != null ? String.valueOf(currentMode.getModeId()) : NOT_AVAILABLE));
        //mDisplayModeId.add(new Pair<>("Display modes length", supportedModes != null ? String.valueOf(supportedModes.length) : NOT_AVAILABLE));
        String modeId = currentMode != null ? String.valueOf(currentMode.getModeId()) : NOT_AVAILABLE;
        String modeLength = supportedModes != null ? String.valueOf(supportedModes.length) : NOT_AVAILABLE;
        mDisplayModeId.add(new Pair<>("显示模式(ID/数量)", modeId + "/" + modeLength));
    }

    private void appendDisplayInfo() {
        for (Pair<String, String> pair : mDisplayInfo) {
            appendRow(pair.first, pair.second);
        }
    }

    private void updateDisplayInfo() {
        mDisplayInfo.clear();

        mDisplayInfo.add(new Pair<>("屏幕DPI", String.valueOf(Helpers.getDeviceDpi(mContext))));
    }

    private void appendPlayerWindowIndex() {
        appendRow("窗口索引", mPlayer.getCurrentWindowIndex());
    }

    private void appendVersion() {
        //appendRow("ExoPlayer version", ExoPlayerLibraryInfo.VERSION);
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(mContext);
        String engine = playerTweaksData.getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP ? "OkHttp" :
                playerTweaksData.getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET
                        && CronetManager.getEngine(mContext) != null ? "Cronet" : "默认";
        String protocol;
        Object manifest = mPlayer.getCurrentManifest();
        if (manifest == null) {
            protocol = "NONE";
        } else if (manifest instanceof DashManifest) {
            protocol = "DASH";
        } else if (manifest instanceof SabrManifest) {
            protocol = "SABR";
        } else {
            protocol = "HLS";
        }
        appendRow("播放器引擎", "引擎=" + engine + ";协议=" + protocol);
        //appendRow("Cronet version", ApiVersion.getCronetVersion());
        //appendRow("OkHttp version", Version.userAgent());
        appendRow(mAppVersion, AppInfoHelpers.getAppVersionName(mContext));
    }

    private void appendDeviceNameSDKCache() {
        appendRow("设备名称", Helpers.getDeviceName());
        appendRow("安卓版本", VERSION.SDK_INT);
        appendRow("磁盘缓存(MB)", String.valueOf(
                (FileHelpers.getDirSize(FileHelpers.getCacheDir(mContext)) + FileHelpers.getDirSize(FileHelpers.getExternalCacheDir(mContext)))
                        / 1024 / 1024
        ));
    }

    private void appendMemoryInfo() {
        //appendRow("Max heap memory (MB)", DeviceHelpers.getMaxHeapMemoryMB()); // Growth Limit
        //appendRow("Allocated heap memory (MB)", DeviceHelpers.getAllocatedHeapMemoryMB());
        appendRow("内存(已分配/上限, MB)", DeviceHelpers.getAllocatedHeapMemoryMB() + "/" + DeviceHelpers.getMaxHeapMemoryMB());
    }

    private void appendWebViewInfo() {
        appendRow("Pot supported", MediaServiceData.instance().isPotSupported());
    }

    private void appendClientType() {
        int videoInfoType = MediaServiceData.instance().getVideoInfoType();
        String clientType = videoInfoType != -1 && videoInfoType < AppClient.values().length ? AppClient.values()[videoInfoType].name() : "default";

        appendRow("Client type", clientType);
    }

    private void appendPlayerVersion() {
        AppInfo appInfo = Helpers.firstNonNull(MediaServiceData.instance().getFailedAppInfo(), MediaServiceData.instance().getAppInfo());
        String playerUrl = appInfo != null ? appInfo.getPlayerUrl() : null;
        if (playerUrl != null) {
            String playerVersion = UrlQueryStringFactory.parse(Uri.parse(playerUrl)).get("player");
            String shortPlayerUrl = playerVersion != null ? playerUrl.split(playerVersion)[1] : null;
            boolean isFailed = MediaServiceData.instance().getFailedAppInfo() != null;
            //appendRow("Player version", isFailed ? Utils.color(playerVersion, Color.RED) : playerVersion);
            //appendRow("Player url", isFailed ? Utils.color(shortPlayerUrl, Color.RED) : shortPlayerUrl);

            shortPlayerUrl = shortPlayerUrl != null ? shortPlayerUrl.split("/")[1] : null;
            CharSequence coloredVersion = isFailed ? Utils.color(playerVersion, Color.RED) : playerVersion;
            CharSequence coloredType = isFailed ? Utils.color(shortPlayerUrl, Color.RED) : shortPlayerUrl;
            appendRow("网页播放器(版本/类型)", TextUtils.concat(coloredVersion, "/", coloredType));
        }
    }

    private void appendWebClientInfo() {
        String clientType = getClientType();
        //CharSequence playerVersion = getPlayerVersion();
        boolean potSupported = MediaServiceData.instance().isPotSupported();

        appendRow("网页信息", "客户端=" + clientType + ";poToken=" + potSupported);
    }

    private void appendAccountInfo() {
        appendRow("账号信息", MediaServiceManager.instance().printAccountDebugInfo());
    }

    private void appendRow(String name, boolean val) {
        appendNameColumn(createTextView(name));
        appendValueColumn(createTextView(val));
    }

    private void appendRow(String name, CharSequence val) {
        appendNameColumn(createTextView(name));
        appendValueColumn(createTextView(val));
    }

    private void appendRow(String name, int val) {
        appendNameColumn(createTextView(name));
        appendValueColumn(createTextView(val));
    }

    private void appendNameColumn(TextView content) {
        content.setGravity(Gravity.END);
        column1.addView(content);
    }

    private void appendValueColumn(TextView content) {
        column2.addView(content);
    }

    private TextView createTextView(CharSequence name) {
        TextView textView = new TextView(mContext);
        textView.setText(name);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        return textView;
    }

    private TextView createTextView(boolean val) {
        TextView textView = new TextView(mContext);
        textView.setText(String.valueOf(val));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        return textView;
    }

    private TextView createTextView(int val) {
        TextView textView = new TextView(mContext);
        textView.setText(String.valueOf(val));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        return textView;
    }

    private String toHumanReadable(int bitrate) {
        if (bitrate < 0) {
            return NOT_AVAILABLE;
        }

        float mbit = ((float) bitrate) / 1_000_000;
        return String.format(Locale.ENGLISH, "%.2fMbps", mbit);
    }

    private String getFormatId(Format video) {
        return video.id;
    }

    private String getFormatMimeType(Format video) {
        if (video == null || video.sampleMimeType == null) {
            return null;
        }

        return video.sampleMimeType.replace("video/", "").replace("audio/", "");
    }

    private String getVideoResolution(Format video) {
        String result = video.width + "x" + video.height;
        if (video.frameRate > 0) {
            result += "@" + ((int) video.frameRate);
        }
        return result;
    }

    // NOTE: Be aware. This info isn't real! It's like caps or something like that. To get real info use method below.
    private String getVideoDecoderNameV1(Format format) {
        if (format == null) {
            return null;
        }

        MediaCodecInfo info = ExoUtils.getCapsDecoderInfo(format.sampleMimeType);

        return info != null ? info.name : null;
    }

    private String getVideoDecoderNameV2() {
        return ExoUtils.getVideoDecoderName();
    }

    private String getColorTransferString(int colorTransfer) {
        if (colorTransfer == Format.NO_VALUE) {
            return NOT_AVAILABLE;
        }

        switch (colorTransfer) {
            case C.COLOR_TRANSFER_SDR:
                return "Gamma";
            case C.COLOR_TRANSFER_ST2084:
                return "PQ (ST.2084)";
            case C.COLOR_TRANSFER_HLG:
                return "HLG";
            default:
                return NOT_AVAILABLE;
        }
    }

    private String getColorSpaceString(int colorSpace) {
        if (colorSpace == Format.NO_VALUE) {
            return NOT_AVAILABLE;
        }

        switch (colorSpace) {
            case C.COLOR_SPACE_BT601:
                return "BT.601";
            case C.COLOR_SPACE_BT709:
                return "BT.709";
            case C.COLOR_SPACE_BT2020:
                return "BT.2020";
            default:
                return NOT_AVAILABLE;
        }
    }

    /**
     * 视频色彩范围（全/有限）
     */
    private String getColorRangeString(int colorRange) {
        if (colorRange == Format.NO_VALUE) {
            return NOT_AVAILABLE;
        }

        return colorRange == C.COLOR_RANGE_FULL ? "全范围(Full)" : "有限范围(Limited)";
    }

    /**
     * 是否带静态 HDR 元数据
     */
    private String getHdriString(byte[] hdrStaticInfo) {
        return hdrStaticInfo != null ? "HDR" : "SDR";
    }

    /**
     * 当前视频的 HDR 类型（含杜比视界）
     */
    private String getHdrTypeString(Format video) {
        if (video == null) {
            return NOT_AVAILABLE;
        }

        if (isDolbyVision(video)) {
            return "杜比视界 (Dolby Vision)";
        }

        if (video.colorInfo == null) {
            return NOT_AVAILABLE;
        }

        switch (video.colorInfo.colorTransfer) {
            case C.COLOR_TRANSFER_ST2084:
                return "HDR10";
            case C.COLOR_TRANSFER_HLG:
                return "HLG";
            case C.COLOR_TRANSFER_SDR:
                return "SDR";
            default:
                return NOT_AVAILABLE;
        }
    }

    /**
     * 分辨率后的 HDR 简短标记，例如 (HDR10)、(DV)、(HLG)
     */
    private String getHdrTag(Format video) {
        if (video == null) {
            return "";
        }

        if (isDolbyVision(video)) {
            return " (DV)";
        }

        if (video.colorInfo != null) {
            switch (video.colorInfo.colorTransfer) {
                case C.COLOR_TRANSFER_ST2084:
                    return " (HDR10)";
                case C.COLOR_TRANSFER_HLG:
                    return " (HLG)";
                default:
                    return "";
            }
        }

        return "";
    }

    /**
     * 杜比视界检测：YouTube DV 流的 codecs 形如 dvhe.04.06 / dvh1.05.01 / dvav...
     */
    private boolean isDolbyVision(Format video) {
        String codecs = video != null && video.codecs != null ? video.codecs.toLowerCase(Locale.US) : null;
        String mime = video != null && video.sampleMimeType != null ? video.sampleMimeType.toLowerCase(Locale.US) : null;

        return (codecs != null && (codecs.contains("dvhe") || codecs.contains("dvh1") || codecs.contains("dvav") || codecs.contains("dolby")))
                || (mime != null && mime.contains("dolby"));
    }

    private String getClientType() {
        int videoInfoType = MediaServiceData.instance().getVideoInfoType();
        String clientType = videoInfoType != -1 && videoInfoType < AppClient.values().length ? AppClient.values()[videoInfoType].name() : "default";

        return clientType;
    }

    private CharSequence getPlayerVersion() {
        CharSequence result = NOT_AVAILABLE;
        AppInfo appInfo = Helpers.firstNonNull(MediaServiceData.instance().getFailedAppInfo(), MediaServiceData.instance().getAppInfo());
        String playerUrl = appInfo != null ? appInfo.getPlayerUrl() : null;
        if (playerUrl != null) {
            String playerVersion = UrlQueryStringFactory.parse(Uri.parse(playerUrl)).get("player");
            boolean isFailed = MediaServiceData.instance().getFailedAppInfo() != null;
            result = isFailed ? Utils.color(playerVersion, Color.RED) : playerVersion;
        }

        return result;
    }
}
