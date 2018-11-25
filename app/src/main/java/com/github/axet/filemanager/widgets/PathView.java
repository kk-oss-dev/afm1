package com.github.axet.filemanager.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.ThemeUtils;

public class PathView extends HorizontalScrollView {
    Uri uri;
    LinearLayoutCompat ll;
    public Listener listener;

    public interface Listener {
        void onUriSelected(Uri u);
    }

    public PathView(Context context) {
        super(context);
        create();
    }

    public PathView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    @TargetApi(11)
    public PathView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    @TargetApi(21)
    public PathView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    void create() {
        ll = new LinearLayoutCompat(getContext());
        ll.setOrientation(LinearLayout.HORIZONTAL);
        addView(ll);
    }

    public void setUri(Uri u) {
        uri = u;
        ll.removeAllViews();
        add(u);
        post(new Runnable() {
            @Override
            public void run() {
                PathView.this.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
            }
        });
    }

    void add(Uri uri) {
        while (uri != null) {
            TextView b = new TextView(getContext());
            int p15 = ThemeUtils.dp2px(getContext(), 15);
            int p10 = ThemeUtils.dp2px(getContext(), 10);
            b.setPadding(p10, p15, p10, p15);
            String n = Storage.getName(getContext(), uri);
            if (n.isEmpty())
                n = OpenFileDialog.ROOT;
            b.setText(n);
            final Uri u = uri;
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onUriSelected(u);
                }
            });
            ll.addView(b, 0);
            uri = Storage.getParent(getContext(), uri);
            if (uri != null) {
                TextView p = new TextView(getContext());
                p.setText(">");
                ViewCompat.setAlpha(p, 0.3f);
                ll.addView(p, 0);
            }
        }
    }
}
