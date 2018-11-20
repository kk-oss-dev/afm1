package com.github.axet.filemanager.widgets;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.GravityCompat;
import android.support.v7.view.CollapsibleActionView;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.view.Menu;

import com.github.axet.filemanager.R;

public class SelectView extends LinearLayoutCompat implements CollapsibleActionView {
    public AppCompatImageButton rename;
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

    public void create() { // TODO send onOptionSelected events
        LayoutParams lp;

        rename = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        rename.setImageResource(R.drawable.ic_edit_black_24dp);
        rename.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        rename.setLayoutParams(lp);
        addView(rename);

        copy = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        copy.setImageResource(R.drawable.ic_content_copy_black_24dp);
        copy.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        copy.setLayoutParams(lp);
        addView(copy);

        cut = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        cut.setImageResource(R.drawable.ic_content_cut_black_24dp);
        cut.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        cut.setLayoutParams(lp);
        addView(cut);

        delete = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
        delete.setImageResource(R.drawable.ic_delete_black_24dp);
        delete.setColorFilter(Color.WHITE);
        lp = generateDefaultLayoutParams();
        lp.gravity = GravityCompat.START;
        delete.setLayoutParams(lp);
        addView(delete);
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
