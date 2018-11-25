package com.github.axet.filemanager.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.widgets.HorizontalScrollView;
import com.github.axet.filemanager.widgets.TextViewStream;

import java.io.IOException;
import java.io.InputStream;

public class MediaFragment extends Fragment {
    public static final String TAG = MediaFragment.class.getSimpleName();

    HorizontalScrollView scroll;
    TextViewStream text;
    boolean supported;

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
        if (text != null) {
            text.close();
            text = null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Storage storage = new Storage(getContext());
        Uri uri = getArguments().getParcelable("uri");
        try {
            if (storage.getLength(uri) == 0)
                return HexFragment.error(getContext(), getContext().getString(R.string.empty_list));
        } catch (Exception e) {
            return HexFragment.error(getContext(), e.getMessage());
        }
        InputStream is = null;
        try {
            is = storage.open(uri);
            Bitmap bm = BitmapFactory.decodeStream(is);
            if (bm != null) {
                ImageView i = new ImageView(getContext());
                i.setImageBitmap(bm);
                supported = true;
                return i;
            }
        } catch (IOException e) {
            Log.d(TAG, "Unable to read", e);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                Log.d(TAG, "unable to close", e);
            }
        }
        try {
            is = storage.open(uri);
            byte[] buf = new byte[1024];
            int len = is.read(buf);
            FileTxt f = new FileTxt();
            f.write(buf, 0, len);
            if (len < buf.length && !f.done)
                f.detected = true;
            if (f.detected) {
                View v = inflater.inflate(R.layout.fragment_media, container, false);
                View wrap = v.findViewById(R.id.wrap);
                View mono = v.findViewById(R.id.mono);
                wrap.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scroll.setWrap(!scroll.getWrap());
                        text.notifyDataSetChanged();
                    }
                });
                mono.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        text.setTypeface(text.getTypeface() == Typeface.DEFAULT ? Typeface.MONOSPACE : Typeface.DEFAULT);
                    }
                });
                scroll = (HorizontalScrollView) v.findViewById(R.id.scroll);
                text = (TextViewStream) v.findViewById(R.id.list);
                text.setText(storage.open(uri));
                supported = true;
                return v;
            }
        } catch (IOException e) {
            Log.d(TAG, "Unable to read", e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "unable to close", e);
            }
        }
        return HexFragment.error(getContext(), getContext().getString(R.string.unsupported));
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
