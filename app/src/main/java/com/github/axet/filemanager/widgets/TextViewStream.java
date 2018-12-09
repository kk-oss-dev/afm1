package com.github.axet.filemanager.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;

public class TextViewStream extends RecyclerView {
    public static final String TAG = TextViewStream.class.getSimpleName();

    Adapter adapter;
    Typeface tf = Typeface.MONOSPACE;
    float sp = 10;

    public static class Holder extends RecyclerView.ViewHolder {
        TextView text;

        public Holder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
            text.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        public Holder(ViewGroup parent) {
            this(new TextView(parent.getContext()));
        }
    }

    public class Adapter extends RecyclerView.Adapter<Holder> {
        Scanner scanner;
        ArrayList<String> ll = new ArrayList<>();

        public Adapter(InputStream is) {
            create(is);
        }

        public void create(InputStream is) {
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
        public void onBindViewHolder(Holder h, int position) {
            if (position >= ll.size() - 1 - getChildCount()) { // load screen + keep second screen
                post(new Runnable() {
                    @Override
                    public void run() {
                        next();
                    }
                });
            }
            h.text.setTypeface(tf, Typeface.NORMAL);
            h.text.setText(ll.get(position));
            h.text.setTextSize(sp);
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
        adapter = new Adapter(is);
        setAdapter(adapter);
        adapter.load();
    }

    public void setTextSize(float sp) {
        this.sp = sp;
        adapter.notifyDataSetChanged();
    }

    public void close() {
        setAdapter(null);
        if (adapter != null) {
            adapter.close();
            adapter = null;
        }
    }

    public void notifyDataSetChanged() {
        adapter.notifyDataSetChanged();
    }
}
