package com.github.axet.filemanager.widgets;

import android.content.Context;
import android.graphics.Typeface;
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
import java.util.Scanner;

public class TextViewStream extends RecyclerView {
    InputStream is;
    Adapter adapter;
    Typeface tf = Typeface.MONOSPACE;

    public static class Holder extends RecyclerView.ViewHolder {
        TextView text;

        public Holder(View itemView) {
            super(itemView);
            text = (TextView) itemView.findViewById(R.id.text);
        }

        public Holder(ViewGroup parent) {
            this(LayoutInflater.from(parent.getContext()).inflate(R.layout.media_item, parent, false));
        }
    }

    public class Adapter extends RecyclerView.Adapter<Holder> {
        Scanner scanner;
        ArrayList<String> ll = new ArrayList<>();

        public Adapter() {
            create();
        }

        public void create() {
            scanner = new Scanner(is);
        }

        public void close() {
            if (scanner != null) {
                scanner.close();
                scanner = null;
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(parent);
        }

        void load() {
            for (int i = 0; i < 50; i++)
                next();
        }

        boolean next() {
            if (scanner == null)
                return false;
            if (scanner.hasNextLine())
                ll.add(scanner.nextLine());
            else
                scanner = null;
            notifyDataSetChanged();
            return true;
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            if (position >= ll.size() - 1 - getChildCount()) { // load screen + keep second screen
                post(new Runnable() {
                    @Override
                    public void run() {
                        next();
                    }
                });
            }
            holder.text.setTypeface(tf, Typeface.NORMAL);
            holder.text.setText(ll.get(position));
        }

        @Override
        public int getItemCount() {
            return ll.size();
        }
    }

    public TextViewStream(Context context) {
        super(context);
        create();
    }

    public TextViewStream(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public TextViewStream(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        create();
    }

    public void create() {
        setLayoutManager(new LinearLayoutManager(getContext()));
    }

    public void setTypeface(Typeface tf) {
        this.tf = tf;
        adapter.notifyDataSetChanged();
    }

    public Typeface getTypeface() {
        return tf;
    }

    public void setText(InputStream is) {
        close();
        this.is = is;
        adapter = new Adapter();
        setAdapter(adapter);
        adapter.load();
    }

    public void close() {
        try {
            setAdapter(null);
            if (adapter != null) {
                adapter.close();
                adapter = null;
            }
            if (is != null) {
                is.close();
                is = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void notifyDataSetChanged() {
        adapter.notifyDataSetChanged();
    }
}
