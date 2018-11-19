package com.github.axet.filemanager.widgets;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.ThemeUtils;

import java.io.File;

public class PathView extends HorizontalScrollView {
    LinearLayout ll;
    public Listener l;
    Uri uri;

    public interface Listener {
        void setUri(Uri u);
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
        ll = new LinearLayout(getContext());
        ll.setOrientation(LinearLayout.HORIZONTAL);
        addView(ll);
    }

    public void setUri(Uri u) {
        uri = u;
        ll.removeAllViews();
        String s = u.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = Storage.getFile(u);
            add(f);
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id;
            if (DocumentsContract.isDocumentUri(getContext(), u))
                id = DocumentsContract.getDocumentId(u);
            else
                id = DocumentsContract.getTreeDocumentId(u);
            String[] ss = id.split(":");
            File f;
            if (ss.length < 2)
                f = new File(OpenFileDialog.ROOT);
            else
                f = new File(OpenFileDialog.ROOT, ss[1]);
            add(f);
        } else {
            throw new Storage.UnknownUri();
        }
        post(new Runnable() {
            @Override
            public void run() {
                PathView.this.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
            }
        });
    }

    void add(File f) {
        while (f != null) {
            TextView b = new TextView(getContext());
            String n = f.getName();
            if (n.isEmpty())
                n = OpenFileDialog.ROOT;
            b.setText(n);
            int p15 = ThemeUtils.dp2px(getContext(), 15);
            int p10 = ThemeUtils.dp2px(getContext(), 10);
            b.setPadding(p10, p15, p10, p15);
            final Uri u;
            String s = uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                u = Uri.fromFile(f);
            } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                String id;
                if (DocumentsContract.isDocumentUri(getContext(), uri))
                    id = DocumentsContract.getDocumentId(uri);
                else
                    id = DocumentsContract.getTreeDocumentId(uri);
                String[] ss = id.split(":");
                u = DocumentsContract.buildDocumentUriUsingTree(uri, ss[0] + ":" + f.getPath());
            } else {
                throw new Storage.UnknownUri();
            }
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    l.setUri(u);
                }
            });
            ll.addView(b, 0);
            f = f.getParentFile();
            if (f != null) {
                b = new TextView(getContext());
                b.setText(">");
                ViewCompat.setAlpha(b, 0.3f);
                ll.addView(b, 0);
            }
        }
    }
}
