package com.github.axet.filemanager;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.internal.NavigationMenuItemView;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.TextMax;
import com.github.axet.androidlibrary.widgets.ThemeUtils;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final int RESULT_SAF = 1;

    public static final String BOOKMARK = "BOOKMARK";
    public static final String ADD_BOOKMARK = "ADDBOOKMARK";

    public SectionsPagerAdapter mSectionsPagerAdapter;
    public ViewPager mViewPager;
    Storage storage;
    TabLayout tabLayout;
    NavigationView navigationView;
    OpenChoicer choicer;
    FilesApplication app;
    Menu bookmarksMenu;

    public static String getDefault() {
        return Uri.fromFile(Environment.getExternalStorageDirectory()).toString();
    }

    public static class FilesTabView extends TextMax {
        TextView text;
        LinearLayout.LayoutParams lp;

        public FilesTabView(Context context, TabLayout tabLayout) {
            this(context, new TextView(context));
            text.setTextColor(tabLayout.getTabTextColors());
        }

        public FilesTabView(Context context, TextView t) {
            super(context, t);
            this.text = t;
            this.text.setId(android.R.id.text1);
        }

        void updateLayout(TabLayout.Tab tab) {
            tab.setCustomView(this);
            ViewParent p = getParent();
            if (p instanceof LinearLayout) { // TabView extends LinearLayout
                LinearLayout l = (LinearLayout) p;
                lp = (LinearLayout.LayoutParams) l.getLayoutParams();
                if (lp != null) {
                    //lp.weight = 0;
                }
            }
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        FilesFragment left;
        FilesTabView leftTab;
        FilesFragment right;
        FilesTabView rightTab;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            left = FilesFragment.newInstance(Uri.parse(shared.getString(FilesApplication.PREF_LEFT, getDefault())));
            right = FilesFragment.newInstance(Uri.parse(shared.getString(FilesApplication.PREF_RIGHT, getDefault())));
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return left;
                case 1:
                    return right;
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return storage.getDisplayName(left.getUri());
                case 1:
                    return storage.getDisplayName(right.getUri());
            }
            return null;
        }

        public void update() {
            TabLayout.Tab left = tabLayout.getTabAt(0);
            leftTab = new FilesTabView(MainActivity.this, tabLayout);
            leftTab.updateLayout(left);
            TabLayout.Tab right = tabLayout.getTabAt(1);
            rightTab = new FilesTabView(MainActivity.this, tabLayout);
            rightTab.updateLayout(right);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        View navigationHeader = navigationView.getHeaderView(0);
        TextView ver = (TextView) navigationHeader.findViewById(R.id.nav_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = "v" + pInfo.versionName;
            ver.setText(version);
        } catch (PackageManager.NameNotFoundException e) {
            ver.setVisibility(View.GONE);
        }

        app = FilesApplication.from(this);

        storage = new Storage(this);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    final FilesTabView collapse, expand;
                    if (mViewPager.getCurrentItem() == 1) {
                        collapse = mSectionsPagerAdapter.leftTab;
                        expand = mSectionsPagerAdapter.rightTab;
                    } else {
                        collapse = mSectionsPagerAdapter.rightTab;
                        expand = mSectionsPagerAdapter.leftTab;
                    }
                    Animation a = new Animation() {
                        float w = collapse.lp.weight;

                        @Override
                        protected void applyTransformation(float f, Transformation t) {
                            collapse.lp.weight = w - (w - 1) * f;
                            collapse.requestLayout();
                        }
                    };
                    a.setInterpolator(new AccelerateInterpolator());
                    a.setDuration(100);
                    collapse.startAnimation(a);
                    Animation b = new Animation() {
                        float w = expand.lp.weight;

                        @Override
                        protected void applyTransformation(float f, Transformation t) {
                            expand.lp.weight = w + (3 - w) * f;
                            expand.requestLayout();
                        }
                    };
                    b.setInterpolator(new AccelerateInterpolator());
                    b.setDuration(100);
                    expand.startAnimation(b);
                }
            }
        });

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        mSectionsPagerAdapter.update();

        Menu m = navigationView.getMenu();
        bookmarksMenu = m.addSubMenu("Bookmarks");

        SubMenu settingsMenu = m.addSubMenu(R.string.menu_settings);
        settingsMenu.setIcon(R.drawable.ic_settings_black_24dp);
        MenuItem add = settingsMenu.add("Add Bookmark");
        add.setIntent(new Intent(ADD_BOOKMARK));
        add.setIcon(R.drawable.ic_add_black_24dp);

        reloadMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings)
            return true;

        if (id == R.id.action_addstorage) {
            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                @Override
                public void onResult(Uri uri) {
                    super.onResult(uri);
                    app.bookmarks.add(uri);
                    reloadMenu();
                }
            };
            choicer.setPermissionsDialog(this, Storage.PERMISSIONS_RW, RESULT_SAF);
            choicer.show(null);
            return true;
        }

        if (id == R.id.action_addsaf) {
            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                @Override
                public void onResult(Uri uri) {
                    super.onResult(uri);
                    app.bookmarks.add(uri);
                    reloadMenu();
                }
            };
            choicer.setStorageAccessFramework(this, RESULT_SAF);
            choicer.show(null);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("RestrictedApi")
    public static View findView(ViewGroup p, MenuItem item) {
        for (int i = 0; i < p.getChildCount(); i++) {
            View v = p.getChildAt(i);
            if (v instanceof ViewGroup) {
                View m = findView((ViewGroup) v, item);
                if (m != null)
                    return m;
            }
            if (v instanceof NavigationMenuItemView) {
                if (((NavigationMenuItemView) v).getItemData() == item)
                    return v;
            }
            if (v.getId() == item.getItemId())
                return v;
        }
        return null;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        Intent intent = item.getIntent();
        if (intent != null && intent.getAction().equals(ADD_BOOKMARK)) {
            PopupMenu menu = new PopupMenu(this, findView(navigationView, item));
            getMenuInflater().inflate(R.menu.menu_add, menu.getMenu());
            if (Build.VERSION.SDK_INT < 21) {
                onOptionsItemSelected(menu.getMenu().findItem(R.id.action_addstorage));
            } else {
                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return onOptionsItemSelected(item);
                    }
                });
                menu.show();
                return true;
            }
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_SAF:
                choicer.onActivityResult(resultCode, data);
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_SAF:
                choicer.onRequestPermissionsFailed(permissions);
                break;
        }
    }

    public void reloadMenu() {
        int accent = ThemeUtils.getThemeColor(this, R.attr.colorAccent);
        bookmarksMenu.clear();
        for (Uri u : app.bookmarks) {
            String s = u.getScheme();
            String n;
            if (s.startsWith(ContentResolver.SCHEME_FILE))
                n = ".../" + Storage.getFile(u).getName();
            else
                n = storage.getDisplayName(u);
            MenuItem m = bookmarksMenu.add(n);
            Intent intent = new Intent(BOOKMARK);
            intent.putExtra("url", u);
            m.setIntent(intent);
            m.setIcon(R.drawable.ic_storage_black_24dp);
            ImageButton b = new ImageButton(this);
            b.setColorFilter(accent);
            b.setImageResource(R.drawable.ic_delete_black_24dp);
            final Uri uri = u;
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Delete Bookmark");
                    builder.setMessage(R.string.are_you_sure);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            app.bookmarks.remove(uri);
                            reloadMenu();
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
                    builder.show();
                }
            });
            MenuItemCompat.setActionView(m, b);
        }
    }
}
