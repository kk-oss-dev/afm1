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
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.view.WindowCallbackWrapper;
import android.support.v7.widget.Toolbar;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.AppCompatFullscreenThemeActivity;
import com.github.axet.androidlibrary.widgets.AppCompatThemeActivity;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.fragments.FilesFragment;
import com.github.axet.filemanager.fragments.HexDialogFragment;
import com.github.axet.filemanager.fragments.MediaFragment;

import java.util.ArrayList;
import java.util.Collections;

public class FullscreenActivity extends AppCompatFullscreenThemeActivity {
    public Toolbar toolbar;
    Storage.Nodes nodes;
    ViewPager pager;
    PagerAdapter adapter;
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

    public class PagerAdapter extends FragmentPagerAdapter {
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
            else if (getCount() == 2)
                pager.setCurrentItem(index, false);
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
            Uri uri = nodes.get(k).uri;
            return MediaFragment.newInstance(uri);
        }

        @Override
        public int getItemPosition(Object object) {
            MediaFragment f = (MediaFragment) object;
            for (int i = 0; i < getCount(); i++) {
                int k = getIndex(i);
                if (nodes.get(k).uri.equals(f.getUri()))
                    return i;
            }
            return POSITION_NONE;
        }

        @Override
        public long getItemId(int i) {
            return getIndex(i);
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

        Window.Callback callback = w.getCallback();
        w.setCallback(new WindowCallbackWrapper(callback) {
            GestureDetectorCompat gestures = new GestureDetectorCompat(FullscreenActivity.this, new GestureDetector.OnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return false;
                }

                @Override
                public void onShowPress(MotionEvent e) {
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    update();
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    return false;
                }
            });

            @SuppressLint("RestrictedApi")
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                gestures.onTouchEvent(event);
                return super.dispatchTouchEvent(event);
            }
        });

        title = (TextView) findViewById(R.id.title);
        left = findViewById(R.id.left);
        right = findViewById(R.id.right);
        count = (TextView) findViewById(R.id.count);
        panel = findViewById(R.id.toolbar_files);
        View fullscreen = findViewById(R.id.fullscreen);
        fullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });

        Uri uri = getIntent().getParcelableExtra("uri");

        Storage storage = new Storage(this);
        final Uri p = Storage.getParent(this, uri);
        ArrayList<Storage.Node> nn = storage.list(p);
        nodes = new Storage.Nodes(nn, false);
        Collections.sort(nodes, new FilesFragment.SortByName());
        storage.closeSu();

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
    }

    void update() {
        Uri uri = nodes.get(adapter.getIndex(pager.getCurrentItem())).uri;
        title.setText(Storage.getName(this, uri));
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
    protected void onDestroy() {
        super.onDestroy();
    }
}
