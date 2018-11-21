package com.github.axet.filemanager.fragments;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.filemanager.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class HexFragment extends Fragment {
    public static final String TAG = HexFragment.class.getSimpleName();

    Adapter adapter;
    RecyclerView list;

    public static String formatSize(int c) {
        String str = "00000000  ";
        for (int i = 0; i < c; i++) {
            str += "00 00 00 00  ";
        }
        for (int i = 0; i < c; i++) {
            str += "####";
        }
        return str;
    }

    public static String toChar(byte b) {
        if (b < ' ')
            return ".";
        return String.valueOf((char) b);
    }

    public static int measureMax(RecyclerView list) {
        Holder h = new Holder(list);
        int w = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        int old = 0;
        for (int i = 1; i < 10; i++) {
            String s = formatSize(i);
            h.text.setText(s);
            h.itemView.measure(0, 0);
            if (h.itemView.getMeasuredWidth() > w)
                return old;
            old = i;
        }
        return old;
    }

    public static float measureFont(RecyclerView list, int max) {
        Holder h = new Holder(list);
        String s = formatSize(max);
        h.text.setText(s);
        int w = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        float old = 0;
        for (float f = 10; f < 20; f += 0.1) {
            h.text.setTextSize(f);
            h.itemView.measure(0, 0);
            if (h.itemView.getMeasuredWidth() > w)
                return old;
            old = f;
        }
        return old;
    }

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

    public static class Holder extends RecyclerView.ViewHolder {
        TextView text;

        public Holder(View itemView) {
            super(itemView);
            text = (TextView) itemView.findViewById(R.id.text);
        }

        public Holder(ViewGroup parent) {
            this(LayoutInflater.from(parent.getContext()).inflate(R.layout.hex_item, parent, false));
        }

        public void format(long addr, byte[] buf, int max) {
            String str = String.format("%08x  ", addr);
            String chars = "";
            int i;
            for (i = 0; i < buf.length; i++) {
                str += String.format("%02x ", buf[i]);
                chars += toChar(buf[i]);
                if ((i + 1) % 4 == 0)
                    str += " ";
            }
            for (; i < max; i++) {
                str += "   ";
                if ((i + 1) % 4 == 0)
                    str += " ";
            }
            text.setText(str + chars);
        }
    }

    public class Adapter extends RecyclerView.Adapter<Holder> {
        FilesFragment.PendingOperation op;
        Uri uri;
        InputStream is;
        long size;
        int max; // row bytes length
        int count; // number of rows
        ArrayList<byte[]> ll = new ArrayList<>();
        float sp;

        public void create() {
            op = new FilesFragment.PendingOperation(getContext());
            uri = getArguments().getParcelable("uri");
            try {
                is = op.open(uri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            size = op.length(uri);
        }

        public void open() {
            int c = measureMax(list);
            sp = measureFont(list, c);
            max = c * 4;
            count = (int) (size / max);
            if (size % max > 0)
                count += 1;
        }

        public void close() {
            if (op != null) {
                op.close();
                op = null;
            }
            if (is != null) {
                try {
                    is.close();
                    is = null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            Holder h = new Holder(parent);
            h.text.setTextSize(sp);
            return h;
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            while (position >= ll.size()) {
                byte[] buf = new byte[max];
                try {
                    int len = is.read(buf);
                    if (len < buf.length)
                        buf = Arrays.copyOf(buf, len);
                    ll.add(buf);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            holder.format(position * max, ll.get(position), max);
        }

        @Override
        public int getItemCount() {
            if (max == 0)
                open();
            return count;
        }
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
        if (list != null) {
            list.setAdapter(null);
            list = null;
        }
        if (adapter != null) {
            adapter.close();
            adapter = null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        adapter = new Adapter();
        try {
            adapter.create();
        } catch (RuntimeException e) {
            return error(getContext(), e.getMessage());
        }
        View rootView = inflater.inflate(R.layout.fragment_hex, container, false);
        list = (RecyclerView) rootView.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()) {
            @Override
            public void onLayoutCompleted(RecyclerView.State state) {
                super.onLayoutCompleted(state);
            }

            @Override
            public void requestLayout() {
                super.requestLayout();
            }

            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
            }

            @Override
            public void onItemsChanged(RecyclerView recyclerView) {
                super.onItemsChanged(recyclerView);
            }
        });
        list.setAdapter(adapter);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
