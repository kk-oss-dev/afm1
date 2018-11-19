package com.github.axet.filemanager.fragments;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activitites.MainActivity;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.SuperUser;
import com.github.axet.filemanager.services.StorageProvider;
import com.github.axet.filemanager.widgets.PathView;
import com.github.axet.filemanager.widgets.SelectView;
import com.github.axet.wget.SpeedInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FilesFragment extends Fragment {
    public static final int RESULT_PERMS = 1;

    public static final String PASTE_UPDATE = FilesFragment.class.getCanonicalName() + ".PASTE_UPDATE";

    Handler handler = new Handler();
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
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(PASTE_UPDATE)) {
                adapter.notifyDataSetChanged();
            }
        }
    };

    public static List<String> splitPath(String s) {
        return new ArrayList<>(Arrays.asList(s.split("[//\\\\]")));
    }

    public static String stripRight(String str, String right) {
        if (str.endsWith(right))
            str = str.substring(0, str.length() - right.length());
        return str;
    }

    public static class PendingOperation {
        Context context;
        ContentResolver resolver;
        Storage storage;

        int calcIndex;
        ArrayList<Uri> calcs;
        Uri calcUri;

        int filesIndex;
        ArrayList<NativeFile> files = new ArrayList<>();

        InputStream is;
        OutputStream os;
        SpeedInfo info; // speed info
        long current; // current file transfers
        long processed; // processed files bytes
        long total; // total size of all files

        boolean readonly; // delete/overwrite file marked readonly without confarmation?
        boolean small; // overwrite file smaller then source
        boolean newer; // overwrite same size file but newer date

        byte[] buf = new byte[SuperUser.BUF_SIZE];

        public PendingOperation(Context context, Uri root, ArrayList<Uri> ff) {
            this.context = context;
            this.resolver = context.getContentResolver();
            this.storage = new Storage(context);
            calcIndex = 0;
            calcs = ff;
            calcUri = root;
        }

        public boolean calc() {
            calc(calcs.get(calcIndex));
            calcIndex++;
            return calcIndex < calcs.size();
        }

        public void calc(Uri uri) {
            String s = uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File r = Storage.getFile(calcUri);
                File f = Storage.getFile(uri);
                files.add(new NativeFile(uri, f.getPath().substring(r.getPath().length()), f.isDirectory(), f.length()));
                total += f.length();
                if (f.isDirectory()) {
                    File[] kk = f.listFiles();
                    if (kk != null) {
                        for (File k : kk)
                            calc(Uri.fromFile(k));
                    }
                }
            } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                String r;
                if (DocumentsContract.isDocumentUri(context, calcUri))
                    r = DocumentsContract.getDocumentId(calcUri);
                else
                    r = DocumentsContract.getTreeDocumentId(calcUri);
                r = stripRight(r, "/");
                ContentResolver resolver = context.getContentResolver();
                Cursor cursor = resolver.query(uri, null, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String id = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        String type = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
                        String name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        long size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                        boolean d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                        files.add(new NativeFile(uri, id.substring(r.length()), d, size));
                        total += size;
                        if (d) {
                            Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
                            Cursor cursor2 = resolver.query(doc, null, null, null, null);
                            if (cursor2 != null) {
                                while (cursor2.moveToNext()) {
                                    id = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                                    Uri child = DocumentsContract.buildDocumentUriUsingTree(doc, id);
                                    calc(child);
                                }
                                cursor2.close();
                            }
                        }
                    }
                    cursor.close();
                }
            } else {
                throw new Storage.UnknownUri();
            }
        }

        public void open(NativeFile f, Uri to) throws IOException {
            String s = f.uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(f.uri);
                is = new FileInputStream(k);
            } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                is = resolver.openInputStream(f.uri);
            } else {
                throw new Storage.UnknownUri();
            }
            s = to.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(to);
                File m = new File(k, f.name);
                os = new FileOutputStream(m);
            } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                Uri doc = storage.createFile(to, f.name);
                os = resolver.openOutputStream(doc);
            } else {
                throw new Storage.UnknownUri();
            }
            current = 0;
        }

        public void delete(NativeFile f) {
            storage.delete(f.uri);
        }

        public void close(NativeFile f) throws IOException {
            if (is != null) {
                is.close();
                is = null;
            }
            if (os != null) {
                os.close();
                os = null;
            }
        }

        public void mkdirs(NativeFile f, Uri to) {
            String s = f.uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(to);
                File m = new File(k, f.name);
                m.mkdirs();
            } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                storage.createFolder(to, f.name);
            } else {
                throw new Storage.UnknownUri();
            }
        }

        public boolean copy() throws IOException {
            int len;
            if ((len = is.read(buf)) < 0)
                return false;
            os.write(buf, 0, len);
            current += len;
            processed += len;
            return true;
        }
    }

    public static class PasteBuilder extends AlertDialog.Builder {
        View v;

        TextView copy;
        TextView from;
        TextView to;
        ProgressBar progressFile;
        ProgressBar progressTotal;
        TextView filesCount;
        TextView filesTotal;

        AlertDialog d;

        public PasteBuilder(Context context) {
            super(context);
        }

        @Override
        public AlertDialog create() {
            setCancelable(false);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            v = inflater.inflate(R.layout.paste, null);
            setView(v);
            setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            setNeutralButton("Pause", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            copy = (TextView) v.findViewById(R.id.copy);
            from = (TextView) v.findViewById(R.id.from);
            to = (TextView) v.findViewById(R.id.to);
            progressFile = (ProgressBar) v.findViewById(R.id.progress_file);
            progressTotal = (ProgressBar) v.findViewById(R.id.progress_total);
            filesCount = (TextView) v.findViewById(R.id.files_count);
            filesTotal = (TextView) v.findViewById(R.id.files_size);

            setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    PasteBuilder.this.onDismiss();
                }
            });

            d = super.create();
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = d.getButton(DialogInterface.BUTTON_NEGATIVE);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onNegative();
                        }
                    });
                    b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onNeutral();
                        }
                    });
                }
            });
            return d;
        }

        public void onDismiss() {
        }

        public void onNeutral() {
        }

        public void onNegative() {
        }

        @Override
        public AlertDialog show() {
            return super.show();
        }

        public void post(Throwable e) {
            ;
        }

        public void postDismiss() {
            ;
        }
    }

    public static class SortByName implements Comparator<NativeFile> { // by name files first
        @Override
        public int compare(NativeFile o1, NativeFile o2) {
            int c = Boolean.valueOf(o2.dir).compareTo(o1.dir);
            if (c != 0)
                return c;
            return o1.name.compareTo(o2.name);
        }
    }

    public static class SortDelete implements Comparator<NativeFile> { // deepest files first
        @Override
        public int compare(NativeFile o1, NativeFile o2) {
            int c = Boolean.valueOf(o1.dir).compareTo(o2.dir);
            if (c != 0)
                return c;
            return Integer.valueOf(splitPath(o2.name).size()).compareTo(splitPath(o1.name).size());
        }
    }

    public static class NativeFile {
        public boolean dir;
        public String name;
        public long size;
        public Uri uri;

        public NativeFile(Uri uri, String n, boolean dir, long size) {
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
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(receiver);
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

        IntentFilter filter = new IntentFilter();
        filter.addAction(PASTE_UPDATE);
        getContext().registerReceiver(receiver, filter);

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
        adapter.notifyDataSetChanged();
        Intent intent = new Intent(PASTE_UPDATE);
        getContext().sendBroadcast(intent);
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
                        adapter.files.add(new NativeFile(Uri.fromFile(f), f.getName(), f.isDirectory(), f.length()));
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
                    adapter.files.add(new NativeFile(u, name, type.equals(DocumentsContract.Document.MIME_TYPE_DIR), size));
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
        final SelectView select = (SelectView) MenuItemCompat.getActionView(toolbar);
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
                app.uri = uri;
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
                app.uri = uri;
                updatePaste();
                closeSelection();
                Toast.makeText(getContext(), "Cut " + app.cut.size() + " files", Toast.LENGTH_SHORT).show();
            }
        });
        select.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Delete");
                builder.setMessage(R.string.are_you_sure);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PendingOperation op = new PendingOperation(getContext(), uri, selected);
                        while (op.calc())
                            ;
                        Collections.sort(op.files, new SortDelete());
                        while (op.filesIndex < op.files.size()) {
                            NativeFile u = op.files.get(op.filesIndex);
                            storage.delete(u.uri);
                            op.filesIndex++;
                        }
                        closeSelection();
                        load();
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            startActivity(open);
            return true;
        }
        if (id == R.id.action_openimage) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "image/*");
            startActivity(open);
            return true;
        }
        if (id == R.id.action_openaudio) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "audio/*");
            startActivity(open);
            return true;
        }
        if (id == R.id.action_view) {
            return true;
        }
        if (id == R.id.action_rename) {
            final Intent intent = item.getIntent();
            final OpenFileDialog.EditTextDialog dialog = new OpenFileDialog.EditTextDialog(getContext());
            dialog.setTitle("Rename");
            dialog.setText(storage.getName(intent.getData()));
            dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    storage.rename(intent.getData(), dialog.getText());
                    load();
                }
            });
            dialog.show();
            return true;
        }
        if (id == R.id.action_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Delete");
            builder.setMessage(R.string.are_you_sure);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = item.getIntent();
                    storage.delete(intent.getData());
                    load();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            builder.show();
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
        if (id == R.id.action_paste) {
            paste();
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

    public void paste() {
        ArrayList<Uri> ff = null;
        if (app.copy != null)
            ff = app.copy;
        if (app.cut != null)
            ff = app.cut;
        final PendingOperation op = new PendingOperation(getContext(), app.uri, ff);
        final PasteBuilder builder = new PasteBuilder(getContext()) {
            @Override
            public void onNeutral() {
                pasteConflict();
            }

            @Override
            public void onDismiss() {
                load();
            }

            @Override
            public void onNegative() {
                d.dismiss();
            }
        };
        String n = "Paste";
        if (app.copy != null)
            n = "Copying";
        if (app.cut != null)
            n = "Move";
        builder.setTitle(n);
        final AlertDialog d = builder.create();

        Thread thread = new Thread() {
            @Override
            public void run() {
                while (op.calc())
                    ;
                Collections.sort(op.files, new SortDelete());
                while (op.filesIndex < op.files.size()) {
                    NativeFile f = op.files.get(op.filesIndex);
                    try {
                        if (f.dir) {
                            op.mkdirs(f, uri);
                        } else {
                            op.open(f, uri);
                            while (op.copy())
                                ;
                            op.close(f);
                        }
                        if (app.cut != null)
                            op.delete(f);
                    } catch (IOException e) {
                        builder.post(e);
                    }
                    op.filesIndex++;
                }
                builder.postDismiss();
            }
        };
        thread.start();

        d.show();
    }

    public void pasteConflict() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        String n = "Paste";
        if (app.copy != null)
            n = "Copying conflict";
        if (app.cut != null)
            n = "Move conflict";
        builder.setTitle(n);
        builder.setView(R.layout.paste_conflict);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ;
            }
        });
        builder.show();
    }
}
