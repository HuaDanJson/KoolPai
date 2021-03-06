package com.qiniu.csvc.activity;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.qiniu.csvc.R;
import com.qiniu.csvc.base.BaseActivity;
import com.qiniu.csvc.utils.ActivityUtil;
import com.qiniu.csvc.utils.Config;
import com.qiniu.csvc.utils.GetPathFromUri;
import com.qiniu.csvc.utils.RecordSettings;
import com.qiniu.csvc.view.CustomProgressDialog;
import com.qiniu.csvc.view.FocusIndicator;
import com.qiniu.csvc.view.SectionProgressBar;
import com.qiniu.csvc.view.VerticalSeekBar;
import com.qiniu.pili.droid.shortvideo.PLAudioEncodeSetting;
import com.qiniu.pili.droid.shortvideo.PLCameraSetting;
import com.qiniu.pili.droid.shortvideo.PLDraft;
import com.qiniu.pili.droid.shortvideo.PLDraftBox;
import com.qiniu.pili.droid.shortvideo.PLFaceBeautySetting;
import com.qiniu.pili.droid.shortvideo.PLFocusListener;
import com.qiniu.pili.droid.shortvideo.PLMicrophoneSetting;
import com.qiniu.pili.droid.shortvideo.PLRecordSetting;
import com.qiniu.pili.droid.shortvideo.PLRecordStateListener;
import com.qiniu.pili.droid.shortvideo.PLShortVideoRecorder;
import com.qiniu.pili.droid.shortvideo.PLVideoEncodeSetting;
import com.qiniu.pili.droid.shortvideo.PLVideoSaveListener;

import java.util.List;
import java.util.Stack;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.qiniu.csvc.utils.RecordSettings.RECORD_SPEED_ARRAY;

public class ShortVideoActivity extends BaseActivity implements PLRecordStateListener, PLVideoSaveListener, PLFocusListener {

    private static final String TAG = "VideoRecordActivity";
    public static final String PREVIEW_SIZE_RATIO = "PreviewSizeRatio";
    public static final String PREVIEW_SIZE_LEVEL = "PreviewSizeLevel";
    public static final String ENCODING_MODE = "EncodingMode";
    public static final String ENCODING_SIZE_LEVEL = "EncodingSizeLevel";
    public static final String ENCODING_BITRATE_LEVEL = "EncodingBitrateLevel";
    public static final String AUDIO_CHANNEL_NUM = "AudioChannelNum";
    public static final String DRAFT = "draft";
    private static final boolean USE_TUSDK = true;

    @BindView(R.id.preview) GLSurfaceView mGLSurfaceView;
    @BindView(R.id.adjust_brightness_button) ImageView adjustBrightnessButton;
    @BindView(R.id.adjust_brightness) VerticalSeekBar mAdjustBrightnessSeekBar;
    @BindView(R.id.brightness_panel) LinearLayout brightnessPanel;
    @BindView(R.id.super_slow_speed_text) TextView superSlowSpeedText;
    @BindView(R.id.slow_speed_text) TextView slowSpeedText;
    @BindView(R.id.normal_speed_text) TextView mSpeedTextView;
    @BindView(R.id.fast_speed_text) TextView fastSpeedText;
    @BindView(R.id.super_fast_speed_text) TextView superFastSpeedText;
    @BindView(R.id.ll_speed) LinearLayout llSpeed;
    @BindView(R.id.focus_indicator) FocusIndicator mFocusIndicator;
    @BindView(R.id.screen_rotate_button) ImageView screenRotateButton;
    @BindView(R.id.capture_frame_button) ImageView captureFrameButton;
    @BindView(R.id.switch_camera) ImageView mSwitchCameraBtn;
    @BindView(R.id.switch_flash) ImageView mSwitchFlashBtn;
    @BindView(R.id.record_progressbar) SectionProgressBar mSectionProgressBar;
    @BindView(R.id.delete) ImageView mDeleteBtn;
    @BindView(R.id.record) ImageView mRecordBtn;
    @BindView(R.id.concat) ImageView mConcatBtn;
    @BindView(R.id.btns) LinearLayout btns;
    @BindView(R.id.audio_mix_button) ImageView audioMixButton;
    @BindView(R.id.recording_percentage) TextView mRecordingPercentageView;
    @BindView(R.id.save_to_draft_button) ImageView saveToDraftButton;
    @BindView(R.id.bottom_control_panel) LinearLayout mBottomControlPanel;

    private PLShortVideoRecorder mShortVideoRecorder;
    private boolean mSectionBegan;
    private double mRecordSpeed;
    private Stack<Long> mDurationRecordStack = new Stack();
    private Stack<Double> mDurationVideoStack = new Stack();
    private PLRecordSetting mRecordSetting;
    private CustomProgressDialog mProcessingDialog;
    private long mLastRecordingPercentageViewUpdateTime = 0;
    private boolean mFlashEnabled;
    private boolean mIsEditVideo = false;
    private GestureDetector mGestureDetector;
    private PLCameraSetting mCameraSetting;
    private PLMicrophoneSetting mMicrophoneSetting;
    private PLVideoEncodeSetting mVideoEncodeSetting;
    private PLAudioEncodeSetting mAudioEncodeSetting;
    private PLFaceBeautySetting mFaceBeautySetting;
    private int mFocusIndicatorX;
    private int mFocusIndicatorY;
    private OrientationEventListener mOrientationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_short_video);
        ButterKnife.bind(this);
        getPermission();
    }

    public void getPermission() {
        PermissionUtils.permission(PermissionConstants.CAMERA, PermissionConstants.MICROPHONE)
                .rationale(new PermissionUtils.OnRationaleListener() {
                    @Override
                    public void rationale(final ShouldRequest shouldRequest) {
                        shouldRequest.again(true);
                    }
                })
                .callback(new PermissionUtils.FullCallback() {
                    @Override
                    public void onGranted(List<String> permissionsGranted) {
                        initCamera();
                    }

                    @Override
                    public void onDenied(List<String> permissionsDeniedForever,
                                         List<String> permissionsDenied) {
                        if (!permissionsDeniedForever.isEmpty()) {

                        }
                    }
                }).request();
    }

    public void initCamera() {
        mRecordSpeed = RECORD_SPEED_ARRAY[2];
        mRecordSetting = new PLRecordSetting();
        mProcessingDialog = new CustomProgressDialog(this);
        mProcessingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mShortVideoRecorder.cancelConcat();
            }
        });
        mShortVideoRecorder = new PLShortVideoRecorder();
        mShortVideoRecorder.setRecordStateListener(this);
        mShortVideoRecorder.setFocusListener(this);
        mRecordSpeed = RECORD_SPEED_ARRAY[2];
        String draftTag = getIntent().getStringExtra(DRAFT);
        if (draftTag == null) {
            int previewSizeRatioPos = getIntent().getIntExtra(PREVIEW_SIZE_RATIO, 0);
            int previewSizeLevelPos = getIntent().getIntExtra(PREVIEW_SIZE_LEVEL, 0);
            int encodingModePos = getIntent().getIntExtra(ENCODING_MODE, 0);
            int encodingSizeLevelPos = getIntent().getIntExtra(ENCODING_SIZE_LEVEL, 0);
            int encodingBitrateLevelPos = getIntent().getIntExtra(ENCODING_BITRATE_LEVEL, 0);
            int audioChannelNumPos = getIntent().getIntExtra(AUDIO_CHANNEL_NUM, 0);
            mCameraSetting = new PLCameraSetting();
            PLCameraSetting.CAMERA_FACING_ID facingId = chooseCameraFacingId();
            mCameraSetting.setCameraId(facingId);
            mCameraSetting.setCameraPreviewSizeRatio(RecordSettings.PREVIEW_SIZE_RATIO_ARRAY[previewSizeRatioPos]);
            mCameraSetting.setCameraPreviewSizeLevel(RecordSettings.PREVIEW_SIZE_LEVEL_ARRAY[previewSizeLevelPos]);
            mMicrophoneSetting = new PLMicrophoneSetting();
            mMicrophoneSetting.setChannelConfig(RecordSettings.AUDIO_CHANNEL_NUM_ARRAY[audioChannelNumPos] == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
            mVideoEncodeSetting = new PLVideoEncodeSetting(this);
            mVideoEncodeSetting.setEncodingSizeLevel(RecordSettings.ENCODING_SIZE_LEVEL_ARRAY[encodingSizeLevelPos]);
            mVideoEncodeSetting.setEncodingBitrate(RecordSettings.ENCODING_BITRATE_LEVEL_ARRAY[encodingBitrateLevelPos]);
            mVideoEncodeSetting.setHWCodecEnabled(encodingModePos == 0);
            mVideoEncodeSetting.setConstFrameRateEnabled(true);

            mAudioEncodeSetting = new PLAudioEncodeSetting();
            mAudioEncodeSetting.setHWCodecEnabled(encodingModePos == 0);
            mAudioEncodeSetting.setChannels(RecordSettings.AUDIO_CHANNEL_NUM_ARRAY[audioChannelNumPos]);

            mRecordSetting = new PLRecordSetting();
            mRecordSetting.setMaxRecordDuration(RecordSettings.DEFAULT_MAX_RECORD_DURATION);
            mRecordSetting.setRecordSpeedVariable(true);
            mRecordSetting.setVideoCacheDir(Config.VIDEO_STORAGE_DIR);
            mRecordSetting.setVideoFilepath(Config.RECORD_FILE_PATH);
            mFaceBeautySetting = new PLFaceBeautySetting(1.0f, 0.5f, 0.5f);
            mShortVideoRecorder.prepare(mGLSurfaceView, mCameraSetting, mMicrophoneSetting, mVideoEncodeSetting, mAudioEncodeSetting, USE_TUSDK ? null : mFaceBeautySetting, mRecordSetting);
            mSectionProgressBar.setFirstPointTime(RecordSettings.DEFAULT_MIN_RECORD_DURATION);
            onSectionCountChanged(0, 0);
        } else {
            PLDraft draft = PLDraftBox.getInstance(this).getDraftByTag(draftTag);
            if (draft == null) {
                ToastUtils.showShort(getString(R.string.toast_draft_recover_fail));
                finish();
            }
            mCameraSetting = draft.getCameraSetting();
            mMicrophoneSetting = draft.getMicrophoneSetting();
            mVideoEncodeSetting = draft.getVideoEncodeSetting();
            mAudioEncodeSetting = draft.getAudioEncodeSetting();
            mRecordSetting = draft.getRecordSetting();
            mFaceBeautySetting = draft.getFaceBeautySetting();
            if (mShortVideoRecorder.recoverFromDraft(mGLSurfaceView, draft)) {
                long draftDuration = 0;
                for (int i = 0; i < draft.getSectionCount(); ++i) {
                    long currentDuration = draft.getSectionDuration(i);
                    draftDuration += draft.getSectionDuration(i);
                    onSectionIncreased(currentDuration, draftDuration, i + 1);
                    if (!mDurationRecordStack.isEmpty()) {
                        mDurationRecordStack.pop();
                    }
                }
                mSectionProgressBar.setFirstPointTime(draftDuration);
                ToastUtils.showShort(getString(R.string.toast_draft_recover_success));
            } else {
                onSectionCountChanged(0, 0);
                mSectionProgressBar.setFirstPointTime(RecordSettings.DEFAULT_MIN_RECORD_DURATION);
                ToastUtils.showShort(getString(R.string.toast_draft_recover_fail));
            }
        }
        mShortVideoRecorder.setRecordSpeed(mRecordSpeed);
        mSectionProgressBar.setProceedingSpeed(mRecordSpeed);
        mSectionProgressBar.setTotalTime(this, mRecordSetting.getMaxRecordDuration());

        mRecordBtn.setOnTouchListener(new View.OnTouchListener() {
            private long mSectionBeginTSMs;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!mSectionBegan && mShortVideoRecorder.beginSection()) {
                        mSectionBegan = true;
                        mSectionBeginTSMs = System.currentTimeMillis();
                        mSectionProgressBar.setCurrentState(SectionProgressBar.State.START);
                        updateRecordingBtns(true);
                    } else {
                        ToastUtils.showShort("无法开始视频段录制");
                    }
                } else if (action == MotionEvent.ACTION_UP) {
                    if (mSectionBegan) {
                        long sectionRecordDurationMs = System.currentTimeMillis() - mSectionBeginTSMs;
                        long totalRecordDurationMs = sectionRecordDurationMs + (mDurationRecordStack.isEmpty() ? 0 : mDurationRecordStack.peek().longValue());
                        double sectionVideoDurationMs = sectionRecordDurationMs / mRecordSpeed;
                        double totalVideoDurationMs = sectionVideoDurationMs + (mDurationVideoStack.isEmpty() ? 0 : mDurationVideoStack.peek().doubleValue());
                        mDurationRecordStack.push(new Long(totalRecordDurationMs));
                        mDurationVideoStack.push(new Double(totalVideoDurationMs));
                        if (mRecordSetting.IsRecordSpeedVariable()) {
                            mSectionProgressBar.addBreakPointTime((long) totalVideoDurationMs);
                        } else {
                            mSectionProgressBar.addBreakPointTime(totalRecordDurationMs);
                        }

                        mSectionProgressBar.setCurrentState(SectionProgressBar.State.PAUSE);
                        mShortVideoRecorder.endSection();
                        mSectionBegan = false;
                    }
                }

                return false;
            }
        });
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                mFocusIndicatorX = (int) e.getX() - mFocusIndicator.getWidth() / 2;
                mFocusIndicatorY = (int) e.getY() - mFocusIndicator.getHeight() / 2;
                mShortVideoRecorder.manualFocus(mFocusIndicator.getWidth(), mFocusIndicator.getHeight(), (int) e.getX(), (int) e.getY());
                return false;
            }
        });

        mGLSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mGestureDetector.onTouchEvent(motionEvent);
                return true;
            }
        });

        mOrientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                int rotation = getScreenRotation(orientation);
                if (!mSectionProgressBar.isRecorded() && !mSectionBegan) {
                    mVideoEncodeSetting.setRotationInMetadata(rotation);
                }
            }
        };
        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mShortVideoRecorder != null) {
            mShortVideoRecorder.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mShortVideoRecorder != null) {
            mShortVideoRecorder.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mShortVideoRecorder != null) {
            mShortVideoRecorder.destroy();
        }
    }

    private int getScreenRotation(int orientation) {
        int screenRotation = 0;
        boolean isPortraitScreen = getResources().getConfiguration().orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (orientation >= 315 || orientation < 45) {
            screenRotation = isPortraitScreen ? 0 : 90;
        } else if (orientation >= 45 && orientation < 135) {
            screenRotation = isPortraitScreen ? 90 : 180;
        } else if (orientation >= 135 && orientation < 225) {
            screenRotation = isPortraitScreen ? 180 : 270;
        } else if (orientation >= 225 && orientation < 315) {
            screenRotation = isPortraitScreen ? 270 : 0;
        }
        return screenRotation;
    }

    private PLCameraSetting.CAMERA_FACING_ID chooseCameraFacingId() {
        if (PLCameraSetting.hasCameraFacing(PLCameraSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD)) {
            return PLCameraSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
        } else if (PLCameraSetting.hasCameraFacing(PLCameraSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT)) {
            return PLCameraSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
        } else {
            return PLCameraSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
        }
    }

    private void onSectionCountChanged(final int count, final long totalTime) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDeleteBtn.setEnabled(count > 0);
                mConcatBtn.setEnabled(totalTime >= (RecordSettings.DEFAULT_MIN_RECORD_DURATION));
            }
        });
    }

    private void refreshSeekBar() {
        final int max = mShortVideoRecorder.getMaxExposureCompensation();
        final int min = mShortVideoRecorder.getMinExposureCompensation();
        boolean brightnessAdjustAvailable = (max != 0 || min != 0);
        Log.e(TAG, "max/min exposure compensation: " + max + "/" + min + " brightness adjust available: " + brightnessAdjustAvailable);
        findViewById(R.id.brightness_panel).setVisibility(brightnessAdjustAvailable ? View.VISIBLE : View.GONE);
        mAdjustBrightnessSeekBar.setOnSeekBarChangeListener(!brightnessAdjustAvailable ? null : new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (i <= Math.abs(min)) {
                    mShortVideoRecorder.setExposureCompensation(i + min);
                } else {
                    mShortVideoRecorder.setExposureCompensation(i - max);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mAdjustBrightnessSeekBar.setMax(max + Math.abs(min));
        mAdjustBrightnessSeekBar.setProgress(Math.abs(min));
    }

    private void updateRecordingBtns(boolean isRecording) {
        mSwitchCameraBtn.setEnabled(!isRecording);
        mRecordBtn.setActivated(isRecording);
    }

    @OnClick(R.id.screen_rotate_button)
    public void onScreenRotation(View v) {
        if (mDeleteBtn.isEnabled()) {
            ToastUtils.showShort("已经开始拍摄，无法旋转屏幕。");
        } else {
            setRequestedOrientation(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ?
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @OnClick(R.id.concat)
    public void onClickConcat(View v) {
        mProcessingDialog.show();
        showChooseDialog();
    }

    private void showChooseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.if_edit_video));
        builder.setPositiveButton(getString(R.string.dlg_yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mIsEditVideo = true;
                mShortVideoRecorder.concatSections(ShortVideoActivity.this);
            }
        });
        builder.setNegativeButton(getString(R.string.dlg_no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mIsEditVideo = false;
                mShortVideoRecorder.concatSections(ShortVideoActivity.this);
            }
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    @OnClick({R.id.super_slow_speed_text, R.id.slow_speed_text, R.id.normal_speed_text, R.id.fast_speed_text, R.id.super_fast_speed_text})
    public void onSpeedClicked(View view) {
        if (!mVideoEncodeSetting.IsConstFrameRateEnabled() || !mRecordSetting.IsRecordSpeedVariable()) {
            if (mSectionProgressBar.isRecorded()) {
                ToastUtils.showShort("变帧率模式下，无法在拍摄中途修改拍摄倍数！");
                return;
            }
        }

        if (mSpeedTextView != null) {
            mSpeedTextView.setTextColor(getResources().getColor(R.color.speedTextNormal));
        }

        TextView textView = (TextView) view;
        textView.setTextColor(getResources().getColor(R.color.colorAccent));
        mSpeedTextView = textView;

        switch (view.getId()) {
            case R.id.super_slow_speed_text:
                mRecordSpeed = RECORD_SPEED_ARRAY[0];
                break;
            case R.id.slow_speed_text:
                mRecordSpeed = RECORD_SPEED_ARRAY[1];
                break;
            case R.id.normal_speed_text:
                mRecordSpeed = RECORD_SPEED_ARRAY[2];
                break;
            case R.id.fast_speed_text:
                mRecordSpeed = RECORD_SPEED_ARRAY[3];
                break;
            case R.id.super_fast_speed_text:
                mRecordSpeed = RECORD_SPEED_ARRAY[4];
                break;
        }

        mShortVideoRecorder.setRecordSpeed(mRecordSpeed);
        if (mRecordSetting.IsRecordSpeedVariable() && mVideoEncodeSetting.IsConstFrameRateEnabled()) {
            mSectionProgressBar.setProceedingSpeed(mRecordSpeed);
            mRecordSetting.setMaxRecordDuration(RecordSettings.DEFAULT_MAX_RECORD_DURATION);
            mSectionProgressBar.setFirstPointTime(RecordSettings.DEFAULT_MIN_RECORD_DURATION);
        } else {
            mRecordSetting.setMaxRecordDuration((long) (RecordSettings.DEFAULT_MAX_RECORD_DURATION * mRecordSpeed));
            mSectionProgressBar.setFirstPointTime((long) (RecordSettings.DEFAULT_MIN_RECORD_DURATION * mRecordSpeed));
        }

        mSectionProgressBar.setTotalTime(this, mRecordSetting.getMaxRecordDuration());
    }


    @OnClick(R.id.adjust_brightness_button)
    public void onClickBrightness(View v) {
        boolean isVisible = mAdjustBrightnessSeekBar.getVisibility() == View.VISIBLE;
        mAdjustBrightnessSeekBar.setVisibility(isVisible ? View.GONE : View.VISIBLE);
    }

    @OnClick(R.id.switch_camera)
    public void onClickSwitchCamera(View v) {
        mShortVideoRecorder.switchCamera();
        mFocusIndicator.focusCancel();
    }

    @OnClick(R.id.switch_flash)
    public void onClickSwitchFlash(View v) {
        mFlashEnabled = !mFlashEnabled;
        mShortVideoRecorder.setFlashEnabled(mFlashEnabled);
        mSwitchFlashBtn.setActivated(mFlashEnabled);
    }

    @OnClick(R.id.audio_mix_button)
    public void onClickAddMixAudio(View v) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT < 19) {
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
        } else {
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
        }
        startActivityForResult(Intent.createChooser(intent, "请选择混音文件："), 0);
    }

    @OnClick(R.id.save_to_draft_button)
    public void onClickSaveToDraft(View v) {
        final EditText editText = new EditText(this);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this)
                .setView(editText)
                .setTitle(getString(R.string.dlg_save_draft_title))
                .setPositiveButton(getString(R.string.dlg_save_draft_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ToastUtils.showShort(mShortVideoRecorder.saveToDraftBox(editText.getText().toString()) ?
                                getString(R.string.toast_draft_save_success) : getString(R.string.toast_draft_save_fail));
                    }
                });
        alertDialog.show();
    }

    private void updateRecordingPercentageView(long currentDuration) {
        final int per = (int) (100 * currentDuration / mRecordSetting.getMaxRecordDuration());
        final long curTime = System.currentTimeMillis();
        if ((mLastRecordingPercentageViewUpdateTime != 0) && (curTime - mLastRecordingPercentageViewUpdateTime < 100)) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecordingPercentageView.setText((per > 100 ? 100 : per) + "%");
                mLastRecordingPercentageViewUpdateTime = curTime;
            }
        });
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            String selectedFilepath = GetPathFromUri.getPath(this, data.getData());
            Log.i(TAG, "Select file: " + selectedFilepath);
            if (selectedFilepath != null && !"".equals(selectedFilepath)) {
                mShortVideoRecorder.setMusicFile(selectedFilepath);
            }
        }
    }

    @Override
    public void onReady() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSwitchFlashBtn.setVisibility(mShortVideoRecorder.isFlashSupport() ? View.VISIBLE : View.GONE);
                mFlashEnabled = false;
                mSwitchFlashBtn.setActivated(mFlashEnabled);
                mRecordBtn.setEnabled(true);
                refreshSeekBar();
                ToastUtils.showShort("可以开始拍摄咯");
            }
        });
    }

    @Override
    public void onError(final int code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtils.showShort(code);
            }
        });
    }

    @Override
    public void onDurationTooShort() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtils.showShort("该视频段太短了");
            }
        });
    }

    @Override
    public void onRecordStarted() {
        Log.i(TAG, "record start time: " + System.currentTimeMillis());
    }

    @Override
    public void onSectionRecording(long sectionDurationMs, long videoDurationMs, int sectionCount) {
        Log.d(TAG, "sectionDurationMs: " + sectionDurationMs + "; videoDurationMs: " + videoDurationMs + "; sectionCount: " + sectionCount);
        updateRecordingPercentageView(videoDurationMs);
    }

    @Override
    public void onRecordStopped() {
        Log.i(TAG, "record stop time: " + System.currentTimeMillis());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateRecordingBtns(false);
            }
        });
    }

    @Override
    public void onSectionIncreased(long incDuration, long totalDuration, int sectionCount) {
        double videoSectionDuration = mDurationVideoStack.isEmpty() ? 0 : mDurationVideoStack.peek().doubleValue();
        if ((videoSectionDuration + incDuration / mRecordSpeed) >= mRecordSetting.getMaxRecordDuration()) {
            videoSectionDuration = mRecordSetting.getMaxRecordDuration();
        }
        Log.d(TAG, "videoSectionDuration: " + videoSectionDuration + "; incDuration: " + incDuration);
        onSectionCountChanged(sectionCount, (long) videoSectionDuration);
    }

    @Override
    public void onSectionDecreased(long decDuration, long totalDuration, int sectionCount) {
        mSectionProgressBar.removeLastBreakPoint();
        if (!mDurationVideoStack.isEmpty()) {
            mDurationVideoStack.pop();
        }
        if (!mDurationRecordStack.isEmpty()) {
            mDurationRecordStack.pop();
        }
        double currentDuration = mDurationVideoStack.isEmpty() ? 0 : mDurationVideoStack.peek().doubleValue();
        onSectionCountChanged(sectionCount, (long) currentDuration);
        updateRecordingPercentageView((long) currentDuration);
    }

    @Override
    public void onRecordCompleted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtils.showShort("已达到拍摄总时长");
            }
        });
    }


    @Override
    public void onManualFocusStart(boolean result) {
        if (result) {
            Log.i(TAG, "manual focus begin success");
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mFocusIndicator.getLayoutParams();
            lp.leftMargin = mFocusIndicatorX;
            lp.topMargin = mFocusIndicatorY;
            mFocusIndicator.setLayoutParams(lp);
            mFocusIndicator.focus();
        } else {
            mFocusIndicator.focusCancel();
            Log.i(TAG, "manual focus not supported");
        }
    }

    @Override
    public void onManualFocusStop(boolean result) {
        Log.i(TAG, "manual focus end result: " + result);
        if (result) {
            mFocusIndicator.focusSuccess();
        } else {
            mFocusIndicator.focusFail();
        }
    }

    @Override
    public void onManualFocusCancel() {
        Log.i(TAG, "manual focus canceled");
        mFocusIndicator.focusCancel();
    }

    @Override
    public void onAutoFocusStart() {
        Log.i(TAG, "auto focus start");
    }

    @Override
    public void onAutoFocusStop() {
        Log.i(TAG, "auto focus stop");
    }

    @Override
    public void onSaveVideoSuccess(final String filePath) {
        Log.i(TAG, "concat sections success filePath: " + filePath);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProcessingDialog.dismiss();
                int screenOrientation = (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE == getRequestedOrientation()) ? 0 : 1;
                if (mIsEditVideo) {
                    ActivityUtil.startVideoEditActivity(ShortVideoActivity.this, filePath, screenOrientation);
                } else {
                    ActivityUtil.startPlaybackActivity(ShortVideoActivity.this, filePath, screenOrientation);
                }
            }
        });
    }

    @Override
    public void onSaveVideoFailed(final int errorCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProcessingDialog.dismiss();
                ToastUtils.showShort("拼接视频段失败: " + errorCode);
            }
        });
    }

    @Override
    public void onSaveVideoCanceled() {
        mProcessingDialog.dismiss();
    }

    @Override
    public void onProgressUpdate(final float percentage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProcessingDialog.setProgress((int) (100 * percentage));
            }
        });
    }
}
