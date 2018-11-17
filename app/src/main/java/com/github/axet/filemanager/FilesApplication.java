package com.github.axet.filemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.MainApplication;

import java.util.ArrayList;

public class FilesApplication extends MainApplication {
    public static final String PREF_LEFT = "left";
    public static final String PREF_RIGHT = "right";

    public static final String PREF_BOOKMARK_COUNT = "bookmark_count";
    public static final String PREF_BOOKMARK_PREFIX = "bookmark_";

    public ArrayList<Uri> bookmarks = new ArrayList<>();

    public static FilesApplication from(Context context) {
        return (FilesApplication) MainApplication.from(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        load();
    }

    public void save() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = shared.edit();
        for (int i = 0; i < bookmarks.size(); i++) {
            editor.putString(PREF_BOOKMARK_PREFIX + i, bookmarks.get(i).toString());
        }
        editor.putInt(PREF_BOOKMARK_COUNT, bookmarks.size());
        editor.commit();
    }

    public void load() {
        bookmarks.clear();
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        int count = shared.getInt(PREF_BOOKMARK_COUNT, 0);
        for (int i = 0; i < count; i++) {
            Uri uri = Uri.parse(shared.getString(PREF_BOOKMARK_PREFIX + i, ""));
            bookmarks.add(uri);
        }
    }
}
