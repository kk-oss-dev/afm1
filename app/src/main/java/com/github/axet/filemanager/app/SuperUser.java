package com.github.axet.filemanager.app;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.github.axet.androidlibrary.app.Natives;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
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
    public static final String LSA = BIN_LS + " -AlH {0}"; // -A = all entries except "." and ".." -l = long format -H = follow symlinks
    public static final String LSa = BIN_LS + " -alH {0}"; // -a = all including starting with "." -l = long format -H = follow symlinks

    public static final File DOT = new File(".");
    public static final File DOTDOT = new File("..");

    public static String BIN_SUIO;

    public static void sudoTest(Context context) {
        exitTest();
        binSuio(context);
    }

    public static String binSuio(Context context) {
        return BIN_SUIO = Natives.search(context, "libsuio.so");
    }

    public static boolean toBoolean(String str) throws IOException {
        if (str.equals("true"))
            return true;
        if (str.equals("false"))
            return false;
        throw new IOException("bad input");
    }

    public static ArrayList<File> ls(String opt, File f, FileFilter filter) {
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
                    } catch (NumberFormatException ignore) {
                    }
                    long last = 0;
                    try {
                        last = LSDATE.parse(m.group(6)).getTime();
                    } catch (ParseException ignore) {
                    }
                    String name = m.group(7);
                    File k = new File(name);
                    if (!k.equals(DOTDOT) && (filter == null || filter.accept(k))) {
                        if (perms.startsWith("d")) {
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new Directory(k, last));
                        } else if (perms.startsWith("l")) {
                            String[] ss = name.split("->", 2);
                            name = ss[0].trim();
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new SymLink(k, size, new File(ss[1].trim())));
                        } else {
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
            su.waitFor();
            new Result(cmd, su).must();
        } catch (InterruptedException | IOException e) {
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

    public static ArrayList<File> lsA(File f) { // list
        return ls(f, new FileFilter() {
            @Override
            public boolean accept(File f) {
                return !f.equals(DOT);
            }
        });
    }

    public static ArrayList<File> lsa(File f) { // walk
        return ls(f, null);
    }

    public static ArrayList<File> ls(File f, FileFilter filter) {
        SuIO su = new SuIO();
        try {
            ArrayList<File> ff = new ArrayList<>();
            su.write("lsa", f);
            String type;
            while (!(type = su.readString()).equals("EOF")) {
                long size = Long.valueOf(su.readString());
                long last = Long.valueOf(su.readString());
                String name = su.readString();
                File k = new File(name);
                if (!k.equals(DOTDOT) && (filter == null || filter.accept(k))) {
                    switch (type) {
                        case "d":
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new Directory(k, last));
                            break;
                        case "ld": {
                            String target = su.readString();
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new SymDirLink(k, size, new File(target)));
                            break;
                        }
                        case "lf": {
                            String target = su.readString();
                            if (k.equals(DOT))
                                k = f;
                            else
                                k = new File(f, name);
                            ff.add(new SymLink(k, size, new File(target)));
                            break;
                        }
                        default:
                            if (k.equals(DOT))
                                k = f;
                            if (!f.equals(k)) // ls file return full path, ls dir return relative path
                                k = new File(f, name);
                            ff.add(new NativeFile(k, size, last));
                            break;
                    }
                }
            }
            return new SuIoResult<>(su, ff).value();
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static boolean isDirectory(File f) {
        SuIO su = new SuIO();
        try {
            su.write("isdir", f);
            return new SuIoResult<>(su, toBoolean(su.readString())).value();
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static long length(File f) {
        SuIO su = new SuIO();
        try {
            su.write("length", f);
            return new SuIoResult<>(su, Long.valueOf(su.readString())).value();
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static SuIoResult ln(File target, File file) {
        SuIO su = new SuIO();
        try {
            su.write("ln", target, file);
            return new SuIoResult<>(su);
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static SuIoResult touch(File target, long time) {
        SuIO su = new SuIO();
        try {
            su.write("touch", target, time / 1000);
            return new SuIoResult<>(su);
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static SuIoResult delete(File target) {
        SuIO su = new SuIO();
        try {
            su.write("delete", target);
            return new SuIoResult<>(su);
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static SuIoResult mkdir(File target) {
        SuIO su = new SuIO();
        try {
            su.write("mkdir", target);
            return new SuIoResult<>(su);
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static SuIoResult rename(File f, File t) {
        SuIO su = new SuIO();
        try {
            su.write("rename", f, t);
            return new SuIoResult<>(su);
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

    public static long lastModified(File f) {
        SuIO su = new SuIO();
        try {
            su.write("last", f);
            return new SuIoResult<>(su, Long.valueOf(su.readString())).value();
        } catch (IOException e) {
            throw new ResultCodeError(su.cmd, su.su, e);
        }
    }

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
            ArrayList<File> all = SuperUser.lsA(this);
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

        @Override
        public String toString() { // display name
            return super.toString() + " -> " + target;
        }
    }

    public static class SymDirLink extends SymLink {
        public SymDirLink(File f, long last, File target) {
            super(f, last, target);
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

    public static class SuIoResult<T> extends Result {
        T value;

        public SuIoResult(SuIO su) throws IOException {
            super(su.exit());
        }

        public SuIoResult(SuIO su, T v) throws IOException {
            super(su.exit());
            value = v;
        }

        public T value() {
            return ((SuIoResult<T>) must()).value;
        }
    }

    public static class SuIO {
        public InputStream is;
        public OutputStream os;
        public Commands cmd;
        public Process su;

        public SuIO() {
            try {
                cmd = new Commands(BIN_SUIO + ";" + BIN_EXIT).exit(true);
                su = Runtime.getRuntime().exec(BIN_SU);
                os = new BufferedOutputStream(su.getOutputStream());
                if (cmd.exit && !EXITCODE)
                    SuperUser.writeString(BIN_TRAP + " '" + KILL_SELF + "' ERR" + EOL, os);
                SuperUser.writeString(cmd.build(), os);
                is = new BufferedInputStream(su.getInputStream());
            } catch (IOException e) {
                throw new ResultCodeError(cmd, su, e);
            }
        }

        public void writeString(String str) throws IOException {
            os.write(str.getBytes(Charset.defaultCharset()));
            os.write(0);
            os.flush();
        }

        public void write(Object... oo) throws IOException {
            for (Object o : oo) {
                if (o instanceof String)
                    writeString((String) o);
                else if (o instanceof Long)
                    writeString(Long.toString((Long) o));
                else if (o instanceof Integer)
                    writeString(Integer.toString((Integer) o));
                else if (o instanceof File)
                    writeString(((File) o).getPath());
                else
                    throw new RuntimeException("unknown type");
            }
        }

        public String readString() throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            int c;
            while ((c = is.read()) != 0) {
                if (c == -1)
                    throw new ResultCodeError(cmd, su, new EOFException());
                os.write(c);
            }
            return os.toString();
        }

        public boolean ping() throws IOException {
            write("ping");
            String str = readString();
            return str.equals("pong");
        }

        public void clear() throws IOException {
            IOUtils.copy(su.getInputStream(), new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }

                @Override
                public void write(@NonNull byte[] b, int off, int len) throws IOException {
                }
            });
            IOUtils.copy(su.getErrorStream(), new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }

                @Override
                public void write(@NonNull byte[] b, int off, int len) throws IOException {
                }
            });
        }

        public Result exit() throws IOException {
            try {
                write("exit");
                su.waitFor();
                return new Result(cmd, su);
            } catch (InterruptedException e) {
                return new Result(cmd, su, e);
            } catch (IOException e) {
                return new Result(cmd, su, e);
            }
        }

        public Result alive() {
            try {
                su.exitValue();
                return new Result(cmd, su);
            } catch (IllegalThreadStateException e) {
                return null;
            }
        }

        public void close() {
            su.destroy();
        }
    }

    public static class RandomAccessFile extends com.github.axet.androidlibrary.app.SuperUser.RandomAccessFile {
        public static final String R = "rb";
        public static final String W = "wb"; // open and truncate

        SuIO su;

        public RandomAccessFile(File f, String mode) throws IOException {
            this.su = new SuIO();
            this.su.write("rafopen", f, mode);
            if (mode.equals(R))
                size = Long.valueOf(this.su.readString());
        }

        public RandomAccessFile(File f) throws IOException {
            this(f, R);
        }

        public int read() throws IOException {
            int size = 1;
            long last = offset + size;
            if (last > this.size) {
                size -= last - this.size;
                if (size == 0)
                    return -1;
            }
            su.write("rafread", offset, size);
            int b = su.is.read();
            offset += size;
            return b;
        }

        public int read(byte[] buf, int off, int size) throws IOException {
            long last = offset + size;
            if (last > this.size) {
                size -= last - this.size;
                if (size == 0)
                    return -1;
            }
            su.write("rafread", offset, size);
            long len;
            int read = 0;
            while ((len = su.is.read(buf, off, size)) > 0) {
                off += len;
                offset += len;
                size -= len;
                read += len;
            }
            return read;
        }

        public void write(int b) throws IOException {
            int size = 1;
            su.write("rafwrite", offset, size);
            su.os.write(b);
            su.os.flush();
            offset += size;
        }

        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            su.write("rafwrite", offset, len);
            su.os.write(b, off, len);
            su.os.flush();
            offset += len;
        }

        @Override
        public void close() throws IOException {
            su.write("rafclose");
            su.exit().must();
            su.close();
        }
    }

    public static class FileInputStream extends InputStream {
        RandomAccessFile r;

        public FileInputStream(File f) throws IOException {
            r = new RandomAccessFile(f);
        }

        @Override
        public int read() throws IOException {
            return r.read();
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            return r.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            r.close();
        }
    }

    public static class FileOutputStream extends OutputStream {
        RandomAccessFile r;

        public FileOutputStream(File f) throws IOException {
            r = new RandomAccessFile(f, RandomAccessFile.W);
        }

        @Override
        public void write(int b) throws IOException {
            r.write(b);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            r.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            r.close();
        }
    }
}
