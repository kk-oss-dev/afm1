package com.github.axet.filemanager.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.support.v4.provider.DocumentFile;
import android.support.v7.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class Storage extends com.github.axet.androidlibrary.app.Storage {

    public Storage(Context context) {
        super(context);
    }

    public boolean getRoot() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        return shared.getBoolean(FilesApplication.PREF_ROOT, false);
    }

    public InputStream open(Uri uri) throws IOException {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (getRoot()) {
                return SuperUser.cat(uri);
            } else {
                File k = getFile(uri);
                return new FileInputStream(k);
            }
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return resolver.openInputStream(uri);
        } else {
            throw new UnknownUri();
        }
    }

    public boolean touch(Uri uri, long last) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(uri);
            if (getRoot()) {
                return SuperUser.touch(k, last).ok();
            } else {
                return k.setLastModified(last); // not working for most devices, requiring root
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return false; // not supported operation for SAF
        } else {
            throw new Storage.UnknownUri();
        }
    }

    public boolean touch(Uri uri, String name) {
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = Storage.getFile(uri);
            return SuperUser.touch(k).ok();
        }
        return super.touch(uri, name);
    }

    @Override
    public boolean delete(Uri t) { // default Storage.delete() uses 'rm -rf'
        String s = t.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(t);
            if (getRoot()) {
                return SuperUser.delete(k).ok();
            } else {
                return k.delete();
            }
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            DocumentFile f = DocumentFile.fromSingleUri(context, t);
            return f.delete();
        } else {
            throw new UnknownUri();
        }
    }

    public Uri mkdir(Uri to, String name) {
        if (to.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = Storage.getFile(to);
            File m = new File(k, name);
            if (SuperUser.mkdir(m).ok())
                return Uri.fromFile(m);
        }
        return super.mkdir(to, name);
    }

    @Override
    public long getLength(Uri uri) {
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            return SuperUser.length(Storage.getFile(uri));
        }
        return super.getLength(uri);
    }

    @Override
    public ArrayList<Node> list(Uri uri) {
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.ls(SuperUser.LSA, Storage.getFile(uri));
            for (File f : ff)
                files.add(new Storage.Node(f));
            return files;
        }
        return super.list(uri);
    }

    @Override
    public ArrayList<Node> walk(Uri root, Uri uri) {
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            int r = Storage.getFile(root).getPath().length();
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.ls(SuperUser.LSa, Storage.getFile(uri));
            for (File f : ff) {
                Storage.Node k = new Storage.Node(Uri.fromFile(f), f.getPath().substring(r), f.isDirectory(), f.length(), f.lastModified());
                files.add(k);
            }
            return files;
        }
        return super.walk(root, uri);
    }
}