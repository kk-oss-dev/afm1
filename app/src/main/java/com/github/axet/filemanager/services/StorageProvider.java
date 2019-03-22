package com.github.axet.filemanager.services;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.github.axet.androidlibrary.services.FileProvider;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.app.SuperUser;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;

public class StorageProvider extends com.github.axet.androidlibrary.services.StorageProvider {
    public static String TAG = StorageProvider.class.getCanonicalName();

    public static final String OTG = "/mnt/media_rw/";

    Storage storage;
    int counter;

    public static StorageProvider getProvider() {
        return (StorageProvider) infos.get(StorageProvider.class);
    }

    public static ArrayList<File> getOTGList(Storage storage) { // otg usb's and portable's list
        File otg = new File(OTG);
        return SuperUser.ls(storage.getSu(), otg, new FileFilter() {
            @Override
            public boolean accept(File file) {
                String name = file.getName();
                Matcher m = OpenFileDialog.DEFAULT_STORAGE_PATTERN.matcher(name);
                return m.matches();
            }
        });
    }

    @TargetApi(21)
    public static Uri filterOTGFolderIntent(Storage storage, Uri uri) { // convert content:///primary to file://
        Context context = storage.getContext();
        String tree;
        if (DocumentsContract.isDocumentUri(context, uri))
            tree = DocumentsContract.getDocumentId(uri);
        else
            tree = DocumentsContract.getTreeDocumentId(uri);
        String[] ss = tree.split(com.github.axet.androidlibrary.app.Storage.COLON, 2); // 1D13-0F08:folder_name
        String id = ss[0];
        ArrayList<File> ff = getOTGList(storage);
        for (File f : ff) {
            if (id.equals(f.getName())) {
                File r = new File(f, ss[1]);
                uri = Uri.fromFile(r);
            }
        }
        return uri;
    }

    public ParcelFileDescriptor openRootFile(final File f, String mode) throws FileNotFoundException {
        counter++;
        return openInputStream(new InputStreamWriter(new SuperUser.FileInputStream(f)) {
            @Override
            public long getSize() {
                return SuperUser.length(f);
            }

            @Override
            public void close() throws IOException {
                super.close();
                counter--;
                freeFiles();
            }
        }, mode);
    }

    public ParcelFileDescriptor openArchiveFile(final Storage.ArchiveReader r, String mode) throws FileNotFoundException {
        counter++;
        return openInputStream(new InputStreamWriter(r.open()) {
            @Override
            public long getSize() {
                return r.length();
            }

            @Override
            public void close() throws IOException {
                super.close();
                counter--;
                freeFiles();
            }
        }, mode);
    }

    public void freeFiles() {
        if (counter <= 0)
            storage.closeSu();
    }

    @Override
    public boolean onCreate() {
        deleteTmp();
        boolean b = super.onCreate();
        super.storage = storage = new Storage(getContext());
        return b;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        final Uri f = find(uri);
        if (f == null)
            return null;
        final Storage.ArchiveReader r = storage.fromArchive(f, false);
        if (r == null)
            return super.query(uri, projection, selection, selectionArgs, sortOrder, cancellationSignal);
        if (projection == null)
            projection = FileProvider.COLUMNS;
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                values[i++] = new File(r.path).getName();
            } else if (OpenableColumns.SIZE.equals(col)) {
                values[i++] = r.length();
            }
        }
        values = FileProvider.copyOf(values, i);
        final MatrixCursor cursor = new MatrixCursor(projection, 1);
        cursor.addRow(values);
        freeFiles();
        return cursor;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        final Uri f = find(uri);
        if (f == null)
            return null;
        final Storage.ArchiveReader r = storage.fromArchive(f, false);
        if (r != null)
            return openArchiveFile(r, mode);
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
        final Storage.ArchiveReader r = storage.fromArchive(f, false);
        if (r != null)
            return new AssetFileDescriptor(openArchiveFile(r, mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (shared.getBoolean(FilesApplication.PREF_ROOT, false))
            return new AssetFileDescriptor(openRootFile(Storage.getFile(f), mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        return super.openAssetFile(uri, mode);
    }
}
