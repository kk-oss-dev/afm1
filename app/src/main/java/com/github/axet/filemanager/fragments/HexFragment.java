package com.github.axet.filemanager.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.axet.filemanager.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class HexFragment extends Fragment {
    public static final String TAG = HexFragment.class.getSimpleName();

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

    public static int measureMax(Holder h, RecyclerView list) {
        int old = 0;
        for (int i = 1; i < 10; i++) {
            String s = formatSize(i);
            h.text.setText(s);
            h.text.measure(0, 0);
            if (h.text.getMeasuredWidth() > list.getWidth())
                return old;
            old = i;
        }
        return old;
    }

    public class Holder extends RecyclerView.ViewHolder {
        TextView text;

        public Holder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }

        public Holder(ViewGroup parent) {
            this(LayoutInflater.from(getContext()).inflate(R.layout.hex_item, parent, false));
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
        InputStream is;
        long size;
        Uri uri;
        int max; // row bytes length
        int count; // number of rows
        ArrayList<byte[]> ll = new ArrayList<>();

        public void create() {
            uri = getArguments().getParcelable("uri");
            Holder h = new Holder(list);
            int c = measureMax(h, list);
            max = c * 4;
            FilesFragment.PendingOperation op = new FilesFragment.PendingOperation(getContext());
            size = op.length(uri);
            count = (int) (size / max);
            if (size % max > 0)
                count += 1;
            try {
                is = op.open(uri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(parent);
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
            if (is == null)
                create();
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_hex, container, false);
        Adapter adapter = new Adapter();
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
