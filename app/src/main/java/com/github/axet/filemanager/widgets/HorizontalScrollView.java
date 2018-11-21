package com.github.axet.filemanager.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class HorizontalScrollView extends android.widget.HorizontalScrollView {
    public boolean wrap = false;

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
        if (!isFillViewport()) {
            scrollTo(0, 0);
            View child = getChildAt(0);
            int w = r - l;
            int h = b - t;
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        } else {
            super.onLayout(changed, l, t, r, b);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isFillViewport())
            return false;
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isFillViewport())
            return false;
        View child = getChildAt(0);
        if (child.getMeasuredWidth() <= getWidth())
            return false;
        return super.onInterceptTouchEvent(ev);
    }
}
