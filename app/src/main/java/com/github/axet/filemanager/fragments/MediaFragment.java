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

import com.github.axet.androidlibrary.app.FileTypeDetector;
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
            FileTypeDetector.FileTxt f = new FileTypeDetector.FileTxt();
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
