package com.github.axet.filemanager.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.androidlibrary.services.StorageProvider;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activities.MainActivity;
import com.github.axet.filemanager.app.Storage;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchFragment extends FilesFragment {
    public static final String TAG = SearchFragment.class.getSimpleName();
    PendingOperation search;
    Pattern pattern;
    Storage.Nodes nodes = new Storage.Nodes();
    Runnable calc = new Runnable() {
        Snackbar old;

        @Override
        public void run() {
            if (search.calc()) {
                handler.post(this);
                if (old == null)
                    old = Snackbar.make(getActivity().findViewById(android.R.id.content), "", Snackbar.LENGTH_LONG);
                old.setText(storage.getDisplayName(search.files.get(search.files.size() - 1).uri));
                old.show();
                while (search.filesIndex < search.files.size()) {
                    Storage.Node n = search.files.get(search.filesIndex);
                    Matcher m = pattern.matcher(n.name);
                    if (m.find())
                        nodes.add(n);
                    search.filesIndex++;
                }
                adapter.notifyDataSetChanged();
            }
        }
    };

    public class SearchAdapter extends Adapter {
        @Override
        public void onBindViewHolder(Holder h, int position) {
            super.onBindViewHolder(h, position);
            final Storage.Node f = files.get(position);
            h.circleFrame.setVisibility(View.GONE);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu menu = new PopupMenu(getContext(), v);
                    menu.inflate(R.menu.menu_file);
                    if (f.dir) {
                        hideMenu(menu.getMenu(), R.id.action_open);
                        hideMenu(menu.getMenu(), R.id.action_share);
                        hideMenu(menu.getMenu(), R.id.action_view);
                        hideMenu(menu.getMenu(), R.id.action_openas);
                    }
                    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            String type;
                            Uri uri;
                            if (!f.dir && item.getItemId() == R.id.action_folder) {
                                type = StorageProvider.CONTENTTYPE_FOLDER;
                                uri = Storage.getParent(getContext(), f.uri);
                            } else {
                                type = Storage.getTypeByName(f.name);
                                uri = f.uri;
                            }
                            intent.setDataAndType(uri, type);
                            intent.putExtra("name", f.name);
                            item.setIntent(intent);
                            return onOptionsItemSelected(item);
                        }
                    });
                    menu.show();
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return false;
                }
            });
        }
    }

    public SearchFragment() {
    }

    public static SearchFragment newInstance(Uri uri, String q) {
        SearchFragment fragment = new SearchFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        args.putString("q", q);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        adapter = new SearchAdapter();
        adapter.files = nodes;
        String q = getArguments().getString("q");
        if (q.contains("*"))
            pattern = Pattern.compile(Storage.wildcard(q));
        else
            pattern = Pattern.compile(q);
        search = new PendingOperation(getContext());
        search.calcUri = uri;
        search.calcs = new ArrayList<>();
        search.walk(uri);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        list = new RecyclerView(getContext());
        list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        calc.run();
        return list;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void reload() {
    }

    @Override
    void updateButton() {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_view) {
            Uri uri = item.getIntent().getData();
            MainActivity main = (MainActivity) getActivity();
            main.openHex(uri, false);
            return true;
        }
        if (id == R.id.action_folder) {
            Uri uri = item.getIntent().getData();
            MainActivity main = (MainActivity) getActivity();
            main.open(uri);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
