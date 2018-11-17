package com.github.axet.filemanager;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class FilesFragment extends Fragment {
    public static final int RESULT_PERMS = 1;

    Uri uri;
    Adapter adapter;
    Storage storage;

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
        public boolean sym;
        public String name;
        public long size;

        public File(String n, boolean dir, boolean s, long size) {
            this.name = n;
            this.dir = dir;
            this.sym = s;
            this.size = size;
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
        public void onBindViewHolder(Holder holder, int position) {
            File f = files.get(position);
            if (f.dir)
                holder.icon.setImageResource(R.drawable.ic_folder_open_black_24dp);
            else
                holder.icon.setImageResource(R.drawable.ic_file);
            holder.name.setText(f.name);
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
        RecyclerView list = (RecyclerView) rootView.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        return rootView;
    }

    public void refresh() {
        final MainActivity main = (MainActivity) getActivity();
        if (Storage.permitted(this, Storage.PERMISSIONS_RW, RESULT_PERMS))
            main.mSectionsPagerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_PERMS:
                break;
        }
    }

    public Uri getUri() {
        if (uri == null)
            return getArguments().getParcelable("uri");
        return uri;
    }

    public void load(Uri u) {
        uri = u;
        load();
    }

    public void load() {
        adapter.files.clear();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            java.io.File file = Storage.getFile(uri);
            java.io.File[] ff = file.listFiles();
            for (java.io.File f : ff) {
                boolean sym = false;
                try {
                    sym = FileUtils.isSymlink(f);
                } catch (IOException e) {
                }
                adapter.files.add(new File(f.getName(), f.isDirectory(), sym, f.length()));
            }
        } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
            Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            ContentResolver resolver = getContext().getContentResolver();
            Cursor cursor = resolver.query(doc, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    String name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                    long size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                    adapter.files.add(new File(name, type.equals(DocumentsContract.Document.MIME_TYPE_DIR), false, size));
                }
                cursor.close();
            }
        } else {
            throw new Storage.UnknownUri();
        }
        Collections.sort(adapter.files, new SortByName());
        adapter.notifyDataSetChanged();
    }
}
