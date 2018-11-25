package com.github.axet.filemanager.fragments;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.widgets.HexViewStream;

public class HexFragment extends Fragment {
    public static final String TAG = HexFragment.class.getSimpleName();

    HexViewStream text;

    public static View error(Context context, String msg) {
        FrameLayout f = new FrameLayout(context);
        int dp5 = ThemeUtils.dp2px(context, 5);
        f.setPadding(dp5, dp5, dp5, dp5);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        f.setLayoutParams(lp);
        TextView rootView = new TextView(context);
        rootView.setText(msg);
        f.addView(rootView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return f;
    }

    public HexFragment() {
    }

    public static HexFragment newInstance(Uri uri) {
        HexFragment fragment = new HexFragment();
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
        if (text != null)
            text.close();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Storage storage = new Storage(getContext());
        Uri uri = getArguments().getParcelable("uri");
        try {
            long size = storage.getLength(uri);
            if (size == 0)
                return HexFragment.error(getContext(), getContext().getString(R.string.empty_list));
            View rootView = inflater.inflate(R.layout.fragment_hex, container, false);
            text = (HexViewStream) rootView.findViewById(R.id.list);
            text.setText(storage.open(uri), size);
            return rootView;
        } catch (Exception e) {
            return error(getContext(), e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
