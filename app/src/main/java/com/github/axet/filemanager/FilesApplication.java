package com.github.axet.filemanager;

import android.content.Context;
import android.net.Uri;

import com.github.axet.androidlibrary.app.MainApplication;

import java.util.ArrayList;

public class FilesApplication extends MainApplication {
    public static final String PREF_LEFT = "left";
    public static final String PREF_RIGHT = "right";

    public ArrayList<Uri> bookmarks = new ArrayList<>();

    public static FilesApplication from(Context context) {
        return (FilesApplication) MainApplication.from(context);
    }
}
