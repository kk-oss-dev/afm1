package com.github.axet.filemanager.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.support.v4.view.GravityCompat;
import android.support.v7.view.CollapsibleActionView;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import com.github.axet.filemanager.R;

public class SelectView extends LinearLayoutCompat implements CollapsibleActionView {
    public MenuBuilder menu;
    public CollapsibleActionView listener;

    public static Activity from(Context context) {
        if (context instanceof Activity)
            return (Activity) context;
        if (context instanceof ContextWrapper)
            return from(((ContextWrapper) context).getBaseContext());
        throw new RuntimeException("unknown context");
    }

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

    @SuppressLint("RestrictedApi")
    public void create() {
        menu = new MenuBuilder(getContext());

        final Activity a = from(getContext());
        a.getMenuInflater().inflate(R.menu.menu_select, menu);

        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            AppCompatImageButton image = new AppCompatImageButton(getContext(), null, android.support.v7.appcompat.R.attr.toolbarNavigationButtonStyle);
            image.setImageDrawable(item.getIcon());
            image.setColorFilter(Color.WHITE);
            LayoutParams lp = generateDefaultLayoutParams();
            lp.gravity = GravityCompat.START;
            image.setLayoutParams(lp);
            image.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
                }
            });
            addView(image);
        }
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
