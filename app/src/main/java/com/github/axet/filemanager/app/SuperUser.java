package com.github.axet.filemanager.app;

import android.net.Uri;

import com.github.axet.androidlibrary.app.Storage;

import java.io.File;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SuperUser extends com.github.axet.androidlibrary.app.SuperUser {
    public static final String BIN_LS = which("ls");

    public static final SimpleDateFormat TOUCHDATE = new SimpleDateFormat("yyyyMMddHHmm.ss");
    public static final SimpleDateFormat LSDATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static final String TOUCH = BIN_TOUCH + " -mct {0} {1}";
    public static final String DELETE = BIN_RM + " -r {0}";
    public static final String LS = BIN_LS + " -AlH {0}";

    public static class NativeFile extends File {
        long size;
        long last;
        boolean d;

        public NativeFile(File f, boolean d, long size, long last) {
            super(f.getPath());
            this.size = size;
            this.d = d;
        }

        @Override
        public boolean isDirectory() {
            return d;
        }

        @Override
        public long length() {
            return size;
        }

        @Override
        public long lastModified() {
            return last;
        }
    }

    public static ArrayList<File> ls(Uri uri) {
        ArrayList<File> ff = new ArrayList<>();
        File f = Storage.getFile(uri);
        Commands cmd = new Commands(MessageFormat.format(LS, escape(f)));
        cmd.stdout(true);
        Result r = su(cmd).must();
        Scanner scanner = new Scanner(r.stdout);
        Pattern p = Pattern.compile("^([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+\\s+[^\\s]+)\\s(.*?)$");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            Matcher m = p.matcher(line);
            if (m.matches()) {
                String perms = m.group(1);
                String size = m.group(5);
                long s = 0;
                try {
                    s = Long.valueOf(size);
                } catch (NumberFormatException e) {
                }
                String date = m.group(6);
                long last = 0;
                try {
                    last = LSDATE.parse(date).getTime();
                } catch (ParseException e) {
                }
                String name = m.group(7);
                if (perms.startsWith("d")) {
                    File k = new File(name);
                    if (!f.equals(k))
                        k = new File(f, name);
                    ff.add(new NativeFile(k, true, s, last));
                } else if (perms.startsWith("l")) {
                    String[] ss = name.split("->");
                    name = ss[0].trim();
                    boolean d = false;
                    File t = new File(ss[1].trim());
                    if (t.isDirectory())
                        d = true;
                    File k = new File(name);
                    if (!f.equals(k))
                        k = new File(f, name);
                    ff.add(new NativeFile(k, d, s, last));
                } else {
                    File k = new File(name);
                    if (!f.equals(k))
                        k = new File(f, name);
                    ff.add(new NativeFile(k, false, s, last));
                }
            }
        }
        scanner.close();
        return ff;
    }

    public static Result touch(File f, long last) {
        return su(TOUCH, TOUCHDATE.format(last), escape(f));
    }

    public static Result delete(File f) {
        return su(DELETE, escape(f));
    }

    public static long length(Uri uri) {
        File f = Storage.getFile(uri);
        Commands cmd = new Commands(MessageFormat.format("stat -c%s {0}", escape(f)));
        cmd.stdout(true);
        Result r = su(cmd);
        return Long.valueOf(r.stdout.trim());
    }
}
