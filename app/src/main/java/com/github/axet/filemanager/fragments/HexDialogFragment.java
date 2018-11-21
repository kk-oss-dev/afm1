package com.github.axet.filemanager.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.github.axet.filemanager.R;

public class HexDialogFragment extends DialogFragment {
    ViewPager pager;
    View v;
    Button play;
    TorrentPagerAdapter adapter;

    public static class TorrentPagerAdapter extends FragmentPagerAdapter {
        Context context;
        HexFragment left;
        MediaFragment right;

        public TorrentPagerAdapter(Context context, FragmentManager fm, Uri uri) {
            super(fm);
            this.context = context;
            left = HexFragment.newInstance(uri);
            right = MediaFragment.newInstance(uri);
        }

        @Override
        public Fragment getItem(int i) {
            Fragment f;

            switch (i) {
                case 0:
                    return left;
                case 1:
                    return right;
                default:
                    return null;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Hex";
                case 1:
                    return "Media";
                default:
                    return "EMPTY";
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    }

    public static HexDialogFragment create(Uri t) {
        HexDialogFragment f = new HexDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", t);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        final Activity activity = getActivity();
        if (activity instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        pager = null;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(getContext().getString(R.string.close),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                }
        );
        builder.setView(createView(LayoutInflater.from(getContext()), null, savedInstanceState));

        final AlertDialog d = builder.create();

        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                if (adapter.right.supported)
                    pager.setCurrentItem(1, false);
                play = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                play.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    }
                });
            }
        });
        return d;
    }

    @Nullable
    @Override
    public View getView() {
        return null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return v;
    }

    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.hex_dialog, container);

        Uri uri = getArguments().getParcelable("uri");

        pager = (ViewPager) v.findViewById(R.id.pager);
        adapter = new TorrentPagerAdapter(getContext(), getChildFragmentManager(), uri);
        pager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) v.findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(pager);

        pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
