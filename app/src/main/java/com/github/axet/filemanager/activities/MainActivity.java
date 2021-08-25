package com.github.axet.filemanager.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
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
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.activities.AppCompatThemeActivity;
import com.github.axet.androidlibrary.preferences.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.PathMax;
import com.github.axet.androidlibrary.widgets.SearchView;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.app.SuperUser;
import com.github.axet.filemanager.fragments.FilesFragment;
import com.github.axet.filemanager.fragments.HexDialogFragment;
import com.github.axet.filemanager.fragments.SearchFragment;
import com.github.axet.filemanager.services.StorageProvider;

import net.i2p.android.ext.floatingactionbutton.FloatingActionButton;
import net.i2p.android.ext.floatingactionbutton.FloatingActionsMenu;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeSet;

public class MainActivity extends AppCompatThemeActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final int RESULT_ADDBOOKMARK = 1;

    public static final String ADD_BOOKMARK = "ADDBOOKMARK";
    public static final Uri EXIT = Uri.parse("");
    public static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"; // UsbManager.ACTION_USB_STATE@hide

    public SectionsPagerAdapter mSectionsPagerAdapter;
    public ViewPager mViewPager;
    SearchFragment search;
    Storage storage;
    TabLayout tabLayout;
    NavigationView navigationView;
    OpenChoicer choicer;
    FilesApplication app;
    Menu bookmarksMenu;
    ViewPager.OnPageChangeListener onPageChangeListener;
    String oldSearch;
    public SearchView.CollapseListener collapseListener;
    Handler handler = new Handler();
    Runnable reload = new Runnable() {
        @Override
        public void run() {
            reloadMenu();
            sendBroadcast(new Intent(FilesFragment.MOVE_UPDATE));
        }
    };
    BroadcastReceiver mounted = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "media" + intent);
            handler.removeCallbacks(reload);
            for (int i = 0; i < 3; i++)
                handler.postDelayed(reload, i * 2000);
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        context.startActivity(intent);
    }

    public static String getDefault(Context context) {
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null || !Storage.permitted(context, Storage.PERMISSIONS_RO))
            return Uri.fromFile(context.getFilesDir()).toString();
        else
            return Uri.fromFile(ext).toString();
    }

    public static class FilesTabView extends PathMax {
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

        void attachLayout(TabLayout.Tab tab) {
            tab.setCustomView(this);
            ViewParent p = getParent();
            if (p instanceof LinearLayout) { // TabView extends LinearLayout
                LinearLayout l = (LinearLayout) p;
                lp = (LinearLayout.LayoutParams) l.getLayoutParams();
            }
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public void save() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            SharedPreferences.Editor editor = shared.edit();
            editor.putString(FilesApplication.PREF_LEFT, getLeft().getUri().toString());
            editor.putString(FilesApplication.PREF_RIGHT, getRight().getUri().toString());
            editor.commit();
        }

        FilesFragment getFragment(int i) {
            FilesFragment f = (FilesFragment) instantiateItem(mViewPager, i);
            finishUpdate(mViewPager);
            return f;
        }

        FilesFragment getLeft() {
            return getFragment(0);
        }

        FilesFragment getRight() {
            return getFragment(1);
        }

        public FilesFragment getActiveFragment() {
            return getFragment(mViewPager.getCurrentItem());
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return FilesFragment.newInstance(Uri.parse(shared.getString(FilesApplication.PREF_LEFT, getDefault(MainActivity.this))));
                case 1:
                    return FilesFragment.newInstance(Uri.parse(shared.getString(FilesApplication.PREF_RIGHT, getDefault(MainActivity.this))));
            }
            return null;
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return Storage.getDisplayName(MainActivity.this, getLeft().getUri()) + " "; // prevent PathMax eat last slash
                case 1:
                    return Storage.getDisplayName(MainActivity.this, getRight().getUri()) + " "; // prevent PathMax eat last slash
            }
            return null;
        }

        public void update() {
            TabLayout.Tab left = tabLayout.getTabAt(0);
            FilesTabView leftTab = new FilesTabView(MainActivity.this, tabLayout);
            leftTab.attachLayout(left);
            TabLayout.Tab right = tabLayout.getTabAt(1);
            FilesTabView rightTab = new FilesTabView(MainActivity.this, tabLayout);
            rightTab.attachLayout(right);
        }

        public FilesTabView getTabView(int i) {
            return (FilesTabView) tabLayout.getTabAt(i).getCustomView();
        }
    }

    public static class EmptyTrashBuilder extends FilesFragment.DeleteBuilder {
        public EmptyTrashBuilder(Context context) {
            super(context);
            op = new FilesFragment.PendingOperation(getContext()) {
                int index;
                File[] tmpFiles;

                {
                    tmpFiles = new File[]{storage.getLocalTrash(), storage.getExternalTrash(), storage.getStorageTrash()};
                    calcs = new ArrayList<>();
                }

                @Override
                public String formatStart() {
                    return Storage.getDisplayName(context, Uri.fromFile(storage.getTrash())) + "/*";
                }

                @Override
                public void run() {
                    try {
                        if (calcIndex < calcs.size()) {
                            deleteCalc();
                            return;
                        }
                        if (index < tmpFiles.length) {
                            File f = tmpFiles[index];
                            if (f.exists() && f.isDirectory() && f.canRead() && f.canWrite()) {
                                Uri uri = Uri.fromFile(f);
                                calcUri = uri;
                                calcs.addAll(storage.list(uri));
                            }
                            index++;
                            post();
                            return;
                        }
                        if (filesIndex < files.size()) {
                            deleteProcess();
                            return;
                        }
                        success();
                    } catch (RuntimeException e) {
                        deleteError(e);
                    }
                }

                @Override
                public void post() {
                    handler.removeCallbacks(this);
                    handler.post(this);
                }
            };
            neutral = new View.OnClickListener() { // pause/resume
                @Override
                public void onClick(View v) {
                    final View.OnClickListener neutral = this;
                    op.pause();
                    handler.removeCallbacks(op);
                    final Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setText(R.string.copy_resume);
                    EmptyTrashBuilder.this.neutral = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            op.run();
                            b.setText(R.string.copy_pause);
                            EmptyTrashBuilder.this.neutral = neutral;
                        }
                    };
                }
            };
            dismiss = new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    handler.removeCallbacks(op);
                    op.close();
                    dismiss();
                }
            };
        }

        public void success() {
            super.success();
        }
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        collapseListener = new SearchView.CollapseListener(getSupportActionBar());

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        View navigationHeader = navigationView.getHeaderView(0);
        TextView ver = (TextView) navigationHeader.findViewById(R.id.nav_version);
        AboutPreferenceCompat.setVersion(ver);

        final FloatingActionsMenu fab = (FloatingActionsMenu) findViewById(R.id.fab);
        FloatingActionButton fabFolder = (FloatingActionButton) findViewById(R.id.fab_create_folder);
        fabFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final OpenFileDialog.EditTextDialog edit = new OpenFileDialog.EditTextDialog(MainActivity.this);
                edit.setTitle(R.string.create_folder);
                edit.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String s = edit.getText();
                        final FilesFragment f = mSectionsPagerAdapter.getActiveFragment();
                        try {
                            final Uri uri = storage.mkdir(f.getUri(), s);
                            if (uri == null)
                                throw new RuntimeException("unable to create " + s);
                            f.reload();
                            f.highlight(uri);
                        } catch (RuntimeException e) {
                            Log.d(TAG, "create folder", e);
                            Toast.makeText(MainActivity.this, R.string.not_permitted, Toast.LENGTH_SHORT).show();
                        } finally {
                            storage.closeSu();
                        }

                    }
                });
                edit.show();
                fab.collapse();
            }
        });
        FloatingActionButton fabFile = (FloatingActionButton) findViewById(R.id.fab_create_file);
        fabFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final OpenFileDialog.EditTextDialog edit = new OpenFileDialog.EditTextDialog(MainActivity.this);
                edit.setTitle(R.string.create_file);
                edit.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String s = edit.getText();
                        FilesFragment f = mSectionsPagerAdapter.getActiveFragment();
                        try {
                            Uri uri = storage.touch(f.getUri(), s);
                            if (uri == null)
                                throw new RuntimeException("unable to create file");
                            f.reload();
                            f.highlight(uri);
                        } catch (RuntimeException e) {
                            Log.d(TAG, "create file", e);
                            Toast.makeText(MainActivity.this, R.string.not_permitted, Toast.LENGTH_SHORT).show();
                        } finally {
                            storage.closeSu();
                        }

                    }
                });
                edit.show();
                fab.collapse();
            }
        });

        app = FilesApplication.from(this);

        storage = new Storage(this);
        if (storage.getRoot()) {
            if (!SuperUser.sudoTest(this)) { // run once per app restart, only when user already enabled root
                SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor edit = shared.edit();
                edit.remove(FilesApplication.PREF_ROOT);
                edit.commit();
                Toast.Text(this, SettingsActivity.SUIO_ERROR);
            }
        } else {
            SuperUser.sudoTest(this); // we need suio binary for 'stat' operations
        }
        storage.closeSu();

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.pagerContainer);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        onPageChangeListener = new ViewPager.OnPageChangeListener() {
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
                        collapse = mSectionsPagerAdapter.getTabView(0);
                        expand = mSectionsPagerAdapter.getTabView(1);
                    } else {
                        collapse = mSectionsPagerAdapter.getTabView(1);
                        expand = mSectionsPagerAdapter.getTabView(0);
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
                            expand.lp.weight = w + (2 - w) * f;
                            expand.requestLayout();
                        }
                    };
                    b.setInterpolator(new AccelerateInterpolator());
                    b.setDuration(100);
                    expand.startAnimation(b);
                }
            }
        };
        mViewPager.addOnPageChangeListener(onPageChangeListener);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        Menu m = navigationView.getMenu();
        bookmarksMenu = m.addSubMenu(R.string.bookmarks);
        SubMenu settingsMenu = m.addSubMenu(R.string.menu_settings);
        settingsMenu.setIcon(R.drawable.ic_settings_black_24dp);
        MenuItem add = settingsMenu.add(R.string.add_bookmark);
        add.setIntent(new Intent(ADD_BOOKMARK));
        add.setIcon(R.drawable.ic_add_black_24dp);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        mViewPager.setCurrentItem(shared.getInt(FilesApplication.PREF_ACTIVE, 0));

        openIntent(getIntent());

        update();

        IntentFilter ff = new IntentFilter();
        ff.addAction(Intent.ACTION_MEDIA_MOUNTED);
        ff.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        ff.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        ff.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        ff.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        ff.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        ff.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        ff.addAction(ACTION_USB_STATE);
        registerReceiver(mounted, ff);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mounted);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent " + intent);
        setIntent(intent);
        openIntent(intent);
    }

    public void openIntent(Intent intent) {
        if (intent == null)
            return;
        String a = intent.getAction();
        if (a == null)
            return;
        if (a.equals(Intent.ACTION_VIEW)) {
            try {
                Uri u = intent.getData();
                String s = u.getScheme();
                if (s.equals(StorageProvider.SCHEME_FOLDER))
                    u = u.buildUpon().scheme(ContentResolver.SCHEME_FILE).build();
                if (Build.VERSION.SDK_INT >= 21 && intent.getType().equals(DocumentsContract.Root.MIME_TYPE_ITEM)) {
                    u = Storage.buildTreeDocumentUriRoot(u);
                    ContentResolver resolver = getContentResolver();
                    resolver.takePersistableUriPermission(u, Storage.SAF_RW); // refresh perms
                }
                if (u != null)
                    open(u);
            } catch (Exception e) {
                Toast.Error(this, e);
                return;
            }
        }
    }

    public void open(Uri uri) {
        FilesFragment f = mSectionsPagerAdapter.getActiveFragment();
        f.load(uri, true);
        searchClose();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem search = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(search);
        collapseListener.addItem(search);
        searchView.setOnQueryTextListener(new android.support.v7.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                oldSearch = query;
                searchView.clearFocus();
                searchOpen(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        searchView.setOnCloseButtonListener(new SearchView.OnCloseButtonListener() {
            @Override
            public void onClosed() {
                if (MainActivity.this.search != null) {
                    if (MainActivity.this.search.isActive()) {
                        MainActivity.this.search.stop();
                    }
                }
                oldSearch = "";
            }
        });
        searchView.setOnCollapsedListener(new SearchView.OnCollapsedListener() {
            @Override
            public void onCollapsed() {
                searchClose();
            }
        });
        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (oldSearch != null && !oldSearch.isEmpty())
                    searchView.setQuery(oldSearch, false);
            }
        });
        return true;
    }

    public void openHex(Uri uri, boolean panel) {
        HexDialogFragment d = HexDialogFragment.create(uri, panel);
        d.show(getSupportFragmentManager(), "");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_about) {
            AboutPreferenceCompat.buildDialog(this, R.raw.about).show();
            return true;
        }

        if (id == R.id.action_settings) {
            SettingsActivity.start(this);
            return true;
        }

        if (id == R.id.action_addstorage) {
            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                @Override
                public void onResult(Uri uri) {
                    super.onResult(uri);
                    app.bookmarks.add(uri);
                    app.bookmarks.save();
                    reloadMenu();
                }

                @Override
                public OpenFileDialog fileDialogBuild() {
                    OpenFileDialog d = super.fileDialogBuild();
                    d.setAdapter(new OpenFileDialog.FileAdapter(d.getContext(), d.getCurrentPath()) {
                        @Override
                        public void scan() {
                            if (storage.getRoot())
                                currentPath = new Storage.VirtualFile(storage, currentPath);
                            super.scan();
                        }

                        @Override
                        public File open(String name) {
                            return new Storage.VirtualFile(storage, currentPath, name);
                        }
                    });
                    return d;
                }

                @Override
                public void onRequestPermissionsFailed(String[] permissions) {
                    Toast.makeText(context, R.string.not_permitted, Toast.LENGTH_SHORT).show();
                }
            };
            choicer.setPermissionsDialog(this, Storage.PERMISSIONS_RW, RESULT_ADDBOOKMARK);
            Uri old = mSectionsPagerAdapter.getActiveFragment().getUri();
            if (!old.getScheme().equals(ContentResolver.SCHEME_FILE))
                old = null;
            choicer.show(old);
            return true;
        }

        if (id == R.id.action_addsaf) {
            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                @Override
                public void onResult(Uri uri) {
                    super.onResult(uri);
                    app.bookmarks.add(uri);
                    app.bookmarks.save();
                    reloadMenu();
                }
            };
            choicer.setStorageAccessFramework(this, RESULT_ADDBOOKMARK);
            choicer.show(null);
            return true;
        }

        Intent intent = item.getIntent();
        if (intent != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
            Uri u = intent.getData();
            if (u != null)
                open(u);
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
        Intent intent = item.getIntent();
        if (intent != null) {
            String a = intent.getAction();
            if (a.equals(ADD_BOOKMARK)) {
                PopupMenu menu = new PopupMenu(this, findView(navigationView, item));
                Menu m = menu.getMenu();
                getMenuInflater().inflate(R.menu.menu_add, m);
                if (Build.VERSION.SDK_INT < 21)
                    m.removeItem(R.id.action_addsaf);
                if (m.size() == 1) {
                    onOptionsItemSelected(m.getItem(0));
                } else {
                    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            return onOptionsItemSelected(item);
                        }
                    });
                    menu.show();
                }
                return true;
            }
            if (a.equals(Intent.ACTION_VIEW)) {
                onOptionsItemSelected(item);
            }
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadMenu(); // trash folder
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_ADDBOOKMARK:
                choicer.onActivityResult(resultCode, data);
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_ADDBOOKMARK:
                choicer.onRequestPermissionsResult(permissions, grantResults);
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        SharedPreferences.Editor editor = shared.edit();
        editor.putInt(FilesApplication.PREF_ACTIVE, mViewPager.getCurrentItem());
        editor.commit();
        mSectionsPagerAdapter.save();
    }

    public String getDrawerName(Uri u) {
        String s = u.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = Storage.getFile(u);
            if (f.equals(Environment.getExternalStorageDirectory()))
                return f.getPath();
            if (f.equals(FilesApplication.getLocalTmp()))
                return f.getPath();
            return ".../" + f.getName();
        } else {
            return Storage.getDisplayName(MainActivity.this, u);
        }
    }

    public boolean ejected(Uri u) { // do not show IO popups for every bookmark
        try {
            return storage.ejected(u);
        } catch (RuntimeException e) {
            Log.e(TAG, "ejected", e);
            return true;
        }
    }

    public void reloadMenu() {
        int accent = ThemeUtils.getThemeColor(this, R.attr.colorAccent);
        bookmarksMenu.clear();
        if (app.bookmarks.isEmpty()) {
            MenuItem m = bookmarksMenu.add(R.string.empty_list);
            m.setEnabled(false);
            m.setIcon(new ColorDrawable(Color.TRANSPARENT));
        }
        for (Uri u : app.bookmarks) {
            String n = getDrawerName(u);
            MenuItem m = bookmarksMenu.add(n);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(u);
            m.setIntent(intent);
            m.setIcon(ejected(u) ? R.drawable.ic_block_black_24dp : R.drawable.ic_storage_black_24dp);
            AppCompatImageButton b = new AppCompatImageButton(this);
            b.setColorFilter(accent);
            b.setImageResource(R.drawable.ic_delete_black_24dp);
            final Uri uri = u;
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.delete_bookmark);
                    builder.setMessage(R.string.are_you_sure);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            app.bookmarks.remove(uri);
                            app.bookmarks.save();
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
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        if (shared.getBoolean(FilesApplication.PREF_RECYCLE, false)) {
            final File trash = storage.getTrash();
            MenuItem m = bookmarksMenu.add(R.string.recyclebin_folder);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.fromFile(trash));
            m.setIntent(intent);
            m.setIcon(R.drawable.ic_storage_black_24dp);
            AppCompatImageButton b = new AppCompatImageButton(this);
            b.setColorFilter(accent);
            b.setImageResource(R.drawable.ic_delete_forever_black_24dp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.empty_recyclebin);
                    builder.setMessage(R.string.are_you_sure);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            EmptyTrashBuilder delete = new EmptyTrashBuilder(MainActivity.this);
                            delete.show();
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
        storage.closeSu();
    }

    public void update() {
        mSectionsPagerAdapter.notifyDataSetChanged();
        mSectionsPagerAdapter.update();
        onPageChangeListener.onPageScrollStateChanged(ViewPager.SCROLL_STATE_IDLE);
        clearCache();
    }

    public void clearCache() {
        for (Uri u : new TreeSet<>(Storage.ARCHIVE_CACHE.keySet())) {
            String p = u.getPath();
            if (Storage.relative(p, mSectionsPagerAdapter.getLeft().getUri().getPath()) != null)
                continue;
            if (Storage.relative(p, mSectionsPagerAdapter.getRight().getUri().getPath()) != null)
                continue;
            Storage.ArchiveCache c = Storage.ARCHIVE_CACHE.get(u);
            c.close();
            Storage.ARCHIVE_CACHE.remove(u);
        }
    }

    @Override
    public void onBackPressed() {
        FilesFragment f = mSectionsPagerAdapter.getActiveFragment();
        if (f.old == null) {
            f.old = EXIT;
            Toast.makeText(this, R.string.back_exit, Toast.LENGTH_SHORT).show();
            return;
        }
        if (f.old != EXIT) {
            f.load(f.old, false);
            f.old = null;
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void supportInvalidateOptionsMenu() {
        if (search != null)
            return; // prevent update options, we need SearchView visible
        super.supportInvalidateOptionsMenu();
    }

    public void searchOpen(String q) {
        FilesFragment f = mSectionsPagerAdapter.getActiveFragment();
        search = SearchFragment.newInstance(f.getUri(), q);
        View v = findViewById(R.id.searchContainer);
        v.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction().replace(R.id.searchContainer, search).commit();
        mViewPager.setVisibility(View.GONE);
    }

    public void searchClose() {
        if (search == null)
            return;
        View v = findViewById(R.id.searchContainer);
        v.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction().remove(search).commit();
        search = null;
        mViewPager.setVisibility(View.VISIBLE);
    }
}
