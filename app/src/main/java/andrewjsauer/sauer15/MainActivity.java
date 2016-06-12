package andrewjsauer.sauer15;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;

import org.greenrobot.eventbus.Subscribe;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import andrewjsauer.sauer15.events.EventAudioPaused;
import andrewjsauer.sauer15.meditation_audio.AudioMediaPlayer;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @Bind(R.id.track_time)
    protected TextView mTrackTime;

    @Bind(R.id.play)
    protected ImageButton mPlayButton;

    @Bind(R.id.pause)
    protected ImageButton mPauseButton;

    static AudioMediaPlayer mMusicPlayer;

    private FirebaseAnalytics mFirebaseAnalytics;

    private boolean mHeadsetConnected = false;
    private Handler mDurationHandler = new Handler();
    private double mTimeElapsed = 0;

    private Runnable updateDuration = new Runnable() {
        public void run() {
            //get current position
            mTimeElapsed = mMusicPlayer.getCurrentAudioTime();

            //set time remaining
            double timeRemaining = mTimeElapsed;

            mTrackTime.setText(String.format(Locale.ENGLISH, "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes((long) timeRemaining),
                    TimeUnit.MILLISECONDS.toSeconds((long) timeRemaining)
                            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long) timeRemaining))));

            //repeat that again in 100 miliseconds
            mDurationHandler.postDelayed(this, 100);
        }
    };


    private final BroadcastReceiver headsetDisconnected = new BroadcastReceiver() {

        private static final String TAG = "headsetDisconnected";

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.hasExtra("state")) {
                if (mHeadsetConnected && intent.getIntExtra("state", 0) == 0) {
                    mHeadsetConnected = false;
                    if (mMusicPlayer.mAudioIsPlaying) {
                        onPauseAudio();
                        Log.d(TAG, "Headset was unplugged during playback.");

                    }
                } else if (!mHeadsetConnected && intent.getIntExtra("state", 0) == 1) {
                    mHeadsetConnected = true;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        FirebaseCrash.log("Activity created");

        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        if (mMusicPlayer == null) {
            mMusicPlayer = AudioMediaPlayer.getInstance(this);
        }

        initializeScreen();


    }

    private void initializeScreen() {

        mMusicPlayer.setAudio();

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "App Open");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, bundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(headsetDisconnected, new IntentFilter(
                Intent.ACTION_HEADSET_PLUG));

        if (mMusicPlayer.mAudioIsPlaying) {
            mDurationHandler.postDelayed(updateDuration, 100);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDurationHandler.removeCallbacks(updateDuration);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        exitAudioDetails();

        FirebaseCrash.log("Activity destroyed");

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "App Close");
        mFirebaseAnalytics.logEvent("App Close", bundle);

    }

    @OnClick (R.id.pause)
    void onPauseAudio() {
        mMusicPlayer.pause();

        mDurationHandler.removeCallbacks(updateDuration);

        mPlayButton.setVisibility(View.VISIBLE);
        mPauseButton.setVisibility(View.INVISIBLE);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "onPauseAudio");
        mFirebaseAnalytics.logEvent("Pause", bundle);
    }

    @OnClick (R.id.play)
    void onPlayAudio() {
        mMusicPlayer.play();

        mDurationHandler.postDelayed(updateDuration, 100);

        mPlayButton.setVisibility(View.INVISIBLE);
        mPauseButton.setVisibility(View.VISIBLE);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "onPlayAudio");
        mFirebaseAnalytics.logEvent("Play", bundle);
    }

    @OnClick (R.id.reset_recording)
    void onResetRecording() {

        mMusicPlayer.setResetRecordingButton();

        if (!mMusicPlayer.mAudioIsPlaying) {
            mTrackTime.setText("00:00");
        }

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "resetRecording");
        mFirebaseAnalytics.logEvent("Reset", bundle);
    }

    @OnClick (R.id.info)
    void onAbout() {

        onPauseAudio();

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "onAbout");
        mFirebaseAnalytics.logEvent("About", bundle);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.about))
                .setMessage(getString(R.string.about_message))
                .setPositiveButton(getString(R.string.got_it), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Integer.parseInt(android.os.Build.VERSION.SDK) > 5
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onBackPressed() {
        exitAudioDetails();
    }

    private void exitAudioDetails() {

        mMusicPlayer.exitAudio();

        mDurationHandler.removeCallbacks(updateDuration);

        unregisterReceiver(headsetDisconnected);
    }

    @Subscribe
    public void onEvent(EventAudioPaused eventAudioPaused) {
        onPauseAudio();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
