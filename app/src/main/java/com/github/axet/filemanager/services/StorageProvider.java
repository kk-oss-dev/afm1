package com.github.axet.filemanager.services;

import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.app.SuperUser;

import java.io.File;
import java.io.FileNotFoundException;

public class StorageProvider extends com.github.axet.androidlibrary.services.StorageProvider {
    public static String TAG = StorageProvider.class.getCanonicalName();

    public static StorageProvider getProvider() {
        return (StorageProvider) infos.get(StorageProvider.class);
    }

    public ParcelFileDescriptor openRootFile(final File f, String mode) {
        return openInputStream(new InputStreamWriter(SuperUser.cat(f)) {
            @Override
            public long getSize() {
                return SuperUser.length(f);
            }
        }, mode);
    }

    @Override
    public boolean onCreate() {
        deleteTmp();
        return super.onCreate();
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return super.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        final Uri f = find(uri);
        if (f == null)
            return null;
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (shared.getBoolean(FilesApplication.PREF_ROOT, false))
            return openRootFile(Storage.getFile(f), mode);
        return super.openFile(uri, mode);
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        final Uri f = find(uri);
        if (f == null)
            return null;
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (shared.getBoolean(FilesApplication.PREF_ROOT, false))
            return new AssetFileDescriptor(openRootFile(Storage.getFile(f), mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        return super.openAssetFile(uri, mode);
    }
}
