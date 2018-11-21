package com.github.axet.filemanager.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;

public class HorizontalScrollView extends android.widget.HorizontalScrollView {
    public HorizontalScrollView(Context context) {
        super(context);
    }

    public HorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public HorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (isFillViewport()) {
            scrollTo(0, 0);
            getChildAt(0).layout(0, 0, r - l, b - t);
        } else {
            super.onLayout(changed, l, t, r, b);
        }
    }
}
