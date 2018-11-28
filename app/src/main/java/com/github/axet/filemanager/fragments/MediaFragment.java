package com.github.axet.filemanager.fragments;

import android.content.Intent;
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
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activities.FullscreenActivity;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.widgets.HorizontalScrollView;
import com.github.axet.filemanager.widgets.TextViewStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

public class MediaFragment extends Fragment {
    public static final String TAG = MediaFragment.class.getSimpleName();

    Uri uri;
    Storage storage;
    Storage.Nodes nodes;

    HorizontalScrollView scroll;
    TextViewStream text;
    boolean supported;

    View image;
    View left;
    View right;

    public MediaFragment() {
    }

    public static MediaFragment newInstance(Uri uri, boolean b) {
        MediaFragment fragment = new MediaFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        args.putBoolean("buttons", b);
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
        storage = new Storage(getContext());
        uri = getArguments().getParcelable("uri");
        final Uri p = Storage.getParent(getContext(), uri);
        ArrayList<Storage.Node> nn = storage.list(p);
        nodes = new Storage.Nodes(nn);
        Collections.sort(nodes, new FilesFragment.SortByName());
        try {
            if (storage.getLength(uri) == 0)
                return error(getContext().getString(R.string.empty_list));
        } catch (Exception e) {
            return error(e.getMessage());
        }
        InputStream is = null;
        try {
            is = storage.open(uri);
            Bitmap bm = BitmapFactory.decodeStream(is);
            if (bm != null) {
                image = inflater.inflate(R.layout.fragment_media_image, container, false);
                ImageView i = (ImageView) image.findViewById(R.id.image);
                i.setImageBitmap(bm);
                supported = true;
                return image;
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
        return error(getContext().getString(R.string.unsupported));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateButtons();
    }

    void updateButtons() {
        View v = getView();
        View t = v.findViewById(R.id.toolbar_files);
        if (getArguments().getBoolean("buttons", false))
            t.setVisibility(View.VISIBLE);
        else
            t.setVisibility(View.GONE);
        left = v.findViewById(R.id.left);
        right = v.findViewById(R.id.right);
        View fullscreen = v.findViewById(R.id.fullscreen);
        TextView title = (TextView) v.findViewById(R.id.title);
        TextView count = (TextView) v.findViewById(R.id.count);
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = nodes.find(uri);
                i--;
                if (i < 0)
                    i = 0;
                Uri u = nodes.get(i).uri;
                getContext().sendBroadcast(new Intent(HexDialogFragment.CHANGED).putExtra("uri", u));
            }
        });
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = nodes.find(uri);
                i++;
                int last = nodes.size() - 1;
                if (i >= last)
                    i = last;
                Uri u = nodes.get(i).uri;
                getContext().sendBroadcast(new Intent(HexDialogFragment.CHANGED).putExtra("uri", u));
            }
        });
        fullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FullscreenActivity.start(getContext(), uri);
            }
        });
        title.setText(storage.getName(uri));
        count.setText((nodes.find(uri) + 1) + "/" + nodes.size());
    }
}
