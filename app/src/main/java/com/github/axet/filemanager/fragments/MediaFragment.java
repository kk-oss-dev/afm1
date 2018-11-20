package com.github.axet.filemanager.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.axet.filemanager.R;

import java.io.IOException;
import java.io.InputStream;

public class MediaFragment extends Fragment {
    public static final String TAG = MediaFragment.class.getSimpleName();

    // https://stackoverflow.com/questions/898669/how-can-i-detect-if-a-file-is-binary-non-text-in-python
    public static class FileTxt {
        public static final int F = 0; /* character never appears in text */
        public static final int T = 1; /* character appears in plain ASCII text */
        public static final int I = 2; /* character appears in ISO-8859 text */
        public static final int X = 3; /* character appears in non-ISO extended ASCII (Mac, IBM PC) */
        public static final int R = 4; // lib.ru formatting, ^T and ^U

        // https://github.com/file/file/blob/f2a6e7cb7db9b5fd86100403df6b2f830c7f22ba/src/encoding.c#L151-L228
        public static byte[] text_chars = new byte[]
                {
                        /*                  BEL BS HT LF VT FF CR    */
                        F, F, F, F, F, F, F, T, T, T, T, T, T, T, F, F,  /* 0x0X */
                        /*                              ESC          */
                        F, F, F, F, R, R, F, F, F, F, F, T, F, F, F, F,  /* 0x1X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x2X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x3X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x4X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x5X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, T,  /* 0x6X */
                        T, T, T, T, T, T, T, T, T, T, T, T, T, T, T, F,  /* 0x7X */
                        /*            NEL                            */
                        X, X, X, X, X, T, X, X, X, X, X, X, X, X, X, X,  /* 0x8X */
                        X, X, X, X, X, X, X, X, X, X, X, X, X, X, X, X,  /* 0x9X */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xaX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xbX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xcX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xdX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I,  /* 0xeX */
                        I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I   /* 0xfX */
                };

        public boolean done;
        public boolean detected;
        public int count = 0;

        public FileTxt() {
        }

        public void write(byte[] buf, int off, int len) {
            int end = off + len;
            for (int i = off; i < end; i++) {
                int b = buf[i] & 0xFF;
                for (int k = 0; k < text_chars.length; k++) {
                    if (text_chars[b] == F) {
                        done = true;
                        detected = false;
                        return;
                    }
                    count++;
                }
            }
            if (count >= 1000) {
                done = true;
                detected = true;
            }
        }
    }

    public MediaFragment() {
    }

    public static MediaFragment newInstance(Uri uri) {
        MediaFragment fragment = new MediaFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FilesFragment.PendingOperation op = new FilesFragment.PendingOperation(getContext());
        Uri uri = getArguments().getParcelable("uri");
        try {
            InputStream is = op.open(uri);
            Bitmap bm = BitmapFactory.decodeStream(is);
            if (bm != null) {
                ImageView i = new ImageView(getContext());
                i.setImageBitmap(bm);
                return i;
            }
        } catch (IOException e) {
            Log.d(TAG, "Unable to read", e);
        }
        try {
            InputStream is = op.open(uri);
            byte[] buf = new byte[16 * 1024];
            int len = is.read(buf);
            FileTxt f = new FileTxt();
            f.write(buf, 0, len);
            if (len < buf.length && !f.done)
                f.detected = true;
            if (f.detected) {
                ScrollView v = new ScrollView(getContext());
                TextView text = new TextView(getContext());
                text.setText(new String(buf));
                v.addView(text);
                return v;
            }
        } catch (IOException e) {
            Log.d(TAG, "Unable to read", e);
        }
        FrameLayout f = new FrameLayout(getContext());
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        f.setLayoutParams(lp);
        TextView rootView = new TextView(getContext());
        rootView.setText(R.string.unsupported);
        f.addView(rootView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return f;
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
