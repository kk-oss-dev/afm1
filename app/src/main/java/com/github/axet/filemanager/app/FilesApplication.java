package com.github.axet.filemanager.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.MainApplication;

import java.util.ArrayList;

public class FilesApplication extends MainApplication {
    public static final String PREF_LEFT = "left";
    public static final String PREF_RIGHT = "right";

    public static final String PREF_BOOKMARK_COUNT = "bookmark_count";
    public static final String PREF_BOOKMARK_PREFIX = "bookmark_";

    public static final String PREF_THEME = "theme";
    public static final String PREF_ROOT = "root";

    public Bookmarks bookmarks;

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
                add(Uri.fromFile(Environment.getExternalStorageDirectory()));
                add(Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)));
                add(Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
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
