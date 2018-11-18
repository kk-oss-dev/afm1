package com.github.axet.filemanager.widgets;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.GravityCompat;
import android.support.v7.view.CollapsibleActionView;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.view.View;

import com.github.axet.filemanager.R;

public class SelectView extends LinearLayoutCompat implements CollapsibleActionView {
    public AppCompatImageButton open;
    public AppCompatImageButton share;
    public AppCompatImageButton copy;
    public AppCompatImageButton cut;
    public AppCompatImageButton delete;

    public CollapsibleActionView listener;

    public SelectView(Context context) {
        super(context);
        create();
    }

    public SelectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public SelectView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public void create() {
        open = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        open.setImageResource(R.drawable.ic_open_in_new_black_24dp);
        open.setColorFilter(Color.WHITE);
        LayoutParams lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        open.setLayoutParams(lp);
        open.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseActionView();
            }
        });
        addView(open);

        share = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        share.setImageResource(R.drawable.ic_share_black_24dp);
        share.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        share.setLayoutParams(lp);
        share.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseActionView();
            }
        });
        addView(share);

        copy = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        copy.setImageResource(R.drawable.ic_content_copy_black_24dp);
        copy.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        copy.setLayoutParams(lp);
        copy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseActionView();
            }
        });
        addView(copy);

        cut = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        cut.setImageResource(R.drawable.ic_content_cut_black_24dp);
        cut.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        cut.setLayoutParams(lp);
        cut.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseActionView();
            }
        });
        addView(cut);

        delete = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        delete.setImageResource(R.drawable.ic_delete_black_24dp);
        delete.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        delete.setLayoutParams(lp);
        delete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseActionView();
            }
        });
        addView(delete);
    }

    public void collapseActionView() {
    }

    @Override
    public void onActionViewExpanded() {
        if (listener != null)
            listener.onActionViewExpanded();
    }

    @Override
    public void onActionViewCollapsed() {
        if (listener != null)
            listener.onActionViewCollapsed();
    }
}
