package com.github.axet.filemanager.fragments;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.view.CollapsibleActionView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activitites.MainActivity;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.SuperUser;
import com.github.axet.filemanager.services.StorageProvider;
import com.github.axet.filemanager.widgets.PathView;
import com.github.axet.filemanager.widgets.SelectView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class FilesFragment extends Fragment {
    public static final int RESULT_PERMS = 1;

    FilesApplication app;
    Uri uri;
    Adapter adapter;
    Storage storage;
    PathView path;
    View button;
    TextView error;
    MenuItem toolbar;
    MenuItem paste;
    MenuItem pasteCancel;
    ArrayList<Uri> selected = new ArrayList<>();

    public static class SortByName implements Comparator<NativeFile> {
        @Override
        public int compare(NativeFile o1, NativeFile o2) {
            int c = Boolean.valueOf(o2.dir).compareTo(o1.dir);
            if (c != 0)
                return c;
            return o1.name.compareTo(o2.name);
        }
    }

    public static class NativeFile {
        public boolean dir;
        public String name;
        public long size;
        public Uri uri;

        public NativeFile(Uri uri, String n, boolean dir, boolean s, long size) {
            this.uri = uri;
            this.name = n;
            this.dir = dir;
            this.size = size;
        }

        public NativeFile(File f) {
            this.uri = Uri.fromFile(f);
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public class Holder extends RecyclerView.ViewHolder {
        public ImageView icon;
        public TextView name;
        public View circle;
        public View unselected;
        public View selected;

        public Holder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            name = (TextView) itemView.findViewById(R.id.name);
            circle = itemView.findViewById(R.id.circle);
            unselected = itemView.findViewById(R.id.unselected);
            selected = itemView.findViewById(R.id.selected);
        }
    }

    public class Adapter extends RecyclerView.Adapter<Holder> {
        ArrayList<NativeFile> files = new ArrayList<>();

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(getContext()).inflate(R.layout.file_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final Holder h, final int position) {
            final NativeFile f = files.get(position);
            if (f.dir)
                h.icon.setImageResource(R.drawable.ic_folder_open_black_24dp);
            else
                h.icon.setImageResource(R.drawable.ic_file);
            h.name.setText(f.name);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (MenuItemCompat.isActionViewExpanded(toolbar)) {
                        if (selected.contains(f.uri))
                            selected.remove(f.uri);
                        else
                            selected.add(f.uri);
                        notifyDataSetChanged();
                        return;
                    }
                    if (f.dir) {
                        load(f.uri);
                    } else {
                        PopupMenu menu = new PopupMenu(getContext(), v);
                        menu.inflate(R.menu.menu_file);
                        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                String type = Storage.getTypeByName(f.name);
                                intent.setDataAndType(f.uri, type);
                                intent.putExtra("name", f.name);
                                item.setIntent(intent);
                                return onOptionsItemSelected(item);
                            }
                        });
                        menu.show();
                    }
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    selected.add(f.uri);
                    openSelection();
                    return true;
                }
            });
            h.circle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selected.add(f.uri);
                    openSelection();
                }
            });
            ViewCompat.setAlpha(h.selected, 1f);
            if (toolbar != null && MenuItemCompat.isActionViewExpanded(toolbar)) {
                h.circle.setVisibility(View.INVISIBLE);
                if (selected.contains(f.uri)) {
                    h.unselected.setVisibility(View.INVISIBLE);
                    h.selected.setVisibility(View.VISIBLE);
                } else {
                    h.unselected.setVisibility(View.VISIBLE);
                    h.selected.setVisibility(View.INVISIBLE);
                }
            } else {
                if (pending(f.uri)) {
                    h.circle.setVisibility(View.INVISIBLE);
                    h.selected.setVisibility(View.VISIBLE);
                    ViewCompat.setAlpha(h.selected, 0.3f);
                    h.unselected.setVisibility(View.INVISIBLE);
                } else {
                    h.circle.setVisibility(View.VISIBLE);
                    h.selected.setVisibility(View.INVISIBLE);
                    h.unselected.setVisibility(View.INVISIBLE);
                }
            }
        }

        public boolean pending(Uri u) {
            return app.copy != null && app.copy.contains(u) || app.cut != null && app.cut.contains(u);
        }

        @Override
        public int getItemCount() {
            return files.size();
        }
    }

    public FilesFragment() {
    }

    public static FilesFragment newInstance(Uri uri) {
        FilesFragment fragment = new FilesFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        app = FilesApplication.from(getContext());
        uri = getUri();
        storage = new Storage(getContext());
        adapter = new Adapter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        path = (PathView) rootView.findViewById(R.id.path);
        path.l = new PathView.Listener() {
            @Override
            public void setUri(Uri u) {
                load(u);
            }
        };
        path.setUri(uri);

        RecyclerView list = (RecyclerView) rootView.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        error = (TextView) rootView.findViewById(R.id.error);
        error.setVisibility(View.GONE);

        button = rootView.findViewById(R.id.permissions);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Storage.permitted(FilesFragment.this, Storage.PERMISSIONS_RW, RESULT_PERMS);
            }
        });
        updateButton();

        load();

        return rootView;
    }

    void updatePaste() {
        if (app.copy != null || app.cut != null) {
            paste.setVisible(true);
            pasteCancel.setVisible(true);
        } else {
            paste.setVisible(false);
            pasteCancel.setVisible(false);
        }
    }

    void updateButton() {
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (shared.getBoolean(FilesApplication.PREF_ROOT, false) || Storage.permitted(getContext(), Storage.PERMISSIONS_RW))
                button.setVisibility(View.GONE);
        } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
            button.setVisibility(View.GONE);
        } else {
            throw new Storage.UnknownUri();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_PERMS:
                load();
                updateButton();
                break;
        }
    }

    public Uri getUri() {
        if (uri == null)
            return getArguments().getParcelable("uri");
        return uri;
    }

    public void load(Uri u) {
        if (uri == null) {
            getArguments().putParcelable("uri", u);
        } else {
            uri = u;
            updateButton();
            path.setUri(uri);
            load();
            MainActivity main = (MainActivity) getActivity();
            main.update();
        }
    }

    public void load() {
        error.setVisibility(View.GONE);
        adapter.files.clear();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                try {
                    ArrayList<File> ff = SuperUser.ls(uri);
                    for (File f : ff)
                        adapter.files.add(new NativeFile(f));
                } catch (RuntimeException e) {
                    error.setText(e.getMessage());
                    error.setVisibility(View.VISIBLE);
                }
            } else {
                File file = Storage.getFile(uri);
                File[] ff = file.listFiles();
                if (ff != null) {
                    for (File f : ff) {
                        boolean sym = false;
                        try {
                            sym = FileUtils.isSymlink(f);
                        } catch (IOException e) {
                        }
                        adapter.files.add(new NativeFile(Uri.fromFile(f), f.getName(), f.isDirectory(), sym, f.length()));
                    }
                }
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            String id;
            if (DocumentsContract.isDocumentUri(getContext(), uri))
                id = DocumentsContract.getDocumentId(uri);
            else
                id = DocumentsContract.getTreeDocumentId(uri);
            Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
            ContentResolver resolver = getContext().getContentResolver();
            Cursor cursor = resolver.query(doc, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    String name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    long size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                    Uri u = DocumentsContract.buildDocumentUriUsingTree(uri, id);
                    adapter.files.add(new NativeFile(u, name, type.equals(DocumentsContract.Document.MIME_TYPE_DIR), false, size));
                }
                cursor.close();
            }
        } else {
            throw new Storage.UnknownUri();
        }
        Collections.sort(adapter.files, new SortByName());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        toolbar = menu.findItem(R.id.action_selected);
        paste = menu.findItem(R.id.action_paste);
        pasteCancel = menu.findItem(R.id.action_paste_cancel);
        updatePaste();
        SelectView select = (SelectView) MenuItemCompat.getActionView(toolbar);
        select.listener = new CollapsibleActionView() {
            @Override
            public void onActionViewExpanded() {
            }

            @Override
            public void onActionViewCollapsed() {
                adapter.notifyDataSetChanged();
                selected.clear();
            }
        };
        select.copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                app.copy = new ArrayList<>(selected);
                app.cut = null;
                updatePaste();
                closeSelection();
                Toast.makeText(getContext(), "Copied " + app.copy.size() + " files", Toast.LENGTH_SHORT).show();
            }
        });
        select.cut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                app.copy = null;
                app.cut = new ArrayList<>(selected);
                updatePaste();
                closeSelection();
                Toast.makeText(getContext(), "Cut " + app.cut.size() + " files", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            startActivity(open);
            return true;
        }
        if (id == R.id.action_share) {
            Intent intent = item.getIntent();
            Intent share = StorageProvider.getProvider().shareIntent(intent.getData(), intent.getType(), intent.getStringExtra("name"));
            startActivity(share);
            return true;
        }
        if (id == R.id.action_refresh) {
            load();
            return true;
        }
        if (id == R.id.action_paste_cancel) {
            app.copy = null;
            app.cut = null;
            updatePaste();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void openSelection() {
        app.copy = null;
        app.cut = null;
        updatePaste();
        MenuItemCompat.expandActionView(toolbar);
        adapter.notifyDataSetChanged();
    }

    public void closeSelection() {
        MenuItemCompat.collapseActionView(toolbar);
        adapter.notifyDataSetChanged();
    }
}
