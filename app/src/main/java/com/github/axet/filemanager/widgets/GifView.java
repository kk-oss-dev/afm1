package com.github.axet.filemanager.widgets;

import android.content.Context;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.sound.MediaPlayerCompat;

import java.io.InputStream;

public class GifView extends MediaPlayerCompat.MovieView {
    public static final String TAG = GifView.class.getSimpleName();

    public static final String EXT = "gif";

    public static class FileGif89a extends FileTypeDetector.ExtDetector.Handler {
        public FileGif89a(String ext) {
            super(ext, "GIF89a");
        }

        public FileGif89a() {
            this(EXT);
        }
    }

    public static class FileGif87a extends FileTypeDetector.ExtDetector.Handler {
        public FileGif87a(String ext) {
            super(ext, "GIF87a");
        }

        public FileGif87a() {
            this(EXT);
        }
    }

    public static class FileGif extends FileTypeDetector.ExtDetector.Handler {
        FileGif87a g87 = new FileGif87a();
        FileGif89a g89 = new FileGif89a();

        public FileGif() {
            super(EXT);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            g87.write(buf, off, len);
            g89.write(buf, off, len);
            done = g87.done || g89.done;
            detected = g87.detected || g89.detected;
        }
    }

    public GifView(Context context, InputStream is) {
        super(context, is);
    }
}
