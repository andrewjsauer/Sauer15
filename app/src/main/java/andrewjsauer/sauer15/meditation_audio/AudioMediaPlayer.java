package andrewjsauer.sauer15.meditation_audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import andrewjsauer.sauer15.R;
import andrewjsauer.sauer15.events.EventAudioPaused;

/**
 * Created by andrewsauer on 6/8/16.
 */
public class AudioMediaPlayer {

    private int seekForwardTime = 10000; // in milliseconds so 10 seconds

    private static final String TAG = AudioMediaPlayer.class.getSimpleName();
    private static AudioMediaPlayer sInstance;

    private static final String NAME = "command";
    private static final String PAUSE = "pause";
    private static final String STOP = "pause";
    private static final String PLAY = "play";

    // Jellybean
    private static String SERVICE_CMD = "service";
    private static String PAUSE_SERVICE_CMD = "pause.service";
    private static String PLAY_SERVICE_CMD = "play.service";

    // Honeycomb
    {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            SERVICE_CMD = "service";
            PAUSE_SERVICE_CMD = "pause.service";
            PLAY_SERVICE_CMD = "play.service";
        }
    }

    public static AudioMediaPlayer getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AudioMediaPlayer(context);
        }
        return sInstance;
    }

    private Context mContext;
    private boolean mAudioFocusGranted = false;
    private MediaPlayer mPlayer;
    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener;
    private BroadcastReceiver mIntentReceiver;
    private boolean mReceiverRegistered = false;

    public boolean mAudioIsPlaying = false;

    private AudioMediaPlayer(Context context) {

        mContext = context;

        mOnAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {

            @Override
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.i(TAG, "AUDIOFOCUS_GAIN");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        Log.i(TAG, "AUDIOFOCUS_GAIN_TRANSIENT");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        Log.i(TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                        pauseAudioLoss();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.e(TAG, "AUDIOFOCUS_LOSS");
                        pauseAudioLoss();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.e(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        pauseAudioLoss();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.e(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        pauseAudioLoss();
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        Log.e(TAG, "AUDIOFOCUS_REQUEST_FAILED");
                        pauseAudioLoss();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    public void setAudio() {
        mPlayer = MediaPlayer.create(mContext, R.raw.fifteen_minute_meditation);
    }

    public void play() {

        if (!mAudioIsPlaying) {
            if (mPlayer == null) {
                mPlayer = new MediaPlayer();
            }

            if (!mAudioFocusGranted && requestAudioFocus()) {
                // 2. Kill off any other play back sources
                forceMusicStop();
                // 3. Register broadcast receiver for player intents
                setupBroadcastReceiver();
            }

            mPlayer.start();
            mAudioIsPlaying = true;
        }
    }

    public void pause() {
        if (mAudioFocusGranted && mAudioIsPlaying) {
            mPlayer.pause();
            mAudioIsPlaying = false;
        }
    }

    private void pauseAudioLoss() {
        EventBus.getDefault().post(new EventAudioPaused());
    }

    public void exitAudio() {
        if (mAudioFocusGranted && mAudioIsPlaying) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
            mAudioIsPlaying = false;
            // 2. Give up audio focus
            abandonAudioFocus();
        }

        if (mReceiverRegistered) {
            mContext.unregisterReceiver(mIntentReceiver);
            mReceiverRegistered = false;
        }
    }

    public void setResetRecordingButton() {
        mPlayer.seekTo(0);
    }

    public void setFastForwardButton() {
        int currentPosition = mPlayer.getCurrentPosition();

        if(currentPosition + seekForwardTime <= mPlayer.getDuration()){
            mPlayer.seekTo(currentPosition + seekForwardTime);
        }
        else {
            mPlayer.seekTo(mPlayer.getDuration());
        }
    }

    public double getCurrentAudioTime() {
        return mPlayer.getCurrentPosition();
    }

    private boolean requestAudioFocus() {
        if (!mAudioFocusGranted) {
            AudioManager am = (AudioManager) mContext
                    .getSystemService(Context.AUDIO_SERVICE);
            // Request audio focus for play back
            int result = am.requestAudioFocus(mOnAudioFocusChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocusGranted = true;
            } else {
                Log.e(TAG,
                        ">>>>>>>>>>>>> FAILED TO GET AUDIO FOCUS <<<<<<<<<<<<<<<<<<<<<<<<");
            }
        }
        return mAudioFocusGranted;
    }

    private void abandonAudioFocus() {
        AudioManager am = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);
        int result = am.abandonAudioFocus(mOnAudioFocusChangeListener);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mAudioFocusGranted = false;
        } else {
            // FAILED
            Log.e(TAG,
                    ">>>>>>>>>>>>> FAILED TO ABANDON AUDIO FOCUS <<<<<<<<<<<<<<<<<<<<<<<<");
        }
        mOnAudioFocusChangeListener = null;
    }

    private void setupBroadcastReceiver() {
        mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                String cmd = intent.getStringExtra(NAME);
                Log.i(TAG, "mIntentReceiver.onReceive " + action + " / " + cmd);

                if (PAUSE_SERVICE_CMD.equals(action)
                        || (SERVICE_CMD.equals(action) && PAUSE.equals(cmd))) {
                    play();
                }

                if (PLAY_SERVICE_CMD.equals(action)
                        || (SERVICE_CMD.equals(action) && PLAY.equals(cmd))) {
                    pause();
                }
            }
        };

        // Do the right thing when something else tries to play
        if (!mReceiverRegistered) {
            IntentFilter commandFilter = new IntentFilter();
            commandFilter.addAction(SERVICE_CMD);
            commandFilter.addAction(PAUSE_SERVICE_CMD);
            commandFilter.addAction(PLAY_SERVICE_CMD);
            mContext.registerReceiver(mIntentReceiver, commandFilter);
            mReceiverRegistered = true;
        }
    }

    private void forceMusicStop() {
        AudioManager am = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);
        if (am.isMusicActive()) {
            Intent intentToStop = new Intent(SERVICE_CMD);
            intentToStop.putExtra(NAME, STOP);
            mContext.sendBroadcast(intentToStop);
        }
    }
}

