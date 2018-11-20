package com.github.axet.filemanager.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

public class HexView extends View {
    public HexView(Context context) {
        super(context);
    }

    public HexView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HexView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public HexView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
