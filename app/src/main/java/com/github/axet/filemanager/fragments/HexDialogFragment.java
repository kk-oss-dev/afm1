package com.github.axet.filemanager.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activities.FullscreenActivity;
import com.github.axet.filemanager.app.Storage;

import java.util.ArrayList;
import java.util.Collections;

public class HexDialogFragment extends DialogFragment {
    public static final String CHANGED = HexDialogFragment.class.getCanonicalName() + ".CHANGED";

    Uri uri;
    ViewPager pager;
    View v;
    PagerAdapter adapter;
    AlertDialog d;
    Storage storage;
    Storage.Nodes nodes;
    Toast old;
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(CHANGED))
                uri = intent.getParcelableExtra("uri");
        }
    };

    public static class PagerAdapter extends FragmentStatePagerAdapter {
        Context context;
        HexFragment left;
        MediaFragment right;
        Uri uri;

        public PagerAdapter(Context context, FragmentManager fm, Uri uri) {
            super(fm);
            this.context = context;
            update(uri);
        }

        public void update(Uri uri) {
            if (this.uri != null && this.uri.equals(uri))
                return;
            this.uri = uri;
            left = HexFragment.newInstance(uri);
            right = MediaFragment.newInstance(uri);
            notifyDataSetChanged();
        }

        @Override
        public Fragment getItem(int i) {
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
        public int getItemPosition(Object object) {
            return POSITION_NONE;
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

    public static HexDialogFragment create(Uri t, boolean panel) {
        HexDialogFragment f = new HexDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", t);
        args.putBoolean("panel", panel);
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uri = getArguments().getParcelable("uri");
        storage = new Storage(getContext());
        final Uri p = Storage.getParent(getContext(), uri);
        ArrayList<Storage.Node> nn = storage.list(p);
        nodes = new Storage.Nodes(nn, false);
        Collections.sort(nodes, new FilesFragment.SortByName());
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

        d = builder.create();

        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                if (adapter.right.supported)
                    pager.setCurrentItem(1, false);
            }
        });

        IntentFilter ff = new IntentFilter(CHANGED);
        getContext().registerReceiver(receiver, ff);

        return d;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(receiver);
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
        v = inflater.inflate(R.layout.hex_dialog, container, false);

        pager = (ViewPager) v.findViewById(R.id.pager);
        adapter = new PagerAdapter(getContext(), getChildFragmentManager(), uri);
        pager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) v.findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(pager);

        pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        d.setView(view); // with out setView api10 crash due to wrapping of child view
    }

    @Override
    public void onResume() {
        super.onResume();
        updateButtons();
        load(uri);
    }

    public void load(Uri uri) {
        this.uri = uri;
        adapter.update(uri);
        if (old != null)
            old.cancel();
        old = Toast.makeText(getContext(), storage.getName(uri), Toast.LENGTH_SHORT);
        old.show();
    }

    public void updateButtons() {
        View left = v.findViewById(R.id.left);
        View right = v.findViewById(R.id.right);
        View fullscreen = v.findViewById(R.id.fullscreen);
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = nodes.find(uri) - 1;
                if (i < 0)
                    i = 0;
                load(nodes.get(i).uri);
            }
        });
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = nodes.find(uri) + 1;
                int last = nodes.size() - 1;
                if (i >= last)
                    i = last;
                load(nodes.get(i).uri);
            }
        });
        fullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FullscreenActivity.start(getContext(), uri);
            }
        });

        if (!getArguments().getBoolean("panel", false)) {
            left.setVisibility(View.GONE);
            right.setVisibility(View.GONE);
            fullscreen.setVisibility(View.GONE);
        }
    }
}
