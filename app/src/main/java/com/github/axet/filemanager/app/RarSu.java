package com.github.axet.filemanager.app;

import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import de.innosystec.unrar.NativeFile;
import de.innosystec.unrar.NativeStorage;

public class RarSu extends NativeStorage {
    Context context;
    RarSu parent;

    public static class SuFile extends NativeFile {
        SuperUser.RandomAccessFile r;

        public SuFile(Context context, File f) throws FileNotFoundException {
            r = new SuperUser.RandomAccessFile(context, f);
        }

        @Override
        public void setPosition(long s) throws IOException {
            r.seek(s);
        }

        @Override
        public long getPosition() throws IOException {
            return r.getPosition();
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            return r.read(buf, off, len);
        }

        @Override
        public int readFully(byte[] buf, int len) throws IOException {
            return read(buf, 0, len);
        }

        @Override
        public int read() throws IOException {
            return r.read();
        }

        @Override
        public void close() throws IOException {
            if (r != null) {
                r.close();
                r = null;
            }
        }
    }

    public RarSu(Context context, File f) {
        super(f);
        this.context = context;
    }

    public RarSu(Context context, RarSu parent, File f) {
        super(f);
        this.context = context;
        this.parent = parent;
    }

    public RarSu(RarSu v) {
        super(v.f);
        context = v.context;
        parent = v.parent;
    }

    @Override
    public SuFile read() throws FileNotFoundException {
        return new SuFile(context, f);
    }

    @Override
    public NativeStorage open(String name) {
        return new RarSu(context, this, new File(f, name));
    }

    @Override
    public boolean exists() {
        return SuperUser.exists(f);
    }

    @Override
    public NativeStorage getParent() {
        return parent;
    }

    @Override
    public long length() {
        return SuperUser.length(f);
    }

    @Override
    public String getPath() {
        return f.getPath();
    }
}
