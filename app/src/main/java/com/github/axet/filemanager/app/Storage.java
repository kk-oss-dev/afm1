package com.github.axet.filemanager.app;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.support.v7.preference.PreferenceManager;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class Storage extends com.github.axet.androidlibrary.app.Storage {

    public static String stripRight(String str, String right) {
        if (str.endsWith(right))
            str = str.substring(0, str.length() - right.length());
        return str;
    }

    public static class Node {
        public Uri uri;
        public String name;
        public boolean dir;
        public long size;
        public long last;

        public Node(Uri uri, String n, boolean dir, long size, long last) {
            this.uri = uri;
            this.name = n;
            this.dir = dir;
            this.size = size;
            this.last = last;
        }

        public Node(File f) {
            this.uri = Uri.fromFile(f);
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
        }

        public Node(DocumentFile f) {
            this.uri = f.getUri();
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
        }

        @TargetApi(21)
        public Node(Uri doc, Cursor cursor) {
            String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
            String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
            name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
            size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
            last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
            uri = DocumentsContract.buildDocumentUriUsingTree(doc, id);
            dir = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
        }

        public String toString() {
            return (dir ? "" : "@") + name;
        }
    }

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

    public boolean touch(Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(uri);
            if (getRoot()) {
                return SuperUser.touch(k).ok();
            } else {
                try {
                    new FileOutputStream(k, true).close();
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            DocumentFile file = getDocumentFile(context, uri);
            if (file.exists()) {
                try {
                    ContentResolver resolver = context.getContentResolver();
                    OutputStream os = resolver.openOutputStream(uri, "wa");
                    os.close();
                    return true;
                } catch (IOException e) {
                    return false;
                }
            } else {
                String ext = getExt(uri);
                String n = getDocumentName(uri);
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                Uri doc = DocumentsContract.createDocument(resolver, uri, mime, n);
                return doc != null;
            }
        } else {
            throw new Storage.UnknownUri();
        }
    }

    @Override
    public boolean delete(Uri t) {
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
        String s = to.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(to);
            File m = new File(k, name);
            if (getRoot()) {
                if (SuperUser.mkdir(m).ok())
                    return Uri.fromFile(m);
            } else {
                if (m.exists() || m.mkdir())
                    return Uri.fromFile(m);
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return DocumentsContract.createDocument(resolver, to, DocumentsContract.Document.MIME_TYPE_DIR, name);
        } else {
            throw new Storage.UnknownUri();
        }
        return null;
    }

    @Override
    public long getLength(Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(uri);
            if (getRoot()) {
                return SuperUser.length(k);
            } else {
                return k.length();
            }
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return getDocumentFile(context, uri).length();
        } else {
            throw new Storage.UnknownUri();
        }
    }

    public ArrayList<Node> list(Uri uri) {
        ArrayList<Node> files = new ArrayList<>();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (getRoot()) {
                ArrayList<File> ff = SuperUser.ls(SuperUser.LSA, Storage.getFile(uri));
                for (File f : ff)
                    files.add(new Storage.Node(f));
            } else {
                File file = Storage.getFile(uri);
                File[] ff = file.listFiles();
                if (ff != null) {
                    for (File f : ff) {
                        files.add(new Storage.Node(f));
                    }
                }
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id;
            if (DocumentsContract.isDocumentUri(getContext(), uri))
                id = DocumentsContract.getDocumentId(uri);
            else
                id = DocumentsContract.getTreeDocumentId(uri);
            Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
            ContentResolver resolver = getContext().getContentResolver();
            Cursor cursor = resolver.query(doc, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext())
                    files.add(new Storage.Node(doc, cursor));
                cursor.close();
            }
        } else {
            throw new Storage.UnknownUri();
        }
        return files;
    }


    public ArrayList<Node> walk(Uri root, Uri uri) {
        ArrayList<Node> files = new ArrayList<>();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            int r = Storage.getFile(root).getPath().length();
            if (getRoot()) {
                ArrayList<File> ff = SuperUser.ls(SuperUser.LSa, Storage.getFile(uri));
                for (File f : ff) {
                    Storage.Node k = new Storage.Node(Uri.fromFile(f), f.getPath().substring(r), f.isDirectory(), f.length(), f.lastModified());
                    files.add(k);
                }
            } else {
                File f = Storage.getFile(uri);
                files.add(new Storage.Node(uri, f.getPath().substring(r), f.isDirectory(), f.length(), f.lastModified()));
                File[] kk = f.listFiles();
                if (kk != null) {
                    for (File k : kk)
                        files.add(new Storage.Node(Uri.fromFile(k), k.getPath().substring(r), k.isDirectory(), k.length(), k.lastModified()));
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id;
            if (DocumentsContract.isDocumentUri(context, root))
                id = DocumentsContract.getDocumentId(root);
            else
                id = DocumentsContract.getTreeDocumentId(root);
            id = stripRight(id, "/"); // sometimes root folder has name '/', sometimes ''.
            int r = id.length();
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    long size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                    long last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                    boolean d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    files.add(new Storage.Node(uri, id.substring(r), d, size, last)); // root
                    if (d) {
                        Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
                        Cursor cursor2 = resolver.query(doc, null, null, null, null);
                        if (cursor2 != null) {
                            while (cursor2.moveToNext()) {
                                id = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                                type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                                size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                                last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                                d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                                files.add(new Storage.Node(DocumentsContract.buildDocumentUriUsingTree(doc, id), id.substring(r), d, size, last)); // root
                            }
                            cursor2.close();
                        }
                    }
                }
                cursor.close();
            }
        } else {
            throw new Storage.UnknownUri();
        }
        return files;
    }
}
