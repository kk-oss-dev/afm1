package com.github.axet.filemanager.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.androidlibrary.services.StorageProvider;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.androidlibrary.widgets.ToolbarActionView;
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
            try {
                search.calcs.subList(0, search.calcIndex).clear();
                search.calcIndex = 0;

                if (search.calcIndex < search.calcs.size() && search.calc()) {
                    handler.post(this);
                    if (old == null)
                        old = Snackbar.make(getActivity().findViewById(android.R.id.content), "", Snackbar.LENGTH_LONG);
                    old.setText(Storage.getDisplayName(getContext(), search.files.get(search.files.size() - 1).uri));
                    old.show();
                    return;
                }
                storage.closeSu();
            } catch (RuntimeException e) {
                Log.d(TAG, "search error", e);
                search.calcIndex++;
                handler.post(this);
            }
        }
    };

    public class SearchAdapter extends Adapter {
        public SearchAdapter(Context context) {
            super(context);
        }

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
                        ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_open);
                        ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_share);
                        ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_view);
                        ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_openas);
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
        adapter = new SearchAdapter(getContext());
        adapter.files = nodes;
        String q = getArguments().getString("q");
        if (q.contains("*"))
            pattern = Pattern.compile(Storage.wildcard(q), Pattern.CASE_INSENSITIVE);
        else
            pattern = Pattern.compile(q, Pattern.CASE_INSENSITIVE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        search.close();
        Storage.SAF_CACHE.remove(SearchFragment.this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        uri = getUri(); // set uri after view created

        list = new RecyclerView(getContext());
        list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        try {
            search = new PendingOperation(getContext()) {
                @Override
                public void walk(Uri uri) {
                    ArrayList<Storage.Node> nn = storage.walk(calcUri, uri);
                    for (Storage.Node n : nn) {
                        if (n.dir) {
                            if (n.uri.equals(uri)) // walk return current dirs, do not follow it
                                process(uri, n);
                            else
                                calcs.add(n);
                        } else {
                            process(uri, n);
                            total += n.size;
                        }
                    }
                }
            };
            search.calcUri = uri;
            search.calcs = new ArrayList<>();
            search.walk(uri);
            calc.run();
        } catch (RuntimeException e) {
            Log.e(TAG, "io", e);
            Toast.Text(getContext(), ErrorDialog.toMessage(e));
        }

        return list;
    }

    void process(Uri root, Storage.Node n) {
        Matcher m = pattern.matcher(n.name);
        if (m.find()) {
            if (!root.equals(n.uri)) // do not current dir (we do not know its root, and it is already in list)
                Storage.SAF_CACHE.get(SearchFragment.this).put(n.uri, root);
            nodes.add(n);
            adapter.notifyDataSetChanged();
        }
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

    public boolean isActive() {
        return search.calcIndex < search.calcs.size();
    }

    public void stop() {
        handler.removeCallbacks(calc);
        search.storage.closeSu();
        storage.closeSu();
    }
}
