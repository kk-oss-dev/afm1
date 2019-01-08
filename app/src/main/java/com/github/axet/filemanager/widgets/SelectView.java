package com.github.axet.filemanager.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.support.annotation.Keep;
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
import com.github.axet.filemanager.fragments.FilesFragment;

@Keep
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

        FilesFragment.hideMenu(menu, R.id.action_rename);

        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            AppCompatImageButton image = new AppCompatImageButton(getContext(), null, R.attr.toolbarNavigationButtonStyle);
            image.setId(item.getItemId());
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
            image.setVisibility(item.isVisible() ? VISIBLE : GONE);
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

    public void hide(int id) {
        View v = findViewById(id);
        v.setVisibility(GONE);
    }
}
