package com.github.axet.filemanager.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Scanner;

public class Mounts extends ArrayList<Mounts.Info> {
    public static class Info {
        public String device; // /dev/name
        public String dev_t; // 0:20, not unique
        public String mount;
        public String fs;

        public Info(String dt, String m, String f, String d) {
            device = d;
            dev_t = dt;
            mount = m;
            fs = f;
        }
    }

    // https://stackoverflow.com/questions/13574188/parsing-proc-mounts-in-java
    public static String parse(String p) {
        p = p.replaceAll("\\\\040", " ");
        p = p.replaceAll("\\\\011", "\t");
        p = p.replaceAll("\\\\012", "\n");
        p = p.replaceAll("\\\\134", "\\");
        return p;
    }

    public static Integer pathSegments(String s) {
        return Storage.splitPath(s).length;
    }

    public static Integer pathNameLength(String s) {
        return new File(s).getName().length();
    }

    public static class LongFirst implements Comparator<Info> {
        @Override
        public int compare(Info o1, Info o2) {
            int c = pathSegments(o2.mount).compareTo(pathSegments(o1.mount));
            if (c != 0)
                return c;
            return pathNameLength(o2.mount).compareTo(pathNameLength(o1.mount));
        }
    }

    public Mounts() {
        try {
            InputStreamReader is = new InputStreamReader(new FileInputStream("/proc/self/mountinfo"), Charset.defaultCharset());
            Scanner scanner = new Scanner(is);
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                String[] ss = line.split(" ");
                add(new Info(ss[2], ss[4], ss[8], ss[9]));
            }
             Collections.sort(this, new LongFirst());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeByFS(String fs) {
        for (int i = size() - 1; i >= 0; i--) {
            Info m = get(i);
            if (m.fs.equals(fs))
                remove(i);
        }
    }

    public Info findMount(File path) {
        String a = path.getAbsolutePath();
        for (Info i : this) {
            if (a.startsWith(i.mount))
                return i;
        }
        return null;
    }

    public Info findDev(String d) {
        for (Info i : this) {
            if (i.dev_t.equals(d))
                return i;
        }
        return null;
    }
}
