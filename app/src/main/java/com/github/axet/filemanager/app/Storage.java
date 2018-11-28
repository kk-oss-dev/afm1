package com.github.axet.filemanager.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.app.ZipSAF;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.filemanager.fragments.FilesFragment;

import net.lingala.zip4j.core.NativeStorage;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class Storage extends com.github.axet.androidlibrary.app.Storage {

    public static final String CONTENTTYPE_ZIP = "application/zip";

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

    public static class SymlinkNode extends Node {
        File symlink;
        Boolean symdir = null;

        public SymlinkNode(Uri uri, String name, long last, File target) {
            this.uri = uri;
            this.name = name;
            this.last = last;
            this.symlink = target;
        }

        public SymlinkNode(SuperUser.SymLink f) {
            super(f);
            symlink = f.getTarget();
        }

        public boolean isSymDir() {
            if (symdir == null)
                symdir = SuperUser.isDirectory(symlink);
            return symdir;
        }

        public void setSymDir(boolean b) {
            symdir = b;
        }

        public File getTarget() {
            return symlink;
        }
    }

    public static class ZipNode extends Node {
        public ZipFile zip;
        public FileHeader h;
    }

    public class ArchiveCache {
        public Uri uri; // archive uri
        public ArrayList<Node> all;
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
            Node n = find();
            if (n != null)
                return n.dir;
            return true;
        }

        public InputStream open() {
            if (all == null)
                read();
            final Node n = find();
            if (n == null)
                return null;
            try {
                return new ZipInputStreamSafe(((ZipNode) n).zip.getInputStream(((ZipNode) n).h));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Node find() {
            for (Node n : all) {
                if (((ZipNode) n).h.getFileName().equals(path))
                    return n;
            }
            return null;
        }

        public long length() {
            if (all == null)
                read();
            Node n = find();
            if (n == null)
                return -1;
            return n.size;
        }

        public void read() {
            try {
                ZipFile zip;
                String s = uri.getScheme();
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    if (getRoot()) {
                        File f = Storage.getFile(uri);
                        zip = new ZipFile(new ZipSu(f));
                    } else {
                        File f = Storage.getFile(uri);
                        zip = new ZipFile(new NativeStorage(f));
                    }
                } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                    zip = new ZipFile(new ZipSAF(context, Storage.getDocumentTreeUri(uri), uri));
                } else {
                    throw new UnknownUri();
                }
                ArrayList<Node> aa = new ArrayList<>();
                List list = zip.getFileHeaders();
                for (Object o : list) {
                    final FileHeader h = (FileHeader) o;
                    ZipNode n = new ZipNode();
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        n.uri = uri.buildUpon().appendPath(h.getFileName()).build();
                    } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                        n.uri = uri.buildUpon().appendQueryParameter("p", h.getFileName()).build();
                    } else {
                        throw new UnknownUri();
                    }
                    n.name = new File(h.getFileName()).getName();
                    n.size = h.getUncompressedSize();
                    n.last = h.getLastModFileTime();
                    n.dir = h.isDirectory();
                    n.zip = zip;
                    n.h = h;
                    aa.add(n);
                }
                all = aa;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public ArrayList<Node> list() {
            if (all == null)
                read();
            ArrayList<Node> nn = new ArrayList<>();
            for (Node n : all) {
                if (list(n))
                    nn.add(n);
            }
            return nn;
        }

        public boolean list(Node n) {
            String p = ((ZipNode) n).h.getFileName();
            if (p.startsWith(OpenFileDialog.ROOT))
                p = p.substring(1);
            String r = relative(path, p);
            if (r != null && !r.isEmpty() && FilesFragment.splitPath(r).length == 1)
                return true;
            return false;
        }

        public ArrayList<Node> walk(Uri root) {
            return null;
        }
    }

    public Storage(Context context) {
        super(context);
    }

    public boolean getRoot() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        return shared.getBoolean(FilesApplication.PREF_ROOT, false);
    }

    public ArchiveReader fromArchive(Uri uri) {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File k = getFile(uri);
            byte[] buf = new byte[1024];
            FileTypeDetector.FileRar rar = new FileTypeDetector.FileRar();
            FileTypeDetector.FileZip zip = new FileTypeDetector.FileZip();
            if (getRoot()) {
                File p = k;
                while (p != null && !p.exists())
                    p = p.getParentFile();
                if (p == null || SuperUser.isDirectory(p))
                    return null;
                try {
                    InputStream is = SuperUser.cat(p);
                    int len = is.read(buf);
                    if (len > 0) {
                        rar.write(buf, 0, len);
                        zip.write(buf, 0, len);
                    }
                    is.close();
                    if (rar.done && rar.detected || zip.done && zip.detected)
                        return cache(new ArchiveReader(Uri.fromFile(p), relative(p.getPath(), k.getPath())));
                } catch (IOException e) {
                    return null;
                }
            } else {
                File p = k;
                while (p != null && !p.exists())
                    p = p.getParentFile();
                if (p == null || p.isDirectory())
                    return null;
                try {
                    FileInputStream is = new FileInputStream(p);
                    int len = is.read(buf);
                    if (len > 0) {
                        rar.write(buf, 0, len);
                        zip.write(buf, 0, len);
                    }
                    is.close();
                    if (rar.done && rar.detected || zip.done && zip.detected)
                        return cache(new ArchiveReader(Uri.fromFile(p), relative(p.getPath(), k.getPath())));
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
            if (t.equals(CONTENTTYPE_RAR) || t.equals(CONTENTTYPE_ZIP))
                return cache(new ArchiveReader(u, uri.getQueryParameter("p")));
        }
        return null;
    }

    public ArchiveReader cache(ArchiveReader r) {
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
                return SuperUser.cat(k);
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
                return SuperUser.open(m);
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
            File k = getFile(to);
            File m = new File(k, name);
            if (SuperUser.mkdir(m).ok())
                return Uri.fromFile(m);
        }
        return super.mkdir(to, name);
    }

    public Uri mkdirs(Uri to, String name) {
        String s = to.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            if (getRoot()) {
                File k = getFile(to);
                File m = new File(k, name);
                if (SuperUser.mkdirs(m).ok())
                    return Uri.fromFile(m);
            } else {
                File k = getFile(to);
                File m = new File(k, name);
                if (m.exists() || m.mkdirs())
                    return Uri.fromFile(m);
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            return createFolder(to, name);
        } else {
            throw new UnknownUri();
        }
        return null;
    }

    @Override
    public Uri rename(Uri f, String t) {
        if (f.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            File k = getFile(f);
            File p = k.getParentFile();
            File m = new File(p, t);
            if (!SuperUser.rename(k, m).ok())
                return null;
            return Uri.fromFile(m);
        }
        return super.rename(f, t);
    }

    @Override
    public long getLength(Uri uri) {
        ArchiveReader r = fromArchive(uri);
        if (r != null)
            return r.length();
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            return SuperUser.length(Storage.getFile(uri));
        }
        return super.getLength(uri);
    }

    @Override
    public ArrayList<Node> list(Uri uri) {
        ArchiveReader r = fromArchive(uri);
        if (r != null)
            return r.list();
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE) && getRoot()) {
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.ls(SuperUser.LSA, Storage.getFile(uri));
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
            int r = Storage.getFile(root).getPath().length();
            ArrayList<Node> files = new ArrayList<>();
            ArrayList<File> ff = SuperUser.ls(SuperUser.LSa, Storage.getFile(uri));
            for (File f : ff) {
                if (f instanceof SuperUser.SymLink)
                    files.add(new SymlinkNode(Uri.fromFile(f), f.getPath().substring(r), f.lastModified(), ((SuperUser.SymLink) f).getTarget()));
                else
                    files.add(new Node(Uri.fromFile(f), f.getPath().substring(r), f.isDirectory(), f.length(), f.lastModified()));
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
            return SuperUser.ln(f.getTarget(), m).ok();
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
