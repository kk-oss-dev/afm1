package com.github.axet.filemanager.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.filemanager.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class FilesApplication extends MainApplication {
    public static final String PREF_LEFT = "left";
    public static final String PREF_RIGHT = "right";
    public static final String PREF_ACTIVE = "active";

    public static final String PREF_BOOKMARK_COUNT = "bookmark_count";
    public static final String PREF_BOOKMARK_PREFIX = "bookmark_";

    public static final String PREF_THEME = "theme";
    public static final String PREF_ROOT = "root";
    public static final String PREF_RECYCLE = "recycle";

    public Bookmarks bookmarks;
    public ArrayList<Storage.Node> copy; // selected files
    public ArrayList<Storage.Node> cut; // selected files
    public Uri uri; // selected root

    public static String formatSize(Context context, long s) {
        if (s < 1024) {
            return s + " " + context.getString(R.string.size_bytes);
        } else {
            return MainApplication.formatSize(context, s);
        }
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

    public class Bookmarks extends ArrayList<Uri> {
        public Bookmarks() {
            load();
        }

        public void save() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(FilesApplication.this);
            SharedPreferences.Editor editor = shared.edit();
            for (int i = 0; i < size(); i++) {
                editor.putString(PREF_BOOKMARK_PREFIX + i, get(i).toString());
            }
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
                Uri uri = Uri.parse(shared.getString(PREF_BOOKMARK_PREFIX + i, ""));
                add(uri);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bookmarks = new Bookmarks();
    }
}
