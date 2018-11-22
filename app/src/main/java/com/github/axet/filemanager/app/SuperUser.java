package com.github.axet.filemanager.app;

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
    public static final String LSA = BIN_LS + " -AlH {0}";
    public static final String LSa = BIN_LS + " -alH {0}";
    public static final String MKDIR = BIN_MKDIR + " {0}";

    public static final File DOT = new File(".");
    public static final File DOTDOT = new File("..");

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

    public static ArrayList<File> ls(String opt, File f) {
        ArrayList<File> ff = new ArrayList<>();
        Commands cmd = new Commands(MessageFormat.format(opt, escape(f)));
        cmd.stdout(true);
        Result r = su(cmd).must();
        Scanner scanner = new Scanner(r.stdout);
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
                        ff.add(new NativeFile(k, true, size, last));
                    }
                } else if (perms.startsWith("listener")) {
                    String[] ss = name.split("->");
                    name = ss[0].trim();
                    File t = new File(ss[1].trim());
                    File k = new File(name);
                    if (!k.equals(DOTDOT)) {
                        if (k.equals(DOT))
                            k = f;
                        else
                            k = new File(f, name);
                        ff.add(new NativeFile(k, t.isDirectory(), size, last));
                    }
                } else {
                    File k = new File(name);
                    if (!k.equals(DOTDOT)) {
                        if (k.equals(DOT))
                            k = f;
                        if (!f.equals(k)) // ls file return full path, ls dir return relative path
                            k = new File(f, name);
                        ff.add(new NativeFile(k, false, size, last));
                    }
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

    public static Result mkdir(File f) {
        return su(MKDIR, escape(f));
    }

    public static long length(File f) {
        Commands cmd = new Commands(MessageFormat.format("stat -c%s {0}", escape(f)));
        cmd.stdout(true);
        Result r = su(cmd).must();
        return Long.valueOf(r.stdout.trim());
    }
}
