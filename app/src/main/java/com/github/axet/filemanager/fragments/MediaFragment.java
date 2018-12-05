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
import android.widget.TextView;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.app.SuperUser;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.widgets.GifView;
import com.github.axet.filemanager.widgets.HorizontalScrollView;
import com.github.axet.filemanager.widgets.TextViewStream;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

import cz.msebera.android.httpclient.entity.ContentType;

public class MediaFragment extends Fragment {
    public static final String TAG = MediaFragment.class.getSimpleName();

    Uri uri;
    Storage storage;

    HorizontalScrollView scroll;
    TextViewStream text;
    boolean supported;

    View image;

    public MediaFragment() {
    }

    public static MediaFragment newInstance(Uri uri) {
        MediaFragment fragment = new MediaFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        fragment.setArguments(args);
        return fragment;
    }

    public View error(String str) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v = inflater.inflate(R.layout.fragment_media_error, null, false);
        TextView t = (TextView) v.findViewById(R.id.text);
        t.setText(str);
        return v;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        storage = new Storage(getContext());
        uri = getUri();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (text != null) {
            text.close();
            text = null;
        }
        storage.closeSu();
    }

    public Uri getUri() {
        if (uri == null)
            return getArguments().getParcelable("uri");
        return uri;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            if (storage.getLength(uri) <= 0)
                return error(getContext().getString(R.string.empty_list));
        } catch (Exception e) {
            Log.e(TAG, "length", e);
            storage.closeSu();
            return error(SuperUser.toMessage(e));
        }
        FileTypeDetector.FileTxt f = new FileTypeDetector.FileTxt();
        FileTypeDetector.FileHTML h = new FileTypeDetector.FileHTML();
        GifView.FileGif g = new GifView.FileGif();
        InputStream is = null;
        try {
            is = storage.open(uri);
            byte[] buf = new byte[1024]; // optimal detect size
            int len = is.read(buf);
            is.close();
            is = null;
            if (len <= 0)
                throw new IOException("unable to read");
            FileTypeDetector.Detector[] dd = new FileTypeDetector.Detector[]{f, h, g};
            FileTypeDetector.FileTypeDetectorXml xml = new FileTypeDetector.FileTypeDetectorXml(dd);
            FileTypeDetector bin = new FileTypeDetector(dd);
            bin.write(buf, 0, len);
            bin.close();
            xml.write(buf, 0, len);
            xml.close();
            if (len < buf.length && !f.done)
                f.detected = true;
        } catch (Exception e) {
            Log.d(TAG, "Unable to read", e);
            try {
                if (is != null)
                    is.close();
            } catch (IOException e1) {
                Log.d(TAG, "unable to close", e1);
            }
            storage.closeSu();
            return error(SuperUser.toMessage(e));
        }
        try {
            if (h.detected) {
                supported = true;
                WebViewCustom web = new WebViewCustom(getContext());
                web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                ContentType content;
                if (h.content != null)
                    content = ContentType.parse(h.content);
                else
                    content = ContentType.DEFAULT_TEXT;
                String html = IOUtils.toString(is = storage.open(uri), content.getCharset());
                web.loadHtmlWithBaseURL(null, html, null);
                return web;
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to read", e);
            try {
                if (is != null)
                    is.close();
            } catch (IOException e1) {
                Log.d(TAG, "unable to close", e1);
            }
            storage.closeSu();
            return error(SuperUser.toMessage(e));
        }
        try {
            if (f.detected) {
                supported = true;
                View v = inflater.inflate(R.layout.fragment_media_text, container, false);
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
                text.setText(is = storage.open(uri));
                return v;
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to read", e);
            try {
                if (is != null)
                    is.close();
            } catch (IOException e1) {
                Log.e(TAG, "close", e1);
            }
            storage.closeSu();
            return error(SuperUser.toMessage(e));
        } // no finally keep 'is'
        try {
            if (g.detected) {
                supported = true;
                return new GifView(getContext(), is = storage.open(uri));
            }
            Bitmap bm = BitmapFactory.decodeStream(is = storage.open(uri));
            if (bm != null) {
                supported = true;
                image = inflater.inflate(R.layout.fragment_media_image, container, false);
                ImageView i = (ImageView) image.findViewById(R.id.image);
                i.setImageBitmap(bm);
                return image;
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to read", e);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                Log.d(TAG, "unable to close", e);
            }
            storage.closeSu();
        }
        return error(getContext().getString(R.string.unsupported));
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
