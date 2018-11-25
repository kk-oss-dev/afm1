package com.github.axet.filemanager.widgets;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.axet.filemanager.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class HexViewStream extends RecyclerView {
    Adapter adapter;

    public static String formatSize(int c) {
        String str = "00000000  ";
        for (int i = 0; i < c; i++)
            str += "00 00 00 00  ";
        for (int i = 0; i < c; i++)
            str += "####";
        return str;
    }

    public static String toChar(byte b) {
        if (b < ' ')
            return ".";
        return String.valueOf((char) b);
    }

    public static int measureMax(RecyclerView list, int widthSpec) {
        Holder h = new Holder(list);
        int w = View.MeasureSpec.getSize(widthSpec) - list.getPaddingLeft() - list.getPaddingRight();
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

    public static float measureFont(RecyclerView list, Holder h, int widthSpec, int max) {
        h.text.setText(formatSize(max));
        int w = View.MeasureSpec.getSize(widthSpec) - list.getPaddingLeft() - list.getPaddingRight();
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
        InputStream is;
        long size;
        int c;
        int max; // row bytes length
        int min; // min width
        int count; // number of rows
        ArrayList<byte[]> ll = new ArrayList<>();
        float sp;

        public Adapter(InputStream is, long size) {
            this.is = is;
            this.size = size;
        }

        public void open(int widthSpec) {
            c = measureMax(HexViewStream.this, widthSpec);
            max = c * 4;
            count = (int) (size / max);
            if (size % max > 0)
                count += 1;
        }

        public void close() {
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
            return new Holder(parent);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            while (position >= ll.size()) {
                byte[] buf = new byte[max];
                try {
                    int len = is.read(buf);
                    if (len > 0) {
                        if (len < buf.length)
                            buf = Arrays.copyOf(buf, len);
                        ll.add(buf);
                    } else {
                        break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            byte[] buf;
            if (position >= ll.size())
                buf = new byte[]{};
            else
                buf = ll.get(position);

            h.text.setTextSize(sp);
            h.format(position * max, buf, max);
            h.text.setMinimumWidth(min);
        }

        @Override
        public int getItemCount() {
            return count;
        }

        public void onMeasure(int widthSpec, int heightSpec) {
            if (ll.size() == 0)
                open(widthSpec);

            Holder m = new Holder(HexViewStream.this);
            sp = measureFont(HexViewStream.this, m, widthSpec, c);
            m.text.setText(formatSize(c));
            m.itemView.measure(0, 0);
            min = m.itemView.getMeasuredWidth();

            notifyDataSetChanged();
        }
    }

    public HexViewStream(Context context) {
        super(context);
        create();
    }

    public HexViewStream(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public HexViewStream(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        create();
    }

    void create() {
        setLayoutManager(new LinearLayoutManager(getContext()));
    }

    public void setText(InputStream is, long size) {
        close();
        adapter = new Adapter(is, size);
        setAdapter(adapter);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        adapter.onMeasure(widthSpec, heightSpec);
        super.onMeasure(widthSpec, heightSpec);
    }

    public void close() {
        if (adapter != null) {
            adapter.close();
            adapter = null;
        }
    }
}
