package com.github.axet.filemanager.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.view.WindowCallbackWrapper;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.AppCompatThemeActivity;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.fragments.FilesFragment;
import com.github.axet.filemanager.fragments.HexDialogFragment;
import com.github.axet.filemanager.fragments.MediaFragment;

import java.util.ArrayList;
import java.util.Collections;

public class FullscreenActivity extends AppCompatThemeActivity {
    private static final boolean AUTO_HIDE = true;

    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            hideSystemUI();
        }
    };
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };
    private boolean fullscreen;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            setFullscreen(false);
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    public Window w;
    public View decorView;
    public Toolbar toolbar;
    public Storage storage;
    Storage.Nodes nodes;
    ViewPager pager;
    PagerAdapter adapter;
    Handler handler = new Handler();
    Runnable update = new Runnable() {
        @Override
        public void run() {
            Animation anim = new AlphaAnimation(1, 0);
            anim.setDuration(1000);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    panel.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            panel.startAnimation(anim);
        }
    };

    TextView title;
    TextView count;
    View left;
    View right;
    View panel;

    public static void start(Context context, Uri uri) {
        Intent intent = new Intent(context, FullscreenActivity.class);
        intent.putExtra("uri", uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public class PagerAdapter extends FragmentStatePagerAdapter {
        int index;

        public PagerAdapter(FragmentManager fm, Uri uri) {
            super(fm);
            index = nodes.find(uri);
            update();
        }

        void update() {
            notifyDataSetChanged();
            if (getCount() == 3)
                pager.setCurrentItem(1, false);
        }

        int getIndex(int i) {
            int k;
            if (getCount() == 3) {
                k = index + i - 1;
            } else if (getCount() == 2) {
                if (index == 0)
                    k = index + i;
                else
                    k = index + i - 1;
            } else {
                k = index;
            }
            return k;
        }

        @Override
        public Fragment getItem(int i) {
            int k = getIndex(i);
            return MediaFragment.newInstance(nodes.get(k).uri, false);
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "EMPTY";
        }

        @Override
        public int getCount() {
            int count = 1;
            if (index > 0)
                count++;
            int last = nodes.size() - 1;
            int k = index + 1;
            if (k <= last)
                count++;
            return count;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);
        // toolbar = (Toolbar) findViewById(R.id.toolbar);
        // setSupportActionBar(toolbar);

        w = getWindow();
        final Window.Callback callback = w.getCallback();
        w.setCallback(new WindowCallbackWrapper(callback) {
            @SuppressLint("RestrictedApi")
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                super.onWindowFocusChanged(hasFocus);
                if (hasFocus)
                    setFullscreen(fullscreen);
            }
        });
        decorView = w.getDecorView();

        title = (TextView) findViewById(R.id.title);
        left = findViewById(R.id.left);
        right = findViewById(R.id.right);
        count = (TextView) findViewById(R.id.count);
        panel = findViewById(R.id.toolbar_files);
        View fullscreen = findViewById(R.id.fullscreen);
        fullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Uri uri = getIntent().getParcelableExtra("uri");

        storage = new Storage(this);
        final Uri p = Storage.getParent(this, uri);
        ArrayList<Storage.Node> nn = storage.list(p);
        nodes = new Storage.Nodes(nn);
        Collections.sort(nodes, new FilesFragment.SortByName());

        pager = (ViewPager) findViewById(R.id.pager);
        adapter = new PagerAdapter(getSupportFragmentManager(), uri);
        pager.setAdapter(adapter);

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    if (adapter.getCount() == 3)
                        adapter.index += pager.getCurrentItem() - 1;
                    else if (adapter.getCount() == 2) {
                        if (adapter.index == 0)
                            adapter.index += pager.getCurrentItem();
                        else
                            adapter.index += pager.getCurrentItem() - 1;
                    }
                    adapter.update();
                    update();
                }
            }
        });
        adapter.update();
        update();

        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pager.setCurrentItem(pager.getCurrentItem() - 1);
            }
        });
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pager.setCurrentItem(pager.getCurrentItem() + 1);
            }
        });

        setFullscreen(true);
    }

    void update() {
        Uri uri = nodes.get(adapter.getIndex(pager.getCurrentItem())).uri;
        title.setText(storage.getName(uri));
        count.setText((nodes.find(uri) + 1) + "/" + nodes.size());
        Animation a = panel.getAnimation();
        if (a != null)
            a.setAnimationListener(null);
        panel.clearAnimation();
        panel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(update);
        handler.postDelayed(update, 2000);
        sendBroadcast(new Intent(HexDialogFragment.CHANGED).putExtra("uri", uri));
    }

    @Override
    public int getAppTheme() {
        return FilesApplication.getTheme(this, FilesApplication.PREF_THEME, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar, getString(R.string.Theme_Dark));
    }

    @Override
    public int getAppThemePopup() {
        return FilesApplication.getTheme(this, FilesApplication.PREF_THEME, R.style.AppThemeLight_PopupOverlay, R.style.AppThemeDark_PopupOverlay, getString(R.string.Theme_Dark));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    public void toggle() {
        setFullscreen(!fullscreen);
    }

    @SuppressLint({"InlinedApi", "RestrictedApi"})
    public void setFullscreen(boolean b) {
        if (fullscreen == b)
            return;
        fullscreen = b;
        if (b) {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            // Hide UI first
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }

            // Schedule a runnable to remove the status and navigation bar after a delay
            mHideHandler.removeCallbacks(mShowPart2Runnable);
            mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            showSystemUI();
            // Schedule a runnable to display UI elements after a delay
            mHideHandler.removeCallbacks(mHidePart2Runnable);
            mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
        }
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    public void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    // This snippet hides the system bars.
    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        if (Build.VERSION.SDK_INT >= 11)
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    // This snippet shows the system bars. It does this by removing all the flags
// except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= 11)
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setFullscreen(fullscreen); // refresh
    }
}
