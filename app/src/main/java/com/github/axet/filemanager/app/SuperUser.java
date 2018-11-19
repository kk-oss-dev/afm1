package com.github.axet.filemanager.app;

import android.net.Uri;

import com.github.axet.androidlibrary.app.Storage;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SuperUser extends com.github.axet.androidlibrary.app.SuperUser {
    public static String LS = "ls {0} {1}";

    public static class NativeFile extends File {
        long size;
        boolean d;

        public NativeFile(File f, boolean d, long size) {
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
    }

    public static ArrayList<File> ls(Uri uri) {
        ArrayList<File> ff = new ArrayList<>();
        File f = Storage.getFile(uri);
        Commands cmd = new Commands(MessageFormat.format(LS, "-Al", escape(f)));
        cmd.stdout(true);
        Result r = su(cmd).must();
        Scanner scanner = new Scanner(r.stdout);
        Pattern p = Pattern.compile("^([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+.*\\d\\s+(.*?)$");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            Matcher m = p.matcher(line);
            if (m.matches()) {
                String perms = m.group(1);
                String size = m.group(5);
                String name = m.group(6);
                if (perms.startsWith("d")) {
                    ff.add(new NativeFile(new File(f, name), true, Long.valueOf(size)));
                } else if (perms.startsWith("l")) {
                    String[] ss = name.split("->");
                    name = ss[0].trim();
                    boolean d = false;
                    File t = new File(ss[1].trim());
                    if (t.isDirectory())
                        d = true;
                    ff.add(new NativeFile(new File(f, name), d, Long.valueOf(size)));
                } else {
                    ff.add(new NativeFile(new File(f, name), false, Long.valueOf(size)));
                }
            }
        }
        scanner.close();
        return ff;
    }

    public static long length(Uri uri) {
        File f = Storage.getFile(uri);
        Commands cmd = new Commands(MessageFormat.format("stat -c%s {0}", escape(f)));
        cmd.stdout(true);
        Result r = su(cmd);
        return Long.valueOf(r.stdout.trim());
    }
}
