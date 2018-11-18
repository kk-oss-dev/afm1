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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activitites.MainActivity;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.SuperUser;
import com.github.axet.filemanager.services.StorageProvider;
import com.github.axet.filemanager.widgets.PathView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilesFragment extends Fragment {
    public static final int RESULT_PERMS = 1;

    Uri uri;
    Adapter adapter;
    Storage storage;
    PathView path;
    View button;

    public static class SortByName implements Comparator<File> {
        @Override
        public int compare(File o1, File o2) {
            int c = Boolean.valueOf(o2.dir).compareTo(o1.dir);
            if (c != 0)
                return c;
            return o1.name.compareTo(o2.name);
        }
    }

    public static class File {
        public boolean dir;
        public String name;
        public long size;
        public Uri uri;

        public File(Uri uri, String n, boolean dir, boolean s, long size) {
            this.uri = uri;
            this.name = n;
            this.dir = dir;
            this.size = size;
        }

        public File(java.io.File f) {
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

        public Holder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            name = (TextView) itemView.findViewById(R.id.name);
        }
    }

    public class Adapter extends RecyclerView.Adapter<Holder> {
        ArrayList<File> files = new ArrayList<>();

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(getContext()).inflate(R.layout.file_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final Holder h, final int position) {
            final File f = files.get(position);
            if (f.dir)
                h.icon.setImageResource(R.drawable.ic_folder_open_black_24dp);
            else
                h.icon.setImageResource(R.drawable.ic_file);
            h.name.setText(f.name);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (f.dir)
                        load(f.uri);
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (f.dir)
                        return false;
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
                    return true;
                }
            });
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
        uri = getUri();
        storage = new Storage(getContext());
        adapter = new Adapter();
        load();
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

        button = rootView.findViewById(R.id.permissions);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Storage.permitted(FilesFragment.this, Storage.PERMISSIONS_RW, RESULT_PERMS);
            }
        });
        updateButton();

        return rootView;
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
            final MainActivity main = (MainActivity) getActivity();
            main.update();
        }
    }

    public void load() {
        adapter.files.clear();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                ArrayList<java.io.File> ff = SuperUser.ls(uri);
                for (java.io.File f : ff)
                    adapter.files.add(new File(f));
            } else {
                java.io.File file = Storage.getFile(uri);
                java.io.File[] ff = file.listFiles();
                if (ff != null) {
                    for (java.io.File f : ff) {
                        boolean sym = false;
                        try {
                            sym = FileUtils.isSymlink(f);
                        } catch (IOException e) {
                        }
                        adapter.files.add(new File(Uri.fromFile(f), f.getName(), f.isDirectory(), sym, f.length()));
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
                    adapter.files.add(new File(u, name, type.equals(DocumentsContract.Document.MIME_TYPE_DIR), false, size));
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
        return super.onOptionsItemSelected(item);
    }
}
