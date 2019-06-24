package com.github.axet.filemanager.app;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activities.MainActivity;
import com.github.axet.filemanager.services.StorageProvider;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class FilesApplication extends MainApplication {
    public static final String PREF_LEFT = "left";
    public static final String PREF_RIGHT = "right";
    public static final String PREF_ACTIVE = "active";

    public static final String PREF_BOOKMARK_COUNT = "bookmark_count";
    public static final String PREF_BOOKMARK_PREFIX = "bookmark_";

    public static final String PREF_THEME = "theme";
    public static final String PREF_ROOT = "root";
    public static final String PREF_RECYCLE = "recycle";

    public static final String PREFERENCE_VERSION = "version";

    public static final String PREFERENCE_SORT = "sort";

    public Bookmarks bookmarks;
    public Storage.Nodes copy; // selected files
    public Storage.Nodes cut; // selected files
    public Uri uri; // selected root

    public static String formatSize(Context context, long s) {
        if (s < 1024)
            return s + " " + context.getString(R.string.size_bytes);
        else
            return MainApplication.formatSize(context, s);
    }

    public static File getLocalTmp() {
        String s = System.getenv("TMPDIR");
        if (s == null || s.isEmpty()) {
            s = System.getenv("ANDROID_DATA");
            if (s == null || s.isEmpty())
                s = "/data";
            File f = new File(s, "local/tmp");
            return f;
        }
        return new File(s);
    }

    public static FilesApplication from(Context context) {
        return (FilesApplication) MainApplication.from(context);
    }

    public static int getTheme(Context context, int light, int dark) {
        return MainApplication.getTheme(context, PREF_THEME, light, dark, context.getString(R.string.Theme_Dark));
    }

    public class Bookmarks extends ArrayList<Uri> {
        public Bookmarks() {
            load();
        }

        public void save() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(FilesApplication.this);
            SharedPreferences.Editor editor = shared.edit();
            for (int i = 0; i < size(); i++)
                editor.putString(PREF_BOOKMARK_PREFIX + i, get(i).toString());
            editor.putInt(PREF_BOOKMARK_COUNT, size());
            editor.commit();
        }

        public void load() {
            clear();
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(FilesApplication.this);
            int count = shared.getInt(PREF_BOOKMARK_COUNT, -1);
            if (count == -1) {
                ArrayList<File> ff = new ArrayList<>(Arrays.asList(Environment.getExternalStorageDirectory(),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
                if (Build.VERSION.SDK_INT >= 19)
                    ff.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));
                for (File f : ff) {
                    if (f.exists())
                        add(Uri.fromFile(f));
                }
            }
            for (int i = 0; i < count; i++) {
                try {
                    Uri uri = Uri.parse(shared.getString(PREF_BOOKMARK_PREFIX + i, ""));
                    String s = uri.getScheme();
                    if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                        ContentResolver resolver = getContentResolver();
                        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); // refresh perms
                    }
                    add(uri);
                } catch (SecurityException e) {
                    Log.e(TAG, "bad perms", e);
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bookmarks = new Bookmarks();

        switch (getVersion(PREFERENCE_VERSION, R.xml.pref_general)) {
            case -1:
                SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor edit = shared.edit();
                edit.putInt(PREFERENCE_VERSION, 1);
                edit.commit();
                break;
            case 0:
                version_0_to_1();
                break;
        }
    }

    public void show(String title, String text) {
        PendingIntent main = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteNotificationCompat.Default builder = new RemoteNotificationCompat.Default(this, R.mipmap.ic_launcher_foreground);
        builder.setTheme(FilesApplication.getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark))
                .setTitle(title)
                .setText(text)
                .setMainIntent(main)
                .setChannel(new NotificationChannelCompat(this, "status", "Status", NotificationManagerCompat.IMPORTANCE_DEFAULT))
                .setAdaptiveIcon(R.mipmap.ic_launcher_foreground)
                .setSmallIcon(R.drawable.ic_launcher_notification);
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    void version_0_to_1() {
        Locale locale = Locale.getDefault();
        if (locale.toString().startsWith("ru")) {
            String title = "Приложение переименовано";
            String text = "'File Manager' -> '" + getString(R.string.app_name) + "'";
            show(text, title);
        }
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = shared.edit();
        edit.putInt(PREFERENCE_VERSION, 1);
        edit.commit();
    }

}

