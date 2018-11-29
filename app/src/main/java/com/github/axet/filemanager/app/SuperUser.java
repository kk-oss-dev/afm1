package com.github.axet.filemanager.app;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.github.axet.androidlibrary.app.Natives;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SuperUser extends com.github.axet.androidlibrary.app.SuperUser {
    public static final SimpleDateFormat LSDATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static final String DELETE = BIN_RM + " -r {0}";
    public static final String LSA = BIN_LS + " -AlH {0}";
    public static final String LSa = BIN_LS + " -alH {0}";

    public static final File DOT = new File(".");
    public static final File DOTDOT = new File("..");

    public static class NativeFile extends File {
        long size;
        long last;

        public NativeFile(File f, long size, long last) {
            super(f.getPath());
            this.size = size;
            this.last = last;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean canWrite() {
            return true;
        }

        @Override
        public long length() {
            return size;
        }

        @Override
        public long lastModified() {
            return last;
        }

        @Override
        public boolean delete() {
            return SuperUser.delete(this).ok();
        }

        @Override
        public boolean renameTo(File dest) {
            return SuperUser.rename(this, dest).ok();
        }
    }

    public static class Directory extends File {
        long last;

        public Directory(File f, long last) {
            super(f.getPath());
            this.last = last;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return true;
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public long lastModified() {
            return last;
        }

        @Override
        public boolean delete() {
            return SuperUser.delete(this).ok();
        }

        @Override
        public boolean mkdir() {
            return SuperUser.mkdir(this).ok();
        }

        @Override
        public boolean mkdirs() {
            return SuperUser.mkdirs(this).ok();
        }

        @Override
        public boolean renameTo(File dest) {
            return SuperUser.rename(this, dest).ok();
        }

        @Override
        public File[] listFiles() {
            return listFiles((FileFilter) null);
        }

        @Override
        public File[] listFiles(final FilenameFilter filter) {
            return listFiles(filter == null ? null : new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return filter.accept(pathname.getParentFile(), pathname.getName());
                }
            });
        }

        @Override
        public File[] listFiles(FileFilter filter) {
            ArrayList<File> all = SuperUser.ls(SuperUser.LSA, this);
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

    public static class SymLink extends Directory {
        File target;

        public SymLink(File f, long last, File target) {
            super(f, last);
            this.target = target;
        }

        @Override
        public boolean isDirectory() {
            return false; // false, but target may be
        }

        @Override
        public boolean exists() {
            return true; // symlink exists, but target may not
        }

        public File getTarget() {
            return target;
        }
    }

    public static class VirtualFile extends SuperUser.Directory { // has no information about attrs (size, last, exists)
        boolean exists;

        public VirtualFile(File f) {
            super(f, 0);
            exists = true;
        }

        public VirtualFile(File f, String name) {
            this(new File(f, name));
            exists = SuperUser.exists(this);
        }

        @Override
        public File getParentFile() {
            String p = getParent();
            if (p == null)
                return null;
            return new VirtualFile(new File(p));
        }

        @Override
        public boolean exists() {
            return exists;
        }
    }

    public static ArrayList<File> ls(String opt, File f) {
        ArrayList<File> ff = new ArrayList<>();
        Commands cmd = new Commands(MessageFormat.format(opt, escape(f))).stdout(true).exit(true);
        OutputStream os = null;
        InputStream is = null;
        Process su = null;
        try {
            su = Runtime.getRuntime().exec(BIN_SU);
            os = new BufferedOutputStream(su.getOutputStream());
            writeString(cmd.build(), os);
            writeString(BIN_EXIT + EOL, os);
            is = new BufferedInputStream(su.getInputStream());
            Scanner scanner = new Scanner(is);
            Pattern p = Pattern.compile("^([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+\\s+[^\\s]+)\\s(.*?)$");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    String perms = m.group(1);
                    long size = 0;
                    try {
                        size = Long.valueOf(m.group(5));
                    } catch (NumberFormatException e) {
                    }
                    long last = 0;
                    try {
                        last = LSDATE.parse(m.group(6)).getTime();
                    } catch (ParseException e) {
                    }
                    String name = m.group(7);
                    if (perms.startsWith("d")) {
                        File k = new File(name);
                        if (!k.equals(DOTDOT)) {
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new Directory(k, last));
                        }
                    } else if (perms.startsWith("l")) {
                        String[] ss = name.split("->", 2);
                        name = ss[0].trim();
                        File k = new File(name);
                        if (!k.equals(DOTDOT)) {
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new SymLink(k, size, new File(ss[1].trim())));
                        }
                    } else {
                        File k = new File(name);
                        if (!k.equals(DOTDOT)) {
                            if (k.equals(DOT))
                                k = f;
                            if (!f.equals(k)) // ls file return full path, ls dir return relative path
                                k = new File(f, name);
                            ff.add(new NativeFile(k, size, last));
                        }
                    }
                }
            }
            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (su != null)
                su.destroy();
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                Log.d(TAG, "close exception", e);
            }
            try {
                if (os != null)
                    os.close();
            } catch (IOException e) {
                Log.d(TAG, "close exception", e);
            }
        }
        return ff;
    }

    public static Result delete(File f) {
        return su(DELETE, escape(f));
    }

    public static ArrayList<File> isDirectory(ArrayList<File> ff) {
        Commands cmd = new Commands();
        for (File f : ff)
            cmd.add("[ -d " + escape(f) + " ] && echo " + escape(f));
        Result r = su(cmd.stdout(true));
        ArrayList<File> a = new ArrayList<>();
        Scanner s = new Scanner(r.stdout);
        while (s.hasNextLine())
            a.add(new File(s.nextLine()));
        s.close();
        return a;
    }

    public static class RandomAccessFile extends com.github.axet.androidlibrary.app.SuperUser.RandomAccessFile {
        public RandomAccessFile(Context context, File f) {
            String bin = Natives.search(context, "libraf.so");
            Commands cmd = new Commands(bin + " " + escape(f)).exit(true);
            try {
                final Process su = Runtime.getRuntime().exec(BIN_SU);
                su.getErrorStream().close();
                os = new BufferedOutputStream(su.getOutputStream());
                writeString(cmd.build(), os);
                is = new BufferedInputStream(new InputStream() {
                    InputStream is = su.getInputStream();

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
                        super.close();
                        su.destroy();
                    }
                });
                size = Long.valueOf(readLine().trim());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int read() throws IOException {
            int size = 1;
            writeString(offset + " " + size + EOL, os);
            int b = is.read();
            offset += 1;
            return b;
        }

        public int read(byte[] buf, int off, int size) throws IOException {
            writeString(offset + " " + size + EOL, os);
            long len;
            int read = 0;
            while ((len = is.read(buf, off, size)) > 0) {
                off += len;
                offset += len;
                size -= len;
                read += len;
            }
            return read;
        }
    }
}
