package com.github.axet.filemanager.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.support.v7.widget.AppCompatImageView;

import com.github.axet.androidlibrary.app.FileTypeDetector;

import java.io.InputStream;

public class GifView extends AppCompatImageView {
    public static final String EXT = "gif";

    Paint p = new Paint();

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
            super(ext, "GIF89a");
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

    public static class GifDrawable extends AnimationDrawable {
        public static int STEP = 1000 / 24; // 24 frames per second
        Movie m;
        int frame = 0;
        int duration;
        boolean mRunning;

        public GifDrawable(InputStream is) {
            m = Movie.decodeStream(is);
            int d = m.duration();
            if (d == 0)
                d = 1000;
            duration = d / STEP;
        }

        @Override
        public int getIntrinsicWidth() {
            return m.width();
        }

        @Override
        public int getIntrinsicHeight() {
            return m.height();
        }

        @Override
        public Drawable getFrame(int index) {
            return this;
        }

        @Override
        public int getDuration(int i) {
            return STEP;
        }

        @Override
        public void run() {
            frame++;
            if (frame > duration)
                frame = 0;
            invalidateSelf();
            schedule();
        }

        void schedule() {
            unscheduleSelf(this);
            scheduleSelf(this, SystemClock.uptimeMillis() + STEP);
        }

        @Override
        public int getNumberOfFrames() {
            return duration;
        }

        @Override
        public void draw(Canvas canvas) {
            m.setTime(frame * STEP);
            m.draw(canvas, 0, 0);
        }

        @Override
        public void start() {
            if (!isRunning()) {
                mRunning = true;
                schedule();
            }
        }

        @Override
        public void stop() {
            mRunning = false;
            unscheduleSelf(this);
        }

        @Override
        public boolean isRunning() {
            return mRunning;
        }
    }

    public GifView(Context context, InputStream is) {
        super(context);
        GifDrawable g = new GifDrawable(is);
        setImageDrawable(g);
        g.start();
        if (Build.VERSION.SDK_INT >= 11)
            setLayerType(LAYER_TYPE_SOFTWARE, p);
    }
}
