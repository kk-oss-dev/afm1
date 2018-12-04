package com.github.axet.filemanager.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.app.RarSAF;
import com.github.axet.androidlibrary.app.ZipSAF;
import com.github.axet.androidlibrary.services.StorageProvider;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;

import net.lingala.zip4j.core.NativeStorage;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;
import de.innosystec.unrar.rarfile.HostSystem;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static final String TAG = Storage.class.getSimpleName();

    public static final String CONTENTTYPE_ZIP = "application/zip";

    public static HashMap<Uri, ArchiveCache> CACHE = new HashMap<>();

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
        return com.github.axet.androidlibrary.app.Storage.getParent(context, uri);
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

    public static class Nodes extends ArrayList<Node> {
        public Nodes() {
        }

        public Nodes(ArrayList<Node> nn) {
            super(nn);
        }

        public Nodes(ArrayList<Node> nn, boolean dir) {
            for (Node n : nn) {
                if (n.dir == dir) {
                    add(n);
                }
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
                if (n.uri.equals(o)) {
                    return remove(n);
                }
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
                return new ZipInputStreamSafe(zip.getInputStream(h));
            } catch (ZipException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class RarNode extends ArchiveNode {
        public Archive rar;
        public de.innosystec.unrar.rarfile.FileHeader h;

        public static String getRarFileName(de.innosystec.unrar.rarfile.FileHeader header) {
            String s = header.getFileNameW();
            if (s == null || s.isEmpty())
                s = header.getFileNameString();
            if (header.getHostOS().equals(HostSystem.win32))
                s = s.replaceAll("\\\\", "/");
            return s;
        }

        @Override
        public String getPath() {
            String s = getRarFileName(h);
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
    }

    public class ArchiveCache {
        public Uri uri; // archive uri
        public ArrayList<ArchiveNode> all;
    }

    public static class ZipInputStreamSafe extends InputStream {
        ZipInputStream is;

        public ZipInputStreamSafe(ZipInputStream is) {
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            is.close(true);
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
            ArchiveReader a = fromArchive(root);
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
        public ZipReader(Uri u, String p) {
            super(u, p);
        }

        public void read() {
            try {
                ZipFile zip;
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
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        n.uri = uri.buildUpon().appendPath(n.getPath()).build();
                    } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                        n.uri = uri.buildUpon().appendQueryParameter("p", n.getPath()).build();
                    } else {
                        throw new UnknownUri();
                    }
                    n.name = getLast(n.getPath());
                    n.size = h.getUncompressedSize();
                    n.last = h.getLastModFileTime();
                    n.dir = h.isDirectory();
                    n.zip = zip;
                    aa.add(n);
                }
                CACHE.put(uri, this);
                all = aa;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public class RarReader extends ArchiveReader {
        public RarReader(Uri u, String p) {
            super(u, p);
        }

        public void read() {
            try {
                Archive rar;
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
                CACHE.put(uri, this);
                all = aa;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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

    public ArchiveReader fromArchive(Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            final File k = getFile(uri);
            byte[] buf = new byte[1024];
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
                    if (!rel.isEmpty()) {
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
                    if (!rel.isEmpty()) {
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
            if (f.isDirectory())
                return null;
            String t = f.getType();
            if (t.equals(CONTENTTYPE_RAR))
                return cache(new RarReader(u, uri.getQueryParameter("p")));
            if (t.equals(CONTENTTYPE_ZIP))
                return cache(new ZipReader(u, uri.getQueryParameter("p")));
        }
        return null;
    }

    public ArchiveReader cache(ArchiveReader r) {
        ArchiveCache c = CACHE.get(r.uri);
        if (c != null)
            return new ArchiveReader(c, r);
        return r;
    }

    public InputStream open(Uri uri) throws IOException {
        ArchiveReader r = fromArchive(uri);
        if (r != null && !r.path.isEmpty())
            return r.open();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            if (getRoot()) {
                return new SuperUser.FileInputStream(k);
            } else {
                return new FileInputStream(k);
            }
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            return resolver.openInputStream(uri);
        } else {
            throw new UnknownUri();
        }
    }

    public OutputStream open(Uri uri, String name) throws IOException {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            File m = new File(k, name);
            if (getRoot()) {
                return new SuperUser.FileOutputStream(m);
            } else {
                return new FileOutputStream(m);
            }
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri doc = createFile(uri, name);
            return resolver.openOutputStream(doc, "rwt");
        } else {
            throw new UnknownUri();
        }
    }

    public boolean touch(Uri uri, long last) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(uri);
            if (getRoot()) {
                return SuperUser.touch(getSu(), k, last).ok();
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
            File m = new File(k, name);
            return SuperUser.touch(getSu(), m, System.currentTimeMillis()).ok();
        }
        return super.touch(uri, name);
    }

    @Override
    public boolean delete(Uri t) { // default Storage.delete() uses 'rm -rf'
        String s = t.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = Storage.getFile(t);
            if (getRoot()) {
                return SuperUser.delete(getSu(), k).ok();
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
            File k = getFile(to);
            File m = new File(k, name);
            if (SuperUser.mkdir(getSu(), m).ok())
                return Uri.fromFile(m);
        }
        return super.mkdir(to, name);
    }

    @Override
    public Uri rename(Uri f, String t) {
        if (f.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = getFile(f);
            File p = k.getParentFile();
            File m = new File(p, t);
            if (!SuperUser.rename(getSu(), k, m).ok())
                return null;
            return Uri.fromFile(m);
        }
        return super.rename(f, t);
    }

    @Override
    public long getLength(Uri uri) {
        ArchiveReader r = fromArchive(uri);
        if (r != null && !r.path.isEmpty())
            return r.length();
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot())
            return SuperUser.length(getSu(), Storage.getFile(uri));
        return super.getLength(uri);
    }

    @Override
    public ArrayList<Node> list(Uri uri) {
        ArchiveReader r = fromArchive(uri);
        if (r != null)
            return r.list();
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
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
        return super.list(uri);
    }

    @Override
    public ArrayList<Node> walk(Uri root, Uri uri) {
        ArchiveReader a = fromArchive(uri);
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
        return super.walk(root, uri);
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

    @Override
    public String getDisplayName(Uri uri) {
        String d = super.getDisplayName(uri);
        String p = uri.getQueryParameter("p");
        if (p != null)
            d += "/" + p;
        return d;
    }
}
