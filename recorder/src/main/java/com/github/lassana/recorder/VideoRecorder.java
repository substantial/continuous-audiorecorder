package com.github.lassana.recorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.view.Surface;

import java.io.File;
import java.io.IOException;

/**
 * @author Nikolai Doronin {@literal <lassana.nd@gmail.com>}
 * @since 8/18/13
 */
public class VideoRecorder {

    public static enum Status {
        STATUS_UNKNOWN,
        STATUS_READY_TO_RECORD,
        STATUS_RECORDING,
        STATUS_RECORD_PAUSED
    }

    public static interface OnException {
        public void onException(Exception e);
    }

    public static interface OnStartListener extends OnException {
        public void onStarted();
    }

    public static interface OnPauseListener extends OnException {
        public void onPaused(String activeRecordFileName);
    }

    /**
     * @author lassana
     * @since 10/06/2013
     */
    public static class MediaRecorderConfig {
        private final int mVideoEncodingBitRate;
        private final int mVideoSource;
        private final int mVideoEncoder;

        public static final MediaRecorderConfig DEFAULT =
                new MediaRecorderConfig(64 * 1024,              /* 64 Kib per second */
                        MediaRecorder.VideoSource.DEFAULT,      /* Default video source */
                        MediaRecorder.VideoEncoder.DEFAULT);       /* Default encoder       */

        /**
         * Constructor.
         *
         * @param videoEncodingBitRate
         * Used for {@link android.media.MediaRecorder#setVideoEncodingBitRate}
         * @param videoSource
         * Used for {@link android.media.MediaRecorder#setVideoSource}
         * @param videoEncoder
         * Used for {@link android.media.MediaRecorder#setVideoEncoder}
         */
        public MediaRecorderConfig(int videoEncodingBitRate, int videoSource, int videoEncoder) {
            mVideoEncodingBitRate = videoEncodingBitRate;
            mVideoSource = videoSource;
            mVideoEncoder = videoEncoder;
        }

    }

    class StartRecordTask extends AsyncTask<OnStartListener, Void, Exception> {

        private OnStartListener mOnStartListener;

        @Override
        protected Exception doInBackground(OnStartListener... params) {
            this.mOnStartListener = params[0];
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setVideoEncodingBitRate(mMediaRecorderConfig.mVideoEncodingBitRate);
            mMediaRecorder.setVideoSize(320, 240);
            mMediaRecorder.setVideoSource(mMediaRecorderConfig.mVideoSource);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            mMediaRecorder.setOutputFile(getTemporaryFileName());
            mMediaRecorder.setVideoEncoder(mMediaRecorderConfig.mVideoEncoder);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

            Exception exception = null;
            try {
                mMediaRecorder.prepare();
                mMediaRecorder.start();
            } catch (IOException e) {
                exception = e;
            }
            return exception;
        }

        @Override
        protected void onPostExecute(Exception e) {
            super.onPostExecute(e);
            if (e == null) {
                setStatus(VideoRecorder.Status.STATUS_RECORDING);
                mOnStartListener.onStarted();
            } else {
                setStatus(VideoRecorder.Status.STATUS_READY_TO_RECORD);
                mOnStartListener.onException(e);
            }
        }
    }

    class PauseRecordTask extends AsyncTask<OnPauseListener, Void, Exception> {
        private OnPauseListener mOnPauseListener;

        @Override
        protected Exception doInBackground(OnPauseListener... params) {
            mOnPauseListener = params[0];
            Exception exception = null;
            try {
                mMediaRecorder.stop();
                mMediaRecorder.release();
            } catch (Exception e) {
                exception = e;
            }
            if ( exception == null ) {
                appendToFile(mTargetRecordFileName, getTemporaryFileName());
            }
            return exception;
        }

        @Override
        protected void onPostExecute(Exception e) {
            super.onPostExecute(e);
            if (e == null) {
                setStatus(VideoRecorder.Status.STATUS_RECORD_PAUSED);
                mOnPauseListener.onPaused(mTargetRecordFileName);
            } else {
                setStatus(VideoRecorder.Status.STATUS_READY_TO_RECORD);
                mOnPauseListener.onException(e);
            }
        }
    }

    private Status mStatus;
    private MediaRecorder mMediaRecorder;
    private final String mTargetRecordFileName;
    private final Context mContext;
    private final MediaRecorderConfig mMediaRecorderConfig;

    private VideoRecorder(final Context context,
                          final String targetRecordFileName,
                          final MediaRecorderConfig mediaRecorderConfig) {
        mTargetRecordFileName = targetRecordFileName;
        mContext = context;
        mMediaRecorderConfig = mediaRecorderConfig;
        mStatus = Status.STATUS_UNKNOWN;
    }

    /**
     * Returns the ready-to-use VideoRecorder.
     * Uses {@link com.github.lassana.recorder.VideoRecorder.MediaRecorderConfig#DEFAULT} as
     * {@link android.media.MediaRecorder} config.
     */
    public static VideoRecorder build(final Context context,
                                      final String targetFileName) {
        return build(context, targetFileName, MediaRecorderConfig.DEFAULT);
    }

    /**
     * Returns the ready-to-use VideoRecorder.
     */
    public static VideoRecorder build(final Context context,
                                      final String targetFileName,
                                      final MediaRecorderConfig mediaRecorderConfig) {
        VideoRecorder rvalue = new VideoRecorder(context, targetFileName, mediaRecorderConfig);
        rvalue.mStatus = Status.STATUS_READY_TO_RECORD;
        return rvalue;
    }

    /**
     * Continues existing record or starts new one.
     */
    @SuppressLint("NewApi")
    public void start(final OnStartListener listener) {
        StartRecordTask task = new StartRecordTask();
        task.execute(listener);

    }

    /**
     * Pauses active recording.
     */
    @SuppressLint("NewApi")
    public void pause(final OnPauseListener listener) {
        PauseRecordTask task = new PauseRecordTask();
        task.execute(listener);

    }

    public Status getStatus() {
        return mStatus;
    }

    public String getRecordFileName() {
        return mTargetRecordFileName;
    }

    public boolean isRecording() {
        return mStatus == Status.STATUS_RECORDING;
    }

    public boolean isReady() {
        return mStatus == Status.STATUS_READY_TO_RECORD;
    }

    public boolean isPaused() {
        return mStatus == Status.STATUS_RECORD_PAUSED;
    }

    private void setStatus(final Status status) {
        mStatus = status;
    }

    private String getTemporaryFileName() {
        return mContext.getCacheDir().getAbsolutePath() + File.separator + "tmprecord";
    }

    private void appendToFile(final String targetFileName, final String newFileName) {
        Mp4ParserWrapper.append(targetFileName, newFileName);
    }
}
