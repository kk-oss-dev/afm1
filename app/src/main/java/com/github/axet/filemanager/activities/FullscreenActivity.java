package com.github.axet.filemanager.activities;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import com.github.axet.androidlibrary.activities.AppCompatFullscreenThemeActivity;
import com.github.axet.androidlibrary.widgets.PinchGesture;
import com.github.axet.androidlibrary.widgets.PinchView;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.fragments.FilesFragment;
import com.github.axet.filemanager.fragments.HexDialogFragment;
import com.github.axet.filemanager.fragments.MediaFragment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class FullscreenActivity extends AppCompatFullscreenThemeActivity {
    Storage.Nodes nodes = new Storage.Nodes();
    ViewPager pager;
    PagerAdapter adapter;
    TextView title;
    TextView count;
    View left;
    View right;
    View panel;
    PinchGesture gesture;

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

    public static void start(Context context, Uri uri) {
        Intent intent = new Intent(context, FullscreenActivity.class);
        intent.putExtra("uri", uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public static ArrayList<Storage.Node> asNodeList(Context context, Uri uri) {
        Storage.Node node;
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_CONTENT))
            node = new Storage.Node(Storage.getDocumentFile(context, uri));
        else if (s.equals(ContentResolver.SCHEME_FILE))
            node = new Storage.Node(Storage.getFile(uri));
        else
            throw new Storage.UnknownUri();
        return new ArrayList(Arrays.asList(node));
    }

    public class PagerAdapter extends FragmentPagerAdapter {
        int index; // nodes index
        FragmentManager fm;

        public PagerAdapter(FragmentManager fm, Uri uri) {
            super(fm);
            this.fm = fm;
            index = nodes.find(uri);
            update();
        }

        void idle() {
            if (getCount() == 3)
                index += pager.getCurrentItem() - 1;
            else if (getCount() == 2) {
                if (index == 0)
                    index += pager.getCurrentItem();
                else
                    index += pager.getCurrentItem() - 1;
            }
        }

        void update() {
            notifyDataSetChanged();
            if (getCount() == 3)
                pager.setCurrentItem(1, false);
            else if (getCount() == 2)
                pager.setCurrentItem(index, false);
        }

        int getIndex(int i) { // pager -> nodes index
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
        public MediaFragment getItem(int i) {
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

        MediaFragment getFragment(int i) {
            MediaFragment f = (MediaFragment) instantiateItem(pager, i);
            finishUpdate(pager);
            return f;
        }

        @SuppressLint("RestrictedApi")
        public MediaFragment getCurrentFragment() {
            int i = pager.getCurrentItem();
            return getFragment(i);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        final ViewGroup v = (ViewGroup) findViewById(R.id.content);

        gesture = new PinchGesture(this) {
            Bitmap tmp;

            @Override
            public void onScaleBegin(float x, float y) {
                super.onScaleBegin(x, y);
                MediaFragment current = adapter.getCurrentFragment();
                Rect rect = PinchView.getImageBounds(current.image);
                InputStream is = null;
                Bitmap bm = null;
                try {
                    bm = tmp = BitmapFactory.decodeStream(is = current.storage.open(current.uri));
                } catch (Exception e) { // catch outofmemory exception
                    Log.w(TAG, e);
                } finally {
                    try {
                        if (is != null)
                            is.close();
                    } catch (Exception e) {
                        Log.d(TAG, "unable to close", e);
                    }
                }
                if (bm == null)
                    bm = current.bm; // out of memory case
                if (current.rotation.get() != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(current.rotation.get());
                    Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
                    bm.recycle();
                    bm = tmp = rotatedBitmap;
                }
                pinchOpen(rect, bm);
                v.addView(pinch, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void pinchClose() { // do not call super, keep 'bm'
                if (pinch != null) {
                    v.removeView(pinch);
                    pinch = null;
                }
                if (tmp != null) {
                    tmp.recycle();
                    tmp = null;
                }
            }
        };

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
        try {
            Uri p = Storage.getParent(this, uri);
            nodes = new Storage.Nodes(p == null ? asNodeList(this, uri) : storage.list(p), false);
            Collections.sort(nodes, FilesFragment.sort(this));
        } catch (RuntimeException e) {
            Log.e(TAG, "unable to read", e);
            nodes = new Storage.Nodes(asNodeList(this, uri), false);
        } finally {
            storage.closeSu();
        }

        pager = (ViewPager) findViewById(R.id.pager);
        adapter = new PagerAdapter(getSupportFragmentManager(), uri);
        pager.setAdapter(adapter);

        final GestureDetectorCompat tap = new GestureDetectorCompat(FullscreenActivity.this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                updateToolbar();
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

        pager.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                tap.onTouchEvent(e);
                MediaFragment current = adapter.getCurrentFragment();
                if (current != null && current.bm != null)
                    if (gesture.onTouchEvent(e))
                        return true;
                return false;
            }
        });

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
                    adapter.idle();
                    adapter.update();
                }
            }
        });
        adapter.update();
        updateToolbar();

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

        handler.post(new Runnable() {
            @Override
            public void run() {
                adapter.idle(); // update 'current'
            }
        });
    }

    void updateToolbar() {
        Uri uri = nodes.get(adapter.getIndex(pager.getCurrentItem())).uri; // TODO onCreate array out of index
        title.setText(Storage.getName(this, uri));
        count.setText((nodes.find(uri) + 1) + "/" + nodes.size());
        Animation a = panel.getAnimation();
        if (a != null)
            a.setAnimationListener(null);
        panel.clearAnimation();
        panel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(update);
        handler.postDelayed(update, 2000);
        left.setVisibility(nodes.size() > 1 ? View.VISIBLE : View.GONE);
        right.setVisibility(nodes.size() > 1 ? View.VISIBLE : View.GONE);
        count.setVisibility(nodes.size() > 1 ? View.VISIBLE : View.GONE);
        sendBroadcast(new Intent(HexDialogFragment.CHANGED).putExtra("uri", uri));
    }

    @Override
    public int getAppTheme() {
        return FilesApplication.getTheme(this, FilesApplication.PREF_THEME, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    public int getAppThemePopup() {
        return FilesApplication.getTheme(this, FilesApplication.PREF_THEME, R.style.AppThemeLight_PopupOverlay, R.style.AppThemeDark_PopupOverlay);
    }

    @Override
    public void onBackPressed() {
        if (gesture.isPinch()) {
            gesture.pinchClose();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
