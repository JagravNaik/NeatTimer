package gh.out386.timer;

import android.animation.Animator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;

import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.hanks.htextview.base.HTextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements ColorChooserDialog.ColorCallback {
    private final int INTERVAL = 205;
    private final int BLINK_INTERVAL = 1000;

    private HTextView timerTv;
    private HTextView clockTv;
    private ScheduledFuture<?> timeHandle;
    private long initialTime;
    private long diff;
    private byte lastSetClockSeconds = 0;
    private int colourPrimary;
    private int colourAccent;
    private SharedPreferences sp;
    private SimpleDateFormat sdf;
    private SimpleDateFormat timeSdf;
    private Date timerDate;
    private Date clockDate;
    private int timerLength = 0;
    private boolean isPaused = true;
    private String formattedDiff;
    private String lastTimerTime;
    private ScheduledExecutorService scheduledExecutorService;
    private GestureDetector timerGesture;
    private GestureDetector containerGesture;
    private SetTextsRunnable setTextsRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        sp = getPreferences(Context.MODE_PRIVATE);
        scheduledExecutorService = Executors.newScheduledThreadPool(1);

        sdf = new SimpleDateFormat("ss:SS");
        timeSdf = new SimpleDateFormat("hh:mm:ss a");
        timerDate = new Date();
        clockDate = new Date();
        timerTv = findViewById(R.id.tv);
        clockTv = findViewById(R.id.time);
        RelativeLayout container = findViewById(R.id.rl);

        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        timerTv.animateText(getResources().getString(R.string.timer_initial_text));

        timerGesture = new GestureDetector(this, new TimerViewListener());
        containerGesture = new GestureDetector(this, new ContainerListener());
        setTextsRunnable = new SetTextsRunnable();

        if (savedInstanceState == null) {
            colourPrimary = sp.getInt(Constants.COLOUR_PRIMARY, 0xffffff);
            colourAccent = sp.getInt(Constants.COLOUR_ACCENT, 0x000000);
            diff = 0;
            formattedDiff = "00:00";
            timeHandle = scheduledExecutorService
                    .scheduleAtFixedRate(new BlinkTimerRunnable(), 0, BLINK_INTERVAL, TimeUnit.MILLISECONDS);
        } else {
            initialTime = savedInstanceState.getLong(Constants.INITIAL_TIME);
            colourPrimary = savedInstanceState.getInt(Constants.COLOUR_PRIMARY);
            colourAccent = savedInstanceState.getInt(Constants.COLOUR_ACCENT);
            isPaused = savedInstanceState.getBoolean(Constants.IS_PAUSED);
            diff = savedInstanceState.getLong(Constants.DIFF);
            if (isPaused) {
                formattedDiff = savedInstanceState.getString(Constants.FORMATTED_DIFF);
                timerTv.animateText(formattedDiff);
                timeHandle = scheduledExecutorService
                        .scheduleAtFixedRate(new BlinkTimerRunnable(), 0, BLINK_INTERVAL, TimeUnit.MILLISECONDS);
            } else
                timeHandle = scheduledExecutorService
                        .scheduleAtFixedRate(new StopTimerRunnable(), 0, INTERVAL, TimeUnit.MILLISECONDS);
        }

        container.setBackgroundColor(colourPrimary);
        timerTv.setTextColor(colourAccent);
        timerTv.setTypeface(FontManager.getInstance(getAssets()).getFont("fonts/NotCourierSans-webfont.ttf"));

        clockTv.setTextColor(colourAccent);
        clockTv.setTypeface(FontManager.getInstance(getAssets()).getFont("fonts/NotCourierSans-webfont.ttf"));

        container.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return containerGesture.onTouchEvent(event);
            }
        });
        timerTv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return timerGesture.onTouchEvent(event);
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            goImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        goImmersive();
    }

    private void goImmersive() {
        getWindow().getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(Constants.FORMATTED_DIFF, formattedDiff);
        outState.putLong(Constants.INITIAL_TIME, initialTime);
        outState.putLong(Constants.DIFF, diff);
        outState.putInt(Constants.COLOUR_PRIMARY, colourPrimary);
        outState.putInt(Constants.COLOUR_ACCENT, colourAccent);
        outState.putBoolean(Constants.IS_PAUSED, isPaused);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        if (timeHandle != null && !timeHandle.isCancelled())
            timeHandle.cancel(true);
        super.onDestroy();
    }

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog dialog, @ColorInt int colour) {
        if (R.string.primary == dialog.getTitle()) {
            colourPrimary = colour;
            findViewById(R.id.rl).setBackgroundColor(colourPrimary);
            sp.edit().putInt(Constants.COLOUR_PRIMARY, colourPrimary).apply();
        } else if (R.string.accent == dialog.getTitle()) {
            colourAccent = colour;
            timerTv.setTextColor(colourAccent);
            clockTv.setTextColor(colourAccent);

            // Needed because the colours will not be changed until the HTextViews update
            timerTv.animateText(timerTv.getText());
            clockTv.animateText(clockTv.getText());
            sp.edit().putInt(Constants.COLOUR_ACCENT, colourAccent).apply();
        }
    }

    @Override
    public void onColorChooserDismissed(@NonNull ColorChooserDialog dialog) {

        if (dialog.getTitle() == R.string.primary) {
            new ColorChooserDialog.Builder(MainActivity.this, R.string.accent)
                    .accentMode(false)
                    .show(getSupportFragmentManager());

        }
    }

    private void onTimerPaused() {
        if (isPaused) {
            if (timeHandle == null) {
                initialTime = SystemClock.elapsedRealtime();
                timeHandle = scheduledExecutorService
                        .scheduleAtFixedRate(new StopTimerRunnable(), 0, INTERVAL, TimeUnit.MILLISECONDS);
                isPaused = false;
                return;
            } else if (!timeHandle.isCancelled())
                timeHandle.cancel(true);
            initialTime = SystemClock.elapsedRealtime() - diff;
            timeHandle = scheduledExecutorService
                    .scheduleAtFixedRate(new StopTimerRunnable(), 0, INTERVAL, TimeUnit.MILLISECONDS);
            isPaused = false;
        } else {
            if (timeHandle != null && !timeHandle.isCancelled())
                timeHandle.cancel(true);
            timeHandle = scheduledExecutorService
                    .scheduleAtFixedRate(new BlinkTimerRunnable(), 0, BLINK_INTERVAL, TimeUnit.MILLISECONDS);
            isPaused = true;
        }
    }

    private class StopTimerRunnable implements Runnable {
        @Override
        public void run() {
            diff = SystemClock.elapsedRealtime() - initialTime;
            if (timerLength == 0 && diff > 50000) {
                sdf.applyPattern("mm:ss:SS");
                timerLength = 1;
            } else if (timerLength == 1 && diff > 2750000) {
                sdf.applyPattern("HH:mm:ss");
                timerLength = 2;
            }

            timerDate.setTime(diff);
            clockDate.setTime(System.currentTimeMillis());

            MainActivity.this.runOnUiThread(setTextsRunnable);
        }
    }

    private class SetTextsRunnable implements Runnable {
        @Override
        public void run() {
            formattedDiff = sdf.format(timerDate.getTime());
            if (!formattedDiff.equals(lastTimerTime)) {
                timerTv.animateText(formattedDiff);
                lastTimerTime = formattedDiff;
            }

            // Update only when seconds change. This Runnable should be called multiple times a second.
            long currentClockTime = clockDate.getTime();
            byte currentClockSeconds = (byte) ((currentClockTime / 1000) % 10);
            if (currentClockSeconds != lastSetClockSeconds) {
                String timeStr = timeSdf.format(clockDate);
                clockTv.animateText(timeStr);
                lastSetClockSeconds = currentClockSeconds;
            }
        }
    }

    private class BlinkTimerRunnable implements Runnable {
        @Override
        public void run() {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    clockTv.animateText(timeSdf.format(new Date(Calendar.getInstance().getTimeInMillis())));
                    timerTv.animate()
                            .alpha(0.0f)
                            .setDuration(700L)
                            .setInterpolator(new DecelerateInterpolator())
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animation) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    timerTv.animate()
                                            .alpha(1.0f)
                                            .setDuration(300L)
                                            .setInterpolator(new AccelerateInterpolator());
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animation) {

                                }
                            });
                }
            });
        }
    }

    private class TimerViewListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            onTimerPaused();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            diff = 0L;
            formattedDiff = "00:00";
            timerLength = 0;
            sdf.applyPattern("ss:SS");
            isPaused = true;
            timerTv.animateText(formattedDiff);
            if (timeHandle != null && !timeHandle.isCancelled())
                timeHandle.cancel(true);
            timeHandle = scheduledExecutorService
                    .scheduleAtFixedRate(new BlinkTimerRunnable(), 0, BLINK_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    private class ContainerListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            onTimerPaused();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            new ColorChooserDialog.Builder(MainActivity.this, R.string.primary)
                    .accentMode(true)
                    .show(getSupportFragmentManager());
        }
    }
}