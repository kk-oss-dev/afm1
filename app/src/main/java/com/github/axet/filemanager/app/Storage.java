package com.github.axet.filemanager.app;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaDataSource;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.app.RarSAF;
import com.github.axet.androidlibrary.app.ZipSAF;
import com.github.axet.androidlibrary.services.StorageProvider;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.filemanager.fragments.FilesFragment;

import net.lingala.zip4j.NativeStorage;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static final String TAG = Storage.class.getSimpleName();

    public static final String CONTENTTYPE_ZIP = "application/zip";
    public static final int SAF_RW = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    public static final String ROOT_MEDIA = "/mnt/media_rw"; // /storage - user access, /mnt/media_rw - root access

    public static final HashMap<Uri, ArchiveCache> ARCHIVE_CACHE = new HashMap<>();
    public static final SAFCaches<FilesFragment> SAF_CACHE = new SAFCaches<>();

    SuperUser.SuIO su;

    public static Uri getParent(Context context, Uri uri) {
        String p = uri.getQueryParameter("p");
        if (p != null) {
            p = new File(p).getParent();
            Uri.Builder b = uri.buildUpon();
            b.query("");
            if (p != null)
                b.appendQueryParameter("p", p);
            return b.build();
        }
        return SAF_CACHE.getParent(context, uri);
    }

    public static String getName(Context context, Uri uri) {
        String p = uri.getQueryParameter("p");
        if (p != null)
            return new File(p).getName();
        return com.github.axet.androidlibrary.app.Storage.getName(context, uri);
    }

    public static String getLast(String name) {
        String[] ss = splitPath(name);
        return ss[ss.length - 1];
    }

    public static String getDisplayName(Context context, Uri uri) {
        String d;
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) { // SAF folder for content
            if (uri.getAuthority().startsWith(SAF)) {
                d = DocumentsContract.getTreeDocumentId(uri);
                if (d.endsWith(COLON))
                    d = d.substring(0, d.length() - 1);
                d += CSS;
                if (DocumentsContract.isDocumentUri(context, uri))
                    d += Storage.getDocumentChildPath(uri);
            } else {
                d = DocumentsContract.getTreeDocumentId(uri);
                if (d.endsWith(COLON))
                    d = d.substring(0, d.length() - 1);
                d += CSS;
                d += uri.getLastPathSegment();
            }
        } else if (s.equals(ContentResolver.SCHEME_FILE)) { // full destionation for files
            d = getFile(uri).getPath();
        } else {
            throw new UnknownUri();
        }
        String p = uri.getQueryParameter("p");
        if (p != null)
            d += "/" + p;
        return d;
    }

    @TargetApi(21)
    public static Uri buildTreeDocumentUriRoot(Uri u) { // build tree uri from root uri (content://com.android.externalstorage.documents/root/XXXX-XXXX and vnd.android.document/root to content://com.android.externalstorage.documents/tree/XXXX-XXXX%3A)
        String id = DocumentsContract.getRootId(u);
        return DocumentsContract.buildTreeDocumentUri(u.getAuthority(), id + Storage.COLON);
    }

    public static class Nodes extends ArrayList<Node> {
        public Nodes() {
        }

        public Nodes(ArrayList<Node> nn) {
            super(nn);
        }

        public Nodes(ArrayList<Node> nn, boolean dir) {
            for (Node n : nn) {
                if (n.dir == dir)
                    add(n);
            }
        }

        public boolean contains(Uri o) {
            for (Node n : this) {
                if (n.uri.equals(o))
                    return true;
            }
            return false;
        }

        public int find(Uri u) {
            for (int i = 0; i < size(); i++) {
                Node n = get(i);
                if (n.uri.equals(u))
                    return i;
            }
            return -1;
        }

        public boolean remove(Uri o) {
            for (Node n : this) {
                if (n.uri.equals(o))
                    return remove(n);
            }
            return false;
        }
    }

    public static class SymlinkNode extends Node {
        File symlink;
        boolean symdir;

        public SymlinkNode(Uri uri, String name, long last, File target, boolean symdir) {
            this.uri = uri;
            this.name = name;
            this.last = last;
            this.symlink = target;
            this.symdir = symdir;
        }

        public SymlinkNode(SuperUser.SymLink f) {
            super(f);
            symlink = f.getTarget();
            symdir = f instanceof SuperUser.SymDirLink;
        }

        public boolean isSymDir() {
            return symdir;
        }

        public File getTarget() {
            return symlink;
        }

        @Override
        public String toString() {
            if (symdir)
                return name + " -> " + (symlink.getPath().endsWith(OpenFileDialog.ROOT) ? symlink.getPath() : symlink.getPath() + OpenFileDialog.ROOT);
            else
                return name + " -> " + symlink.getPath();
        }
    }

    public static class VirtualFile extends SuperUser.VirtualFile {
        Storage storage;

        public VirtualFile(Storage storage, File f) {
            super(f);
            this.storage = storage;
        }

        public VirtualFile(Storage storage, File f, String name) {
            super(f, name);
            this.storage = storage;
        }

        @Override
        public File[] listFiles(FileFilter filter) {
            ArrayList<File> all = SuperUser.lsA(storage.getSu(), this);
            if (filter != null) {
                ArrayList<File> ff = new ArrayList<>();
                for (File f : all) {
                    if (filter.accept(f))
                        ff.add(f);
                }
                all = ff;
            }
            return all.toArray(new File[]{});
        }
    }

    public static class ArchiveNode extends Node {
        public String getPath() {
            return null;
        }

        public InputStream open() {
            return null;
        }
    }

    public static class ZipNode extends ArchiveNode {
        public ZipFile zip;
        public FileHeader h;

        @Override
        public String getPath() {
            String s = h.getFileName();
            if (s.startsWith(OpenFileDialog.ROOT))
                s = s.substring(1);
            if (s.endsWith(OpenFileDialog.ROOT))
                s = s.substring(0, s.length() - 1);
            return s;
        }

        @Override
        public InputStream open() {
            try {
                return new ZipSAF.ZipInputStreamSafe(zip.getInputStream(h));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class RarNode extends ArchiveNode {
        public Archive rar;
        public de.innosystec.unrar.rarfile.FileHeader h;

        @Override
        public String getPath() {
            String s = RarSAF.getRarFileName(h);
            if (s == null || s.isEmpty())
                s = h.getFileNameString();
            if (s.startsWith(OpenFileDialog.ROOT))
                s = s.substring(1);
            if (s.endsWith(OpenFileDialog.ROOT))
                s = s.substring(0, s.length() - 1);
            return s;
        }

        @Override
        public InputStream open() {
            try {
                return new ParcelFileDescriptor.AutoCloseInputStream(new StorageProvider.ParcelInputStream() {
                    @Override
                    public void copy(OutputStream os) throws IOException {
                        try {
                            rar.extractFile(h, os);
                        } catch (RarException e) {
                            throw new IOException(e);
                        }
                    }

                    @Override
                    public long getStatSize() {
                        return h.getFullUnpackSize();
                    }

                    @Override
                    public void close() throws IOException {
                        super.close();
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void close() {
            try {
                rar.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public class ArchiveCache {
        public Uri uri; // archive uri
        public ArrayList<ArchiveNode> all;

        public void close() {
        }
    }

    public class ArchiveReader extends ArchiveCache {
        public String path; // inner path

        public ArchiveReader(Uri u, String p) {
            uri = u;
            if (p == null)
                p = "";
            path = p;
        }

        public ArchiveReader(ArchiveCache c, ArchiveReader r) {
            uri = c.uri;
            all = c.all;
            path = r.path;
        }

        public boolean isDirectory() {
            if (all == null)
                read();
            ArchiveNode n = find();
            if (n != null)
                return n.dir;
            return true;
        }

        public InputStream open() {
            if (all == null)
                read();
            final ArchiveNode n = find();
            if (n == null)
                return null;
            return n.open();
        }

        public ArchiveNode find() {
            for (ArchiveNode n : all) {
                if (n.getPath().equals(path))
                    return n;
            }
            return null;
        }

        public long length() {
            if (all == null)
                read();
            ArchiveNode n = find();
            if (n == null)
                return -1;
            return n.size;
        }

        public void read() {
        }

        public ArrayList<Node> list() {
            if (all == null)
                read();
            ArrayList<Node> nn = new ArrayList<>();
            for (ArchiveNode n : all) {
                String p = n.getPath();
                String r = relative(path, p);
                if (r != null && !r.isEmpty() && splitPath(r).length == 1)
                    nn.add(n);
            }
            return nn;
        }

        public ArrayList<Node> walk(Uri root) {
            if (all == null)
                read();
            ArchiveReader a = fromArchive(root, true);
            ArrayList<Node> nn = new ArrayList<>();
            for (ArchiveNode n : all) {
                String p = n.getPath();
                String r = relative(path, p);
                if (r != null && splitPath(r).length == 1) {
                    Node k = new Node();
                    k.size = n.size;
                    k.name = relative(a.path, p);
                    k.dir = n.dir;
                    k.last = n.last;
                    k.uri = n.uri;
                    nn.add(k);
                }
            }
            return nn;
        }
    }

    public class ZipReader extends ArchiveReader {
        ZipFile zip;

        public ZipReader(Uri u, String p) {
            super(u, p);
        }

        public void read() {
            try {
                String s = uri.getScheme();
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    if (getRoot()) {
                        File f = Storage.getFile(uri);
                        zip = new ZipFile(new ZipSu(getSu(), f));
                    } else {
                        File f = Storage.getFile(uri);
                        zip = new ZipFile(new NativeStorage(f));
                    }
                } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    zip = new ZipFile(new ZipSAF(context, Storage.getDocumentTreeUri(uri), uri));
                } else {
                    throw new UnknownUri();
                }
                ArrayList<ArchiveNode> aa = new ArrayList<>();
                List list = zip.getFileHeaders();
                for (Object o : list) {
                    final FileHeader h = (FileHeader) o;
                    ZipNode n = new ZipNode();
                    n.h = h;
                    if (s.equals(ContentResolver.SCHEME_FILE))
                        n.uri = uri.buildUpon().appendPath(n.getPath()).build();
                    else if (s.equals(ContentResolver.SCHEME_CONTENT))
                        n.uri = uri.buildUpon().appendQueryParameter("p", n.getPath()).build();
                    else
                        throw new UnknownUri();
                    n.name = getLast(n.getPath());
                    n.size = h.getUncompressedSize();
                    n.last = h.getLastModifiedTime();
                    n.dir = h.isDirectory();
                    n.zip = zip;
                    aa.add(n);
                }
                ARCHIVE_CACHE.put(uri, this);
                all = aa;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            super.close();
        }
    }

    public class RarReader extends ArchiveReader {
        Archive rar;

        public RarReader(Uri u, String p) {
            super(u, p);
        }

        public void read() {
            try {
                String s = uri.getScheme();
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    if (getRoot()) {
                        File f = Storage.getFile(uri);
                        rar = new Archive(new RarSu(getSu(), f));
                    } else {
                        File f = Storage.getFile(uri);
                        rar = new Archive(new de.innosystec.unrar.NativeStorage(f));
                    }
                } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    rar = new Archive(new RarSAF(context, Storage.getDocumentTreeUri(uri), uri));
                } else {
                    throw new UnknownUri();
                }
                ArrayList<ArchiveNode> aa = new ArrayList<>();
                List list = rar.getFileHeaders();
                for (Object o : list) {
                    final de.innosystec.unrar.rarfile.FileHeader h = (de.innosystec.unrar.rarfile.FileHeader) o;
                    RarNode n = new RarNode();
                    n.h = h;
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        n.uri = uri.buildUpon().appendPath(n.getPath()).build();
                    } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                        n.uri = uri.buildUpon().appendQueryParameter("p", n.getPath()).build();
                    } else {
                        throw new UnknownUri();
                    }
                    n.name = getLast(n.getPath());
                    n.size = h.getFullUnpackSize();
                    n.last = h.getMTime().getTime();
                    n.dir = h.isDirectory();
                    n.rar = rar;
                    aa.add(n);
                }
                ARCHIVE_CACHE.put(uri, this);
                all = aa;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            try {
                if (rar != null) {
                    rar.close();
                    rar = null;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class UriOutputStream {
        public Uri uri;
        public OutputStream os;

        public UriOutputStream(Uri u, OutputStream os) {
            this.uri = u;
            this.os = os;
        }

        public UriOutputStream(File f, OutputStream os) {
            this.uri = Uri.fromFile(f);
            this.os = os;
        }
    }

    public Storage(Context context) {
        super(context);
    }

    public boolean getRoot() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        return shared.getBoolean(FilesApplication.PREF_ROOT, false);
    }

    public SuperUser.SuIO getSu() {
        if (su == null)
            su = new SuperUser.SuIO();
        if (!su.valid()) {
            closeSu();
            su = new SuperUser.SuIO();
        }
        su.clear();
        if (!su.valid()) {
            closeSu();
            su = new SuperUser.SuIO();
        }
        return su;
    }

    public void closeSu() {
        try {
            if (su != null) {
                su.exit();
                su.close();
                su = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "close", e);
            su.close();
            su = null;
        }
    }

    public File getStorageTrash() {
        File f = Environment.getExternalStorageDirectory();
        if (f == null)
            return null;
        return new File(f, ".trash");
    }

    public File getExternalTrash() {
        File f = context.getExternalCacheDir();
        if (f == null)
            return null;
        return new File(f, "trash");
    }

    public File getLocalTrash() {
        File f = context.getCacheDir();
        if (f == null)
            return null;
        return new File(f, "trash");
    }

    public File getTrash() {
        if (permitted(context, PERMISSIONS_RW)) {
            return getStorageTrash();
        } else {
            if (getRoot())
                return FilesApplication.getLocalTmp();
            else {
                File f = getExternalTrash();
                if (f == null)
                    f = getLocalTrash();
                return f;
            }
        }
    }

    public ArchiveReader fromArchive(Uri uri, boolean root) { // root - open archive root 'file.zip/' or null
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            final File k = getFile(uri);
            byte[] buf = new byte[FileTypeDetector.BUF_SIZE];
            FileTypeDetector.FileRar rar = new FileTypeDetector.FileRar();
            FileTypeDetector.FileZip zip = new FileTypeDetector.FileZip();
            if (getRoot()) {
                try {
                    File p = k;
                    while (p != null && !SuperUser.exists(getSu(), p))
                        p = p.getParentFile();
                    if (p == null || SuperUser.isDirectory(getSu(), p))
                        return null;
                    String rel = relative(p.getPath(), k.getPath());
                    if (root || !rel.isEmpty()) {
                        InputStream is = new SuperUser.FileInputStream(p);
                        int len = is.read(buf);
                        if (len > 0) {
                            rar.write(buf, 0, len);
                            zip.write(buf, 0, len);
                        }
                        is.close();
                        if (rar.done && rar.detected)
                            return cache(new RarReader(Uri.fromFile(p), rel));
                        if (zip.done && zip.detected)
                            return cache(new ZipReader(Uri.fromFile(p), rel));
                    }
                } catch (IOException e) {
                    return null;
                }
            } else {
                try {
                    File p = k;
                    while (p != null && !p.exists())
                        p = p.getParentFile();
                    if (p == null || p.isDirectory())
                        return null;
                    String rel = relative(p.getPath(), k.getPath());
                    if (root || !rel.isEmpty()) {
                        FileInputStream is = new FileInputStream(p);
                        int len = is.read(buf);
                        if (len > 0) {
                            rar.write(buf, 0, len);
                            zip.write(buf, 0, len);
                        }
                        is.close();
                        if (rar.done && rar.detected)
                            return cache(new RarReader(Uri.fromFile(p), rel));
                        if (zip.done && zip.detected)
                            return cache(new ZipReader(Uri.fromFile(p), rel));
                    }
                } catch (IOException e) {
                    return null;
                }
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            if (!DocumentsContract.isDocumentUri(context, uri))
                return null;
            Uri u = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
            DocumentFile f = DocumentFile.fromSingleUri(context, u);
            if (!f.exists() || f.isDirectory())
                return null;
            String t = f.getType();
            String rel = uri.getQueryParameter("p");
            if (t.equals(CONTENTTYPE_XRAR) || t.equals(CONTENTTYPE_RAR))
                return cache(new RarReader(u, rel));
            if (t.equals(CONTENTTYPE_ZIP))
                return cache(new ZipReader(u, rel));
        }
        return null;
    }

    public ArchiveReader cache(ArchiveReader r) {
        ArchiveCache c = ARCHIVE_CACHE.get(r.uri);
        if (c != null)
            return new ArchiveReader(c, r);
        return r;
    }

    public InputStream open(Uri uri) throws IOException {
        ArchiveReader r = fromArchive(uri, false);
        if (r != null)
            return r.open();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            if (getRoot())
                return new SuperUser.FileInputStream(k);
            else
                return new FileInputStream(k);
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return resolver.openInputStream(uri);
        } else {
            throw new UnknownUri();
        }
    }

    public UriOutputStream open(Uri uri, String name) throws IOException {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            File m = new File(k, name);
            if (getRoot())
                return new UriOutputStream(m, new SuperUser.FileOutputStream(m));
            else
                return new UriOutputStream(m, new FileOutputStream(m));
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri doc = createDocumentFile(context, uri, name);
            return new UriOutputStream(doc, resolver.openOutputStream(doc, "rwt"));
        } else {
            throw new UnknownUri();
        }
    }

    public UriOutputStream write(Uri uri) throws IOException {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            if (getRoot())
                return new UriOutputStream(k, new SuperUser.FileOutputStream(k));
            else
                return new UriOutputStream(k, new FileOutputStream(k));
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return new UriOutputStream(uri, resolver.openOutputStream(uri, "rwt"));
        } else {
            throw new UnknownUri();
        }
    }

    public boolean touch(Uri uri, long last) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(uri);
            if (getRoot())
                return SuperUser.touch(getSu(), k, last).ok();
            else
                return k.setLastModified(last); // not working for most devices, requiring root
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return false; // not supported operation for SAF
        } else {
            throw new Storage.UnknownUri();
        }
    }

    public Uri touch(Uri uri, String name) {
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = Storage.getFile(uri);
            File m = new File(k, name);
            if (SuperUser.touch(getSu(), m, System.currentTimeMillis()).ok())
                return uri;
            return null;
        }
        return super.touch(context, uri, name);
    }

    public boolean delete(Uri t) { // default Storage.delete() uses 'rm -rf'
        String s = t.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(t);
            if (getRoot())
                return SuperUser.delete(getSu(), k).ok();
            else
                return k.delete();
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            DocumentFile f = DocumentFile.fromSingleUri(context, t);
            return f.delete();
        } else {
            throw new UnknownUri();
        }
    }

    public Uri mkdir(Uri to, String name) {
        if (to.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = getFile(to);
            File m = new File(k, name);
            if (SuperUser.mkdir(getSu(), m).ok())
                return Uri.fromFile(m);
        }
        return super.mkdir(context, to, name);
    }

    public Uri rename(Uri f, String t) {
        if (f.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = getFile(f);
            File p = k.getParentFile();
            File m = new File(p, t);
            if (!SuperUser.rename(getSu(), k, m).ok())
                return null;
            return Uri.fromFile(m);
        }
        return super.rename(context, f, t);
    }

    public long getLength(Uri uri) {
        ArchiveReader r = fromArchive(uri, false);
        if (r != null)
            return r.length();
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot())
            return SuperUser.length(getSu(), Storage.getFile(uri));
        return super.getLength(context, uri);
    }

    public ArrayList<Node> list(Uri uri) {
        ArchiveReader r = fromArchive(uri, true);
        if (r != null)
            return r.list();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.lsA(getSu(), Storage.getFile(uri));
            for (File f : ff) {
                if (f instanceof SuperUser.SymLink)
                    files.add(new SymlinkNode((SuperUser.SymLink) f));
                else
                    files.add(new Node(f));
            }
            return files;
        }
        return super.list(context, uri);
    }

    public ArrayList<Node> walk(Uri root, Uri uri) {
        ArchiveReader a = fromArchive(uri, true);
        if (a != null)
            return a.walk(root);
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File r = Storage.getFile(root);
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.lsa(getSu(), Storage.getFile(uri));
            for (File f : ff) {
                if (f instanceof SuperUser.SymLink)
                    files.add(new SymlinkNode(Uri.fromFile(f), relative(r, f).getPath(), f.lastModified(), ((SuperUser.SymLink) f).getTarget(), f instanceof SuperUser.SymDirLink));
                else
                    files.add(new Node(Uri.fromFile(f), relative(r, f).getPath(), f.isDirectory(), f.length(), f.lastModified()));
            }
            return files;
        }
        return super.walk(context, root, uri);
    }

    public boolean symlink(SymlinkNode f, Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (!getRoot())
                return false; // users not allowed to create symlinks
            File k = getFile(uri);
            File m = new File(k, f.name);
            return SuperUser.ln(getSu(), f.getTarget(), m).ok();
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return false;
        } else {
            throw new UnknownUri();
        }
    }

    public boolean mv(Uri f, Uri tp, String tn) {
        String s = tp.getScheme(); // target 's'
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            s = f.getScheme(); // source 's'
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(tp);
                File mf = Storage.getFile(f);
                File mt = new File(k, tn);
                if (getRoot()) {
                    if (SuperUser.rename(getSu(), mf, mt).ok())
                        return true;
                } else {
                    if (mf.renameTo(mt))
                        return true;
                }
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            s = f.getScheme(); // source 's'
            if (s.equals(ContentResolver.SCHEME_CONTENT) && tp.getAuthority().startsWith(SAF) && f.getAuthority().startsWith(SAF)) {
                try {
                    if (Build.VERSION.SDK_INT >= 24 && DocumentsContract.moveDocument(resolver, f, Storage.getDocumentParent(context, f), tp) != null) // moveDocument api24+
                        return true;
                } catch (RuntimeException | FileNotFoundException e) { // IllegalStateException: "Failed to move"
                }
            }
        } else {
            throw new Storage.UnknownUri();
        }
        return false;
    }

    public boolean ejected(Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File f = getFile(uri);
            if (!SuperUser.exists(getSu(), f))
                return true; // ejected
            return false;
        }
        return super.ejected(context, uri);
    }

    public Bitmap createVideoThumbnail(Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= 10) {
            final ArchiveReader r = fromArchive(uri, false);
            if (r != null) { // creating video thumbnails from archives only API23+
                if (Build.VERSION.SDK_INT >= 23) {
                    final InputStream is = r.open();
                    MediaDataSource source = new MediaDataSource() { // API23
                        CacheImagesAdapter.SeekInputStream sis = new CacheImagesAdapter.SeekInputStream(is);

                        @Override
                        public void close() throws IOException {
                            if (sis != null) {
                                sis.close();
                                sis = null;
                            }
                        }

                        @Override
                        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
                            sis.seek(position);
                            return sis.read(buffer, offset, size);
                        }

                        @Override
                        public long getSize() throws IOException {
                            return r.length();
                        }
                    };
                    return CacheImagesAdapter.createVideoThumbnail(source);
                } else {
                    return null; // below API23 unable to create thumbmails for archived videos
                }
            }
            String s = uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE) && getRoot()) {
                final File f = Storage.getFile(uri);
                if (Build.VERSION.SDK_INT >= 23) {
                    MediaDataSource source = new MediaDataSource() { // API23
                        SuperUser.RandomAccessFile raf = new SuperUser.RandomAccessFile(f);

                        @Override
                        public void close() throws IOException {
                            try {
                                if (raf != null) {
                                    raf.close();
                                    raf = null;
                                }
                            } catch (IOException ignore) { // native code crashed on exception
                            }
                        }

                        @Override
                        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
                            raf.seek(position);
                            return raf.read(buffer, offset, size);
                        }

                        @Override
                        public long getSize() throws IOException {
                            return raf.getSize();
                        }
                    };
                    return CacheImagesAdapter.createVideoThumbnail(source);
                } else {
                    return null; // below API23 with Root browser we unable to create thumbnails for videos
                }
            }
            return CacheImagesAdapter.createVideoThumbnail(getContext(), uri);
        }
        return null;
    }


    public Bitmap createAudioThumbnail(Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= 10) {
            final ArchiveReader r = fromArchive(uri, false);
            if (r != null) { // creating video thumbnails from archives only API23+
                if (Build.VERSION.SDK_INT >= 23) {
                    final InputStream is = r.open();
                    MediaDataSource source = new MediaDataSource() { // API23
                        CacheImagesAdapter.SeekInputStream sis = new CacheImagesAdapter.SeekInputStream(is);

                        @Override
                        public void close() throws IOException {
                            if (sis != null) {
                                sis.close();
                                sis = null;
                            }
                        }

                        @Override
                        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
                            sis.seek(position);
                            return sis.read(buffer, offset, size);
                        }

                        @Override
                        public long getSize() throws IOException {
                            return r.length();
                        }
                    };
                    return CacheImagesAdapter.createAudioThumbnail(source);
                } else {
                    return null; // below API23 unable to create thumbmails for archived audios
                }
            }
            String s = uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE) && getRoot()) {
                final File f = com.github.axet.androidlibrary.app.Storage.getFile(uri);
                if (Build.VERSION.SDK_INT >= 23) {
                    MediaDataSource source = new MediaDataSource() { // API23
                        SuperUser.RandomAccessFile raf = new SuperUser.RandomAccessFile(f);

                        @Override
                        public void close() throws IOException {
                            try {
                                if (raf != null) {
                                    raf.close();
                                    raf = null;
                                }
                            } catch (IOException e) { // native code cashed on excepion
                                Log.d(TAG, "close exception", e);
                            } catch (RuntimeException e) {
                                Log.d(TAG, "close exception", e);
                            }
                        }

                        @Override
                        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
                            try {
                                raf.seek(position);
                                return raf.read(buffer, offset, size);
                            } catch (IOException e) {
                                throw e;
                            } catch (RuntimeException e) { // native code cashed on excepion
                                throw new IOException(e);
                            }
                        }

                        @Override
                        public long getSize() throws IOException {
                            return raf.getSize();
                        }
                    };
                    return CacheImagesAdapter.createAudioThumbnail(source);
                } else {
                    return null; // below API23 with Root browser we unable to create thumbnails for videos
                }
            }
            return CacheImagesAdapter.createAudioThumbnail(getContext(), uri);
        }
        return null;
    }
}
