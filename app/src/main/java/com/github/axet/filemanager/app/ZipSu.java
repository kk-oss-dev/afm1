package com.github.axet.filemanager.app;

import android.content.Context;

import net.lingala.zip4j.core.NativeFile;
import net.lingala.zip4j.core.NativeStorage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class ZipSu extends NativeStorage {
    ZipSu parent;

    public static class SuFile extends NativeFile {
        SuperUser.RandomAccessFile r;

        public SuFile(File f) throws IOException {
            r = new SuperUser.RandomAccessFile(f);
        }

        @Override
        public long length() throws IOException {
            return r.getSize();
        }

        @Override
        public void seek(long s) throws IOException {
            r.seek(s);
        }

        @Override
        public void readFully(byte[] buf, int off, int len) throws IOException {
            int r = read(buf, off, len);
            if (r != len)
                throw new IOException("bad read");
        }

        @Override
        public int read(byte[] buf) throws IOException {
            return read(buf, 0, buf.length);
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            return r.read(buf, off, len);
        }

        @Override
        public long getFilePointer() throws IOException {
            return r.getPosition();
        }

        @Override
        public void close() throws IOException {
            if (r != null) {
                r.close();
                r = null;
            }
        }

        @Override
        public void write(byte[] buf) throws IOException {
            write(buf, 0, buf.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException("unsupported");
        }
    }

    public ZipSu(File f) {
        super(f);
    }

    public ZipSu(ZipSu parent, File f) {
        super(f);
        this.parent = parent;
    }

    public ZipSu(ZipSu v) {
        super(v.f);
        parent = v.parent;
    }

    @Override
    public SuFile read() throws FileNotFoundException {
        try {
            return new SuFile(f);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public SuFile write() throws FileNotFoundException {
        throw new FileNotFoundException("not supported");
    }

    @Override
    public NativeStorage open(String name) {
        return new ZipSu(this, new File(f, name));
    }

    @Override
    public boolean exists() {
        return SuperUser.exists(f);
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public NativeStorage getParent() {
        return parent;
    }

    @Override
    public String getName() {
        return f.getName();
    }

    @Override
    public boolean isDirectory() {
        return SuperUser.isDirectory(f);
    }

    @Override
    public long lastModified() {
        return SuperUser.lastModified(f);
    }

    @Override
    public long length() {
        return SuperUser.length(f);
    }

    @Override
    public boolean renameTo(NativeStorage t) {
        return SuperUser.rename(f, ((ZipSu) t).f).ok();
    }

    @Override
    public void setLastModified(long l) {
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public boolean mkdirs() {
        return false;
    }

    @Override
    public boolean delete() {
        return SuperUser.delete(f).ok();
    }

    @Override
    public NativeStorage[] listFiles() {
        ArrayList<File> ff = SuperUser.lsA(f);
        NativeStorage[] nn = new NativeStorage[ff.size()];
        for (int i = 0; i < ff.size(); i++) {
            nn[i++] = new ZipSu(this, f);
        }
        return nn;
    }

    @Override
    public String getPath() {
        return f.getPath();
    }
}
