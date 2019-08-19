package com.github.axet.filemanager.app;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.github.axet.androidlibrary.app.Natives;
import com.github.axet.androidlibrary.services.StorageProvider;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;

public class SuperUser extends com.github.axet.androidlibrary.app.SuperUser {
    public static String BIN_SUIO;

    public static boolean isCPU64() {
        return Build.CPU_ABI.equals("arm64-v8a") || Build.CPU_ABI.equals("x86_64");
    }

    public static boolean sudoTest(Context context) {
        trapTest();
        exitTest();
        return binSuio(context) != null;
    }

    public static String binSuio(Context context) {
        if (Build.VERSION.SDK_INT >= 16 || isCPU64()) // cmake ANDROID_PIE
            return BIN_SUIO = Natives.search(context, "libsuio.so");
        else
            return BIN_SUIO = Natives.search(context, "libsuio-nopie.so");
    }

    public static void skipAll(InputStream is) throws IOException {
        int a;
        while ((a = is.available()) > 0)
            IOUtils.skip(is, a);
    }

    public static boolean toBoolean(String str) throws IOException {
        if (str.equals("true"))
            return true;
        if (str.equals("false"))
            return false;
        throw new IOException("bad input");
    }

    public static ArrayList<File> lsA(SuIO su, File f) { // list
        return ls(su, f, new FileFilter() {
            @Override
            public boolean accept(File f) {
                return !f.equals(DOT);
            }
        });
    }

    public static ArrayList<File> lsa(SuIO su, File f) { // walk
        return ls(su, f, null);
    }

    public static ArrayList<File> ls(SuIO su, File f, FileFilter filter) {
        try {
            ArrayList<File> ff = new ArrayList<>();
            su.write("lsa", f);
            String type;
            while (!(type = su.readString()).equals("EOF")) {
                final long size = Long.valueOf(su.readString());
                final long last = Long.valueOf(su.readString());
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
                            ff.add(new SymLink(k, last, new File(target)) {
                                @Override
                                public long length() {
                                    return size;
                                }
                            });
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
            return ff;
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static boolean isDirectory(SuIO su, File f) {
        try {
            su.write("isdir", f);
            return toBoolean(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static long length(SuIO su, File f) {
        try {
            su.write("length", f);
            return Long.valueOf(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result ln(SuIO su, File target, File file) {
        try {
            su.write("ln", target, file);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result touch(SuIO su, File target, long time) {
        try {
            su.write("touch", target, time / 1000);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result delete(SuIO su, File target) {
        try {
            su.write("delete", target);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result mkdir(SuIO su, File target) {
        try {
            su.write("mkdir", target);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static Result rename(SuIO su, File f, File t) {
        try {
            su.write("rename", f, t);
            return su.ok();
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static long lastModified(SuIO su, File f) {
        try {
            su.write("last", f);
            return Long.valueOf(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static boolean exists(SuIO su, File f) {
        try {
            su.write("exists", f);
            return toBoolean(su.readString());
        } catch (IOException e) {
            throw new RuntimeException(new Result(su.cmd, su.su, e).errno());
        }
    }

    public static class Directory extends com.github.axet.androidlibrary.app.SuperUser.Directory {
        public Directory(File f, long last) {
            super(f, last);
        }

        @Override
        public File[] listFiles(FileFilter filter) {
            ArrayList<File> all = SuperUser.lsA(new SuIO(), this);
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

    public static class SuIO {
        public InputStream is;
        public OutputStream os;
        public Commands cmd;
        public Process su;
        public boolean valid = true;

        public SuIO() {
            this(BIN_SU);
        }

        public SuIO(String shell) {
            try {
                cmd = new Commands(BIN_SUIO + ";" + BIN_EXIT).exit(true);
                su = Runtime.getRuntime().exec(shell);
                os = new BufferedOutputStream(su.getOutputStream());
                if (cmd.exit && !EXITCODE)
                    SuperUser.writeString(BIN_TRAP + " '" + KILL_SELF + "' ERR" + EOL, os);
                SuperUser.writeString(cmd.build(), os);
                is = new BufferedInputStream(su.getInputStream());
            } catch (IOException e) {
                if (su != null)
                    throw new RuntimeException(new Result(cmd, su, e).errno());
                else
                    throw new RuntimeException(e);
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
                    throw new IOException("unknown type");
            }
        }

        public String readString() throws IOException {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            int c;
            while ((c = is.read()) != 0) {
                if (c == -1) {
                    valid = false;
                    try {
                        su.waitFor(); // wait to read exitCode() or exception will be thrown
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                    throw new EOFException();
                }
                os.write(c);
            }
            return os.toString();
        }

        public boolean ping() throws IOException {
            write("ping");
            String str = readString();
            return str.equals("pong");
        }

        public void clear() {
            try {
                skipAll(su.getInputStream());
                skipAll(su.getErrorStream());
            } catch (IOException e) {
                Log.e(TAG, "clear", e);
                valid = false;
            }
        }

        public boolean valid() {
            return valid;
        }

        public void exit() throws IOException { // no handling exit codes and stderr here
            valid = false;
            write("exit");
            try {
                su.waitFor();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            Result.must(su);
        }

        public void alive() throws IOException {
            try {
                su.exitValue();
                valid = false;
                throw new IOException("!alive");
            } catch (IllegalThreadStateException ignore) { // not exited
            }
        }

        public Result ok() { // input / output stream sanity checks
            try {
                String ok = readString();
                if (ok.equals("ok"))
                    return new Result(0);
                valid = false;
                return new Result(cmd, su, new Throwable("!ok: " + ok));
            } catch (IOException e) { // wrap exceptions, so caller decide raise or twist
                valid = false;
                return new Result(cmd, su, e);
            }
        }

        public void close() {
            valid = false;
            su.destroy();
        }
    }

    public static class RandomAccessFile extends com.github.axet.androidlibrary.app.SuperUser.RandomAccessFile {
        public static final String R = "rb";
        public static final String W = "wb"; // open and truncate

        SuIO su;

        public RandomAccessFile(File f, String mode) throws FileNotFoundException {
            try {
                su = new SuIO();
                su.write("rafopen", f, mode);
                if (mode.equals(R))
                    size = Long.valueOf(su.readString());
                else
                    su.ok().must();
            } catch (final IOException e) {
                su.valid = false;
                if (su != null)
                    throw StorageProvider.fnfe(new Result(su.cmd, su.su, e).errno());
                else
                    throw StorageProvider.fnfe(e);
            }
        }

        public RandomAccessFile(File f) throws FileNotFoundException {
            this(f, R);
        }

        public int read() throws IOException {
            try {
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
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        public int read(byte[] buf, int off, int size) throws IOException {
            try {
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
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        public void write(int b) throws IOException {
            try {
                int size = 1;
                su.write("rafwrite", offset, size);
                su.os.write(b);
                su.os.flush();
                offset += size;
                su.ok().must();
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            try {
                su.write("rafwrite", offset, len);
                su.os.write(b, off, len);
                su.os.flush();
                offset += len;
                su.ok().must();
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }

        @Override
        public void close() throws IOException {
            try {
                su.write("rafclose");
                su.ok().must();
                su.exit();
                su.close();
            } catch (IOException e) {
                su.valid = false;
                throw new IOException(new Result(su.cmd, su.su, e).errno());
            }
        }
    }

    public static class FileInputStream extends InputStream {
        RandomAccessFile r;

        public FileInputStream(File f) throws FileNotFoundException {
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

        public FileOutputStream(File f) throws FileNotFoundException {
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

    public static class DF {
        public static int ADFS_SUPER_MAGIC = 0xadf5;
        public static int AFFS_SUPER_MAGIC = 0xadff;
        public static int AFS_SUPER_MAGIC = 0x5346414f;
        public static int ANON_INODE_FS_MAGIC = 0x09041934; /* Anonymous inode FS (for  pseudofiles that have no name; e.g., epoll, signalfd, bpf) */
        public static int AUTOFS_SUPER_MAGIC = 0x0187;
        public static int BDEVFS_MAGIC = 0x62646576;
        public static int BEFS_SUPER_MAGIC = 0x42465331;
        public static int BFS_MAGIC = 0x1badface;
        public static int BINFMTFS_MAGIC = 0x42494e4d;
        public static int BPF_FS_MAGIC = 0xcafe4a11;
        public static int BTRFS_SUPER_MAGIC = 0x9123683e;
        public static int BTRFS_TEST_MAGIC = 0x73727279;
        public static int CGROUP_SUPER_MAGIC = 0x27e0eb; /* Cgroup pseudo FS */
        public static int CGROUP2_SUPER_MAGIC = 0x63677270; /* Cgroup v2 pseudo FS */
        public static int CIFS_MAGIC_NUMBER = 0xff534d42;
        public static int CODA_SUPER_MAGIC = 0x73757245;
        public static int COH_SUPER_MAGIC = 0x012ff7b7;
        public static int CRAMFS_MAGIC = 0x28cd3d45;
        public static int DEBUGFS_MAGIC = 0x64626720;
        public static int DEVFS_SUPER_MAGIC = 0x1373; /* Linux 2.6.17 and earlier */
        public static int DEVPTS_SUPER_MAGIC = 0x1cd1;
        public static int ECRYPTFS_SUPER_MAGIC = 0xf15f;
        public static int EFIVARFS_MAGIC = 0xde5e81e4;
        public static int EFS_SUPER_MAGIC = 0x00414a53;
        public static int EXT_SUPER_MAGIC = 0x137d; /* Linux 2.0 and earlier */
        public static int EXT2_OLD_SUPER_MAGIC = 0xef51;
        public static int EXT2_SUPER_MAGIC = 0xef53;
        public static int EXT3_SUPER_MAGIC = 0xef53;
        public static int EXT4_SUPER_MAGIC = 0xef53;
        public static int F2FS_SUPER_MAGIC = 0xf2f52010;
        public static int FUSE_SUPER_MAGIC = 0x65735546;
        public static int FUTEXFS_SUPER_MAGIC = 0xbad1dea; /* Unused */
        public static int HFS_SUPER_MAGIC = 0x4244;
        public static int HOSTFS_SUPER_MAGIC = 0x00c0ffee;
        public static int HPFS_SUPER_MAGIC = 0xf995e849;
        public static int HUGETLBFS_MAGIC = 0x958458f6;
        public static int ISOFS_SUPER_MAGIC = 0x9660;
        public static int JFFS2_SUPER_MAGIC = 0x72b6;
        public static int JFS_SUPER_MAGIC = 0x3153464a;
        public static int MINIX_SUPER_MAGIC = 0x137f; /* original minix FS */
        public static int MINIX_SUPER_MAGIC2 = 0x138f; /* 30 char minix FS */
        public static int MINIX2_SUPER_MAGIC = 0x2468; /* minix V2 FS */
        public static int MINIX2_SUPER_MAGIC2 = 0x2478; /* minix V2 FS, 30 char names */
        public static int MINIX3_SUPER_MAGIC = 0x4d5a; /* minix V3 FS, 60 char names */
        public static int MQUEUE_MAGIC = 0x19800202; /* POSIX message queue FS */
        public static int MSDOS_SUPER_MAGIC = 0x4d44;
        public static int MTD_INODE_FS_MAGIC = 0x11307854;
        public static int NCP_SUPER_MAGIC = 0x564c;
        public static int NFS_SUPER_MAGIC = 0x6969;
        public static int NILFS_SUPER_MAGIC = 0x3434;
        public static int NSFS_MAGIC = 0x6e736673;
        public static int NTFS_SB_MAGIC = 0x5346544e;
        public static int OCFS2_SUPER_MAGIC = 0x7461636f;
        public static int OPENPROM_SUPER_MAGIC = 0x9fa1;
        public static int OVERLAYFS_SUPER_MAGIC = 0x794c7630;
        public static int PIPEFS_MAGIC = 0x50495045;
        public static int PROC_SUPER_MAGIC = 0x9fa0; /* /proc FS */
        public static int PSTOREFS_MAGIC = 0x6165676c;
        public static int QNX4_SUPER_MAGIC = 0x002f;
        public static int QNX6_SUPER_MAGIC = 0x68191122;
        public static int RAMFS_MAGIC = 0x858458f6;
        public static int REISERFS_SUPER_MAGIC = 0x52654973;
        public static int ROMFS_MAGIC = 0x7275;
        public static int SECURITYFS_MAGIC = 0x73636673;
        public static int SELINUX_MAGIC = 0xf97cff8c;
        public static int SMACK_MAGIC = 0x43415d53;
        public static int SMB_SUPER_MAGIC = 0x517b;
        public static int SOCKFS_MAGIC = 0x534f434b;
        public static int SQUASHFS_MAGIC = 0x73717368;
        public static int SYSFS_MAGIC = 0x62656572;
        public static int SYSV2_SUPER_MAGIC = 0x012ff7b6;
        public static int SYSV4_SUPER_MAGIC = 0x012ff7b5;
        public static int TMPFS_MAGIC = 0x01021994;
        public static int TRACEFS_MAGIC = 0x74726163;
        public static int UDF_SUPER_MAGIC = 0x15013346;
        public static int UFS_MAGIC = 0x00011954;
        public static int USBDEVICE_SUPER_MAGIC = 0x9fa2;
        public static int V9FS_MAGIC = 0x01021997;
        public static int VXFS_SUPER_MAGIC = 0xa501fcf5;
        public static int XENFS_SUPER_MAGIC = 0xabba1974;
        public static int XENIX_SUPER_MAGIC = 0x012ff7b4;
        public static int XFS_SUPER_MAGIC = 0x58465342;
        public static int _XIAFS_SUPER_MAGIC = 0x012fd16d; /* Linux 2.0 and earlier */

        public static final int S_IFMT = 0170000; //  bit mask for the file type bit field
        public static final int S_IFSOCK = 0140000; // socket
        public static final int S_IFLNK = 0120000; // symbolic link
        public static final int S_IFREG = 0100000; // regular file
        public static final int S_IFBLK = 0060000; // block device
        public static final int S_IFDIR = 0040000; // directory
        public static final int S_IFCHR = 0020000; //  character device
        public static final int S_IFIFO = 0010000; // FIFO

        public String device; // 234:20
        public long inode; // inode number
        public int mode; // mode
        public long uid; // user id
        public long gid; // group id
        public long type; // file system type
        public long bsize; // block size
        public long blocks; // total blocks
        public long bfree; // free blocks
        public long nodes; // total inodes
        public long nfree; // free inodes
        public String name;
        public String group;

        public DF(File path) {
            SuperUser.SuIO su = new SuperUser.SuIO(SuperUser.BIN_SH);
            create(su, path);
            su.close();
        }

        public DF(SuperUser.SuIO su, File path) {
            create(su, path);
        }

        public void create(SuperUser.SuIO su, File path) {
            try {
                su.write("df", path.getAbsolutePath());
                String str = su.readString();
                String[] ss = str.split(" ");
                device = ss[0];
                inode = Long.valueOf(ss[1]);
                mode = Integer.valueOf(ss[2]);
                uid = Long.valueOf(ss[3]);
                gid = Long.valueOf(ss[4]);
                type = Long.valueOf(ss[5]);
                bsize = Long.valueOf(ss[6]);
                blocks = Long.valueOf(ss[7]);
                bfree = Long.valueOf(ss[8]);
                nodes = Long.valueOf(ss[9]);
                nfree = Long.valueOf(ss[10]);
                name = ss[11];
                group = ss[12];
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public String getMode8() {
            return Long.toString(mode & 0777, 8);
        }

        public String getMode() {
            String text = "";
            switch (mode & S_IFMT) {
                case S_IFSOCK:
                    text += "s";
                    break;
                case S_IFLNK:
                    text += "l";
                    break;
                case S_IFREG:
                    text += "-";
                    break;
                case S_IFBLK:
                    text += "b";
                    break;
                case S_IFDIR:
                    text += "d";
                    break;
                case S_IFCHR:
                    text += "c";
                    break;
                case S_IFIFO:
                    text += "f";
                    break;
            }
            long m = mode & 0777;
            String mm = "rwx";
            for (int i = 0; i < 9; i++) {
                if ((m & 256) == 256)
                    text += mm.charAt(i % 3);
                else
                    text += "-";
                m = m << 1;
            }
            return text;
        }
    }
}
