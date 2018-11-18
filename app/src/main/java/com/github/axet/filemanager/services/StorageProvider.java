package com.github.axet.filemanager.services;

import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.github.axet.androidlibrary.services.FileProvider;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.SuperUser;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StorageProvider extends com.github.axet.androidlibrary.services.StorageProvider {
    public static String TAG = StorageProvider.class.getCanonicalName();

    public static final String FILE_PREFIX = "player";
    public static final String FILE_SUFFIX = ".tmp";

    public static com.github.axet.androidlibrary.services.StorageProvider getProvider() {
        return infos.get(StorageProvider.class);
    }

    public ParcelFileDescriptor openRootFile(final Uri f, String mode) {
        try {
            if (mode.equals("r")) { // can be pipe. check ContentProvider#openFile
                ParcelFileDescriptor[] ff = ParcelFileDescriptor.createPipe();
                final ParcelFileDescriptor r = ff[0];
                final ParcelFileDescriptor w = ff[1];
                final InputStream is = SuperUser.cat(f);
                Thread thread = new Thread("Read Root File") {
                    @Override
                    public void run() {
                        OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(w);
                        try {
                            IOUtils.copy(is, os);
                            is.close();
                        } catch (IOException e) {
                            Log.d(TAG, "Error reading root file", e);
                        } finally {
                            try {
                                os.close();
                            } catch (IOException e) {
                                Log.d(TAG, "copy close error", e);
                            }
                        }
                    }
                };
                thread.start();
                return r;
            } else { // rw - has to be File. check ContentProvider#openFile
                File tmp = getContext().getExternalCacheDir();
                if (tmp == null)
                    tmp = getContext().getCacheDir();
                tmp = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, tmp);
                FileOutputStream os = new FileOutputStream(tmp);
                try {
                    InputStream is = SuperUser.cat(f);
                    IOUtils.copy(is, os);
                    is.close();
                } finally {
                    try {
                        os.close();
                    } catch (IOException e) {
                        Log.d(TAG, "copy close error", e);
                    }
                }
                final int fileMode = FileProvider.modeToMode(mode);
                return ParcelFileDescriptor.open(tmp, fileMode);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        final Uri f = find(uri);
        if (f == null)
            return null;
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (shared.getBoolean(FilesApplication.PREF_ROOT, false))
            return openRootFile(f, mode);
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
            return new AssetFileDescriptor(openRootFile(f, mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH); // -1 means full file, check ContentResolver#openFileDescriptor
        return super.openAssetFile(uri, mode);
    }
}
