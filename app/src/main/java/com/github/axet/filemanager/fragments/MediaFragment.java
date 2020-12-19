package com.github.axet.filemanager.fragments;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.FileTypeDetector;
import com.github.axet.androidlibrary.preferences.RotatePreferenceCompat;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.PopupWindowCompat;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.androidlibrary.widgets.WebViewCustom;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.widgets.GifView;
import com.github.axet.filemanager.widgets.HorizontalScrollView;
import com.github.axet.filemanager.widgets.TextViewStream;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import cz.msebera.android.httpclient.entity.ContentType;

public class MediaFragment extends Fragment {
    public static final String TAG = MediaFragment.class.getSimpleName();

    public Uri uri;
    public Storage storage;

    HorizontalScrollView scroll;
    TextViewStream text;
    boolean supported;

    public ImageView image;
    public Bitmap bm;

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
        if (bm != null) {
            bm.recycle();
            bm = null;
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
            return error(ErrorDialog.toMessage(e));
        }
        FileTypeDetector.FileTxt f = new FileTypeDetector.FileTxt();
        FileTypeDetector.FileHTML h = new FileTypeDetector.FileHTML();
        GifView.FileGif g = new GifView.FileGif();
        InputStream is = null;
        try {
            is = storage.open(uri);
            byte[] buf = new byte[FileTypeDetector.BUF_SIZE]; // optimal detect size
            int len = is.read(buf);
            is.close();
            is = null;
            if (len <= 0)
                throw new IOException("Read error");
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
            return error(ErrorDialog.toMessage(e));
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
                is.close();
                is = null;
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
            return error(ErrorDialog.toMessage(e));
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
            return error(ErrorDialog.toMessage(e));
        } // no 'finally' section keep 'is'
        try {
            if (g.detected) {
                supported = true;
                return new GifView(getContext(), is = storage.open(uri));
            }
            bm = CacheImagesAdapter.createScaled(is = storage.open(uri), getResources().getDisplayMetrics().widthPixels);
            if (bm != null) {
                supported = true;
                final AtomicInteger rotation = new AtomicInteger();
                View view = inflater.inflate(R.layout.fragment_media_image, container, false);
                final FrameLayout rotate = (FrameLayout) view.findViewById(R.id.rotate);
                final ImageView lock = (ImageView) view.findViewById(R.id.image_rotate_lock);
                lock.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Activity a = getActivity();
                        if (a.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                            RotatePreferenceCompat.setRequestedOrientationLock(getActivity());
                            lock.setBackgroundColor(ThemeUtils.getThemeColor(getContext(), android.R.attr.colorButtonNormal));
                        } else {
                            RotatePreferenceCompat.setRequestedOrientationDefault(getActivity());
                            lock.setBackgroundColor(Color.TRANSPARENT);
                        }
                    }
                });
                final View save = view.findViewById(R.id.image_rotate_save);
                save.setVisibility(View.GONE);
                save.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final ProgressDialog dialog = new ProgressDialog(getContext());
                        Thread thread = new Thread("Rotate") {
                            @Override
                            public void run() {
                                Matrix matrix = new Matrix();
                                matrix.postRotate(rotation.get());
                                try {
                                    Bitmap bm = BitmapFactory.decodeStream(storage.open(uri));
                                    Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
                                    bm.recycle();
                                    Storage.UriOutputStream os = storage.write(uri);
                                    String ext = Storage.getExt(getContext(), uri).toLowerCase();
                                    switch (ext) {
                                        case "png":
                                            rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, os.os);
                                            break;
                                        case "jpg":
                                        case "jpeg":
                                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os.os);
                                            break;
                                    }
                                    os.os.close();
                                    File cover = CacheImagesAdapter.cacheUri(getContext(), uri);
                                    cover.delete();
                                } catch (IOException e) {
                                    Log.d(TAG, "Unable to read", e);
                                } finally {
                                    dialog.dismiss();
                                }
                            }
                        };
                        dialog.setCancelable(false);
                        dialog.show();
                        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                save.setVisibility(View.GONE);
                            }
                        });
                        thread.setPriority(Thread.MIN_PRIORITY);
                        thread.start();
                    }
                });
                View left = view.findViewById(R.id.image_rotate_left);
                left.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupWindowCompat.setRotationCompat(rotate, rotation.addAndGet(-90));
                        if (rotation.get() < 0)
                            rotation.addAndGet(360);
                        save.setVisibility(View.VISIBLE);
                    }
                });
                View right = view.findViewById(R.id.image_rotate_right);
                right.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupWindowCompat.setRotationCompat(rotate, rotation.addAndGet(90));
                        if (rotation.get() > 360)
                            rotation.addAndGet(-360);
                        save.setVisibility(View.VISIBLE);
                    }
                });
                image = (ImageView) view.findViewById(R.id.image);
                image.setImageBitmap(bm);
                return view;
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
