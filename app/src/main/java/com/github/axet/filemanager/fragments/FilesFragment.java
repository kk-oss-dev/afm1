package com.github.axet.filemanager.fragments;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.provider.DocumentFile;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.CollapsibleActionView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class FilesFragment extends Fragment {
    public static final String TAG = FilesFragment.class.getSimpleName();

    public static final int RESULT_PERMS = 1;

    public static final String PASTE_UPDATE = FilesFragment.class.getCanonicalName() + ".PASTE_UPDATE";
    public static final String MOVE_UPDATE = FilesFragment.class.getCanonicalName() + ".MOVE_UPDATE";

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyyMMdd\'T\'HHmmss");

    public static final NumberFormat BYTES = new DecimalFormat("###,###,###,###,###.#", new DecimalFormatSymbols() {{
        setGroupingSeparator(' ');
    }});

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
    SelectView select;
    ArrayList<Uri> selected = new ArrayList<>();
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a == null)
                return;
            if (a.equals(PASTE_UPDATE))
                adapter.notifyDataSetChanged();
            if (a.equals(MOVE_UPDATE))
                reload();
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

    public static class PendingOperation implements Runnable {
        Context context;
        ContentResolver resolver;
        Storage storage;
        SharedPreferences shared;
        Thread thread;
        Throwable delayed;

        int calcIndex;
        ArrayList<Uri> calcs;
        ArrayList<Uri> calcsStart; // initial calcs dir for UI
        Uri calcUri;

        int filesIndex;
        ArrayList<NativeFile> files = new ArrayList<>();

        InputStream is;
        OutputStream os;
        Uri t; // target file, to update last modified time or delete in case of errors

        SpeedInfo info = new SpeedInfo(); // speed info
        long current; // current file transfers
        long processed; // processed files bytes
        long total; // total size of all files

        EnumSet<OPERATION> small = EnumSet.of(OPERATION.ASK); // overwrite file smaller then source
        EnumSet<OPERATION> big = EnumSet.of(OPERATION.ASK); // overwrite file bigger then source
        EnumSet<OPERATION> newer = EnumSet.of(OPERATION.ASK); // overwrite same size file but newer date
        EnumSet<OPERATION> same = EnumSet.of(OPERATION.ASK); // same file size and date

        byte[] buf = new byte[SuperUser.BUF_SIZE];

        enum OPERATION {NONE, ASK, SKIP, OVERWRITE}

        public PendingOperation(Context context) {
            this.context = context;
            this.resolver = context.getContentResolver();
            this.storage = new Storage(context);
            this.shared = PreferenceManager.getDefaultSharedPreferences(context);
        }

        public PendingOperation(Context context, Uri root, ArrayList<Uri> ff) {
            this(context);
            calcIndex = 0;
            calcs = new ArrayList<>(ff);
            calcsStart = new ArrayList<>(ff);
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
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                    ArrayList<File> ff = SuperUser.ls(SuperUser.LSa, uri);
                    for (File f : ff) {
                        NativeFile k = new NativeFile(Uri.fromFile(f), f.getPath().substring(r.getPath().length()), f.isDirectory(), f.length(), f.lastModified());
                        files.add(k);
                        if (f.isDirectory()) {
                            if (!k.uri.equals(uri)) // LSa return current dirs, do not follow it
                                calcs.add(Uri.fromFile(f));
                        } else {
                            total += f.length();
                        }
                    }
                } else {
                    File f = Storage.getFile(uri);
                    files.add(new NativeFile(uri, f.getPath().substring(r.getPath().length()), f.isDirectory(), f.length(), f.lastModified()));
                    if (f.isDirectory()) {
                        File[] kk = f.listFiles();
                        if (kk != null) {
                            for (File k : kk)
                                calcs.add(Uri.fromFile(k));
                        }
                    } else {
                        total += f.length();
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
                        long size = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                        long last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                        boolean d = type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                        files.add(new NativeFile(uri, id.substring(r.length()), d, size, last));
                        total += size;
                        if (d) {
                            Uri doc = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
                            Cursor cursor2 = resolver.query(doc, null, null, null, null);
                            if (cursor2 != null) {
                                while (cursor2.moveToNext()) {
                                    id = cursor2.getString(cursor2.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                                    Uri child = DocumentsContract.buildDocumentUriUsingTree(doc, id);
                                    calcs.add(child);
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

        public void open(final NativeFile f, Uri to) throws IOException {
            String s = f.uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                    is = SuperUser.cat(f.uri);
                } else {
                    File k = Storage.getFile(f.uri);
                    is = new FileInputStream(k);
                }
            } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                is = resolver.openInputStream(f.uri);
            } else {
                throw new Storage.UnknownUri();
            }
            s = to.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(to);
                final File m = new File(k, f.name);
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                    os = SuperUser.write(Uri.fromFile(m));
                } else {
                    os = new FileOutputStream(m);
                }
                t = Uri.fromFile(m);
            } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                Uri doc = storage.createFile(to, f.name);
                os = resolver.openOutputStream(doc);
                t = doc;
            } else {
                throw new Storage.UnknownUri();
            }
            current = 0;
        }

        public void delete(NativeFile f) {
            String s = f.uri.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(f.uri);
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                    SuperUser.delete(k);
                } else {
                    k.delete();
                }
            } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                DocumentFile k = DocumentFile.fromSingleUri(context, f.uri);
                k.delete();
            }
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
            String s = t.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(t);
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                    SuperUser.touch(k, f.last);
                } else {
                    k.setLastModified(f.last); // not working, requiring root
                }
            } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                ; // not supported
            } else {
                throw new Storage.UnknownUri();
            }
            t = null;
        }

        public void close() {
            try {
                if (thread != null) {
                    thread.interrupt();
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    thread = null;
                }
                if (is != null) {
                    is.close();
                    is = null;
                }
                if (os != null) {
                    os.close();
                    os = null;
                }
                if (t != null) {
                    String s = t.getScheme();
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        File k = Storage.getFile(t);
                        if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                            SuperUser.delete(k);
                        } else {
                            k.delete();
                        }
                    } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                        DocumentFile f = DocumentFile.fromSingleUri(context, t);
                        f.delete();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Uri target(NativeFile f, Uri to) {
            String s = to.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(to);
                File m = new File(k, f.name);
                return Uri.fromFile(m);
            } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                Uri doc = storage.child(to, f.name);
                return doc;
            } else {
                throw new Storage.UnknownUri();
            }
        }

        public void mkdir(String name, Uri to) {
            String s = to.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(to);
                File m = new File(k, name);
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                    SuperUser.mkdir(m).must();
                } else {
                    if (!m.exists() && !m.mkdir())
                        throw new RuntimeException("unable to create dir: " + m);
                }
            } else if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                Uri doc = storage.createFolder(to, name);
                if (doc == null)
                    throw new RuntimeException("Unable to create dir: " + name);
            } else {
                throw new Storage.UnknownUri();
            }
        }

        public EnumSet<OPERATION> check(NativeFile f, NativeFile t) { // ask user for confirmations?
            if (t.size < f.size)
                return small;
            if (t.size > f.size)
                return big;
            if (t.size == f.size && t.last > f.last)
                return newer;
            if (t.size == f.size && t.last == f.last)
                return same;
            return EnumSet.of(OPERATION.NONE); // not asking
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

        @Override
        public void run() {
        }

        public void pause() {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                thread = null;
            }
        }

        public String formatStart() {
            if (calcsStart.size() == 1) {
                return storage.getDisplayName(calcsStart.get(0));
            } else {
                String str = storage.getDisplayName(calcUri) + "{";
                for (Uri u : calcsStart)
                    str += Storage.getDocumentName(u) + ",";
                str = stripRight(str, ",");
                str += "}";
                return str;
            }
        }

        public String formatCalc() {
            return storage.getDisplayName(files.get(files.size() - 1).uri);
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

        DialogInterface.OnDismissListener dismiss = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
            }
        };
        View.OnClickListener neutral = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        };
        View.OnClickListener negative = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        };

        public PasteBuilder(Context context) {
            super(context);
            setCancelable(false);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            v = inflater.inflate(R.layout.paste, null);
            setView(v);
            setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            setNeutralButton(R.string.copy_pause, new DialogInterface.OnClickListener() {
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
                    dismiss.onDismiss(dialog);
                }
            });
        }

        @Override
        public AlertDialog create() {
            d = super.create();
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = d.getButton(DialogInterface.BUTTON_NEGATIVE);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            negative.onClick(v);
                        }
                    });
                    b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            neutral.onClick(v);
                        }
                    });
                }
            });
            return d;
        }

        @Override
        public AlertDialog show() {
            return super.show();
        }

        public void dismiss() {
            d.dismiss();
        }

        public void update(PendingOperation op) {
            filesCount.setText(op.filesIndex + " / " + op.files.size());
            progressFile.setProgress(0);
            progressTotal.setProgress(0);
            filesTotal.setText(FilesApplication.formatSize(getContext(), op.processed) + " / " + FilesApplication.formatSize(getContext(), op.total));
        }

        public void update(PendingOperation op, int old, NativeFile f) {
            filesCount.setText(old + " / " + op.files.size());
            progressFile.setProgress(f.size == 0 ? 0 : (int) (op.current * 100 / f.size));
            progressTotal.setProgress(op.total == 0 ? 0 : (int) (op.processed * 100 / op.total));
            filesTotal.setText(FilesApplication.formatSize(getContext(), op.processed) + " / " + FilesApplication.formatSize(getContext(), op.total));
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
        public long last;

        public NativeFile(Uri uri, String n, boolean dir, long size, long last) {
            this.uri = uri;
            this.name = n;
            this.dir = dir;
            this.size = size;
            this.last = last;
        }

        public NativeFile(File f) {
            this.uri = Uri.fromFile(f);
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
        }

        public NativeFile(DocumentFile f) {
            this.uri = f.getUri();
            this.name = f.getName();
            this.dir = f.isDirectory();
            this.size = f.length();
            this.last = f.lastModified();
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
                        updateSelection();
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

        reload();

        IntentFilter filter = new IntentFilter();
        filter.addAction(PASTE_UPDATE);
        filter.addAction(MOVE_UPDATE);
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
                reload();
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
            reload();
            MainActivity main = (MainActivity) getActivity();
            main.update();
        }
    }

    public void reload() {
        error.setVisibility(View.GONE);
        adapter.files.clear();
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (shared.getBoolean(FilesApplication.PREF_ROOT, false)) {
                try {
                    ArrayList<File> ff = SuperUser.ls(SuperUser.LSA, uri);
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
                        adapter.files.add(new NativeFile(Uri.fromFile(f), f.getName(), f.isDirectory(), f.length(), f.lastModified()));
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
                    long last = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
                    Uri u = DocumentsContract.buildDocumentUriUsingTree(uri, id);
                    adapter.files.add(new NativeFile(u, name, type.equals(DocumentsContract.Document.MIME_TYPE_DIR), size, last));
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
        reload();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        toolbar = menu.findItem(R.id.action_selected);
        paste = menu.findItem(R.id.action_paste);
        pasteCancel = menu.findItem(R.id.action_paste_cancel);
        updatePaste();
        select = (SelectView) MenuItemCompat.getActionView(toolbar);
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
                        final PasteBuilder paste = new PasteBuilder(getContext());
                        paste.setTitle("Deleting");
                        final PendingOperation op = new PendingOperation(getContext(), uri, selected) {
                            @Override
                            public void run() {
                                if (calcIndex < calcs.size()) {
                                    if (!calc())
                                        Collections.sort(files, new SortDelete());
                                    paste.copy.setText("Calculating: " + formatCalc());
                                    paste.update(this);
                                    paste.progressFile.setVisibility(View.GONE);
                                    paste.from.setText("Deleting: " + formatStart());
                                    paste.to.setVisibility(View.GONE);
                                    post();
                                    return;
                                }
                                if (filesIndex < files.size()) {
                                    int old = filesIndex;
                                    NativeFile f = files.get(filesIndex);
                                    delete(f);
                                    if (!f.dir)
                                        processed += f.size;
                                    filesIndex++;
                                    paste.copy.setVisibility(View.GONE);
                                    paste.update(this, old, f);
                                    paste.progressFile.setVisibility(View.GONE);
                                    paste.from.setText("Deleting: " + storage.getDisplayName(f.uri));
                                    paste.to.setVisibility(View.GONE);
                                    post();
                                    return;
                                }
                                paste.dismiss();
                                closeSelection();
                                reload();
                                Toast.makeText(getContext(), "Deleted " + files.size() + " files", Toast.LENGTH_SHORT).show();
                            }

                            public void post() {
                                handler.removeCallbacks(this);
                                handler.post(this);
                            }
                        };
                        paste.dismiss = new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                handler.removeCallbacks(op);
                                paste.dismiss();
                            }
                        };
                        paste.show();
                        op.run();
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
        select.rename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Uri f = selected.get(0);
                final OpenFileDialog.EditTextDialog dialog = new OpenFileDialog.EditTextDialog(getContext());
                dialog.setTitle("Rename");
                dialog.setText(storage.getName(f));
                dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        storage.rename(f, dialog.getText());
                        reload();
                        closeSelection();
                    }
                });
                dialog.show();
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
        if (id == R.id.action_openas) {
            return true;
        }
        if (id == R.id.action_openastext) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "text/*");
            startActivity(open);
            return true;
        }
        if (id == R.id.action_openasimage) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "image/*");
            startActivity(open);
            return true;
        }
        if (id == R.id.action_openasaudio) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "audio/*");
            startActivity(open);
            return true;
        }
        if (id == R.id.action_view) {
            return true;
        }
        if (id == R.id.action_share) {
            Intent intent = item.getIntent();
            Intent share = StorageProvider.getProvider().shareIntent(intent.getData(), intent.getType(), intent.getStringExtra("name"));
            startActivity(share);
            return true;
        }
        if (id == R.id.action_refresh) {
            reload();
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
        updateSelection();
    }

    public void updateSelection() {
        if (selected.size() == 1) {
            select.rename.setEnabled(true);
            select.rename.setColorFilter(Color.WHITE);
        } else {
            select.rename.setEnabled(false);
            select.rename.setColorFilter(Color.GRAY);
        }
        adapter.notifyDataSetChanged();
    }

    public void closeSelection() {
        MenuItemCompat.collapseActionView(toolbar);
        updateSelection();
    }

    public void paste() {
        ArrayList<Uri> ff = null;
        if (app.copy != null)
            ff = app.copy;
        if (app.cut != null)
            ff = app.cut;
        final PasteBuilder paste = new PasteBuilder(getContext());
        final String n;
        if (app.copy != null)
            n = "Copying";
        else if (app.cut != null)
            n = "Move";
        else
            n = "Paste";
        paste.setTitle(n);

        final PendingOperation op = new PendingOperation(getContext(), app.uri, ff) {
            int deleteIndex = -1;
            ArrayList<NativeFile> delete = new ArrayList<>();

            @Override
            public void run() {
                try {
                    if (calcIndex < calcs.size()) {
                        calc();
                        paste.copy.setText(getString(R.string.copy_calculating) + formatCalc());
                        paste.update(this);
                        paste.from.setText(getString(R.string.copy_from) + formatStart());
                        paste.to.setText(getString(R.string.copy_to) + storage.getDisplayName(uri));
                        post();
                        return;
                    }
                    if (is != null && os != null) {
                        final NativeFile f = files.get(filesIndex);
                        int old = filesIndex;
                        Uri oldt = t;
                        if (thread == null) {
                            thread = new Thread("Copy thread") {
                                @Override
                                public void run() {
                                    try {
                                        while (!isInterrupted() && copy())
                                            info.step(current);
                                        post();
                                    } catch (Exception e) {
                                        delayed = e;
                                    }
                                }
                            };
                            thread.start();
                        } else if (!thread.isAlive()) {
                            try {
                                if (delayed != null)
                                    throw new RuntimeException(delayed);
                                close(f);
                                if (app.cut != null)
                                    delete(f);
                                filesIndex++;
                                thread = null;
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        int a = info.getAverageSpeed();
                        String e;
                        long diff = 0;
                        if (a > 0)
                            diff = (f.size - current) * 1000 / a;
                        if (diff >= 1000)
                            e = FilesApplication.formatLeftExact(context, diff);
                        else
                            e = "∞";
                        paste.copy.setText(n + " " + FilesApplication.formatSize(context, a) + "/s" + ", " + e);
                        paste.update(this, old, f);
                        paste.from.setText(getString(R.string.copy_from) + storage.getDisplayName(f.uri));
                        paste.to.setText(getString(R.string.copy_to) + storage.getDisplayName(oldt));
                        post(thread != null ? 1000 : 0);
                        return;
                    }
                    if (filesIndex < files.size()) {
                        NativeFile f = files.get(filesIndex);
                        try {
                            if (f.dir) {
                                mkdir(f.name, uri);
                                filesIndex++;
                                if (app.cut != null) {
                                    delete.add(f);
                                    deleteIndex = delete.size() - 1;
                                }
                            } else {
                                String s = uri.getScheme();
                                NativeFile t = null;
                                if (s.equals(ContentResolver.SCHEME_FILE)) {
                                    File k = Storage.getFile(uri);
                                    File m = new File(k, f.name);
                                    if (m.exists())
                                        t = new NativeFile(m);
                                } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, f.name);
                                    DocumentFile k = DocumentFile.fromSingleUri(context, doc);
                                    if (k.exists())
                                        t = new NativeFile(k);
                                } else {
                                    throw new Storage.UnknownUri();
                                }
                                if (t != null) {
                                    switch (check(f, t).iterator().next()) {
                                        case NONE:
                                            break;
                                        case ASK:
                                            pasteConflict(this, paste, f, t);
                                            return;
                                        case SKIP:
                                            filesIndex++;
                                            post();
                                            return;
                                        case OVERWRITE:
                                            delete(t);
                                            break;
                                    }
                                }
                                open(f, uri);
                                info.start(current);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        post();
                        return;
                    }
                    if (deleteIndex >= 0) {
                        NativeFile f = delete.get(deleteIndex);
                        delete(f);
                        deleteIndex--;
                        paste.copy.setVisibility(View.GONE);
                        paste.progressFile.setVisibility(View.GONE);
                        paste.progressTotal.setVisibility(View.GONE);
                        paste.from.setText("Deleting: " + storage.getDisplayName(f.uri));
                        paste.to.setVisibility(View.GONE);
                        post();
                        return;
                    }
                    if (app.cut != null) {
                        app.cut = null; // not possible to move twice
                        getContext().sendBroadcast(new Intent(MOVE_UPDATE));
                    }
                    paste.dismiss(); // all done!
                } catch (RuntimeException e) {
                    pasteError(paste, this, e);
                }
            }

            public void post() {
                post(0);
            }

            public void post(long l) {
                handler.removeCallbacks(this);
                handler.postDelayed(this, l);
            }
        };

        paste.neutral = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View.OnClickListener neutral = this;
                op.pause();
                handler.removeCallbacks(op);
                final Button b = paste.d.getButton(DialogInterface.BUTTON_NEUTRAL);
                b.setText(R.string.copy_resume);
                paste.neutral = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        op.run();
                        b.setText(R.string.copy_pause);
                        paste.neutral = neutral;
                    }
                };
            }
        };

        paste.dismiss = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                paste.dismiss();
                op.close();
                handler.removeCallbacks(op);
                reload();
            }
        };
        final AlertDialog d = paste.create();
        d.show();

        op.run();
    }

    public void pasteError(final PasteBuilder paste, final PendingOperation op, Throwable e) {
        Log.e(TAG, "paste", e);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Error");
        builder.setMessage(e.getMessage());
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                op.close();
                paste.dismiss();
                handler.removeCallbacks(op);
            }
        });
        builder.setNeutralButton(R.string.copy_retry, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                op.run();
            }
        });
        builder.show();
    }

    public void pasteConflict(final PendingOperation op, final PasteBuilder paste, final NativeFile f, final NativeFile t) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        String n = "Paste";
        if (app.copy != null)
            n = "Copying conflict";
        if (app.cut != null)
            n = "Move conflict";
        builder.setTitle(n);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v = inflater.inflate(R.layout.paste_conflict, null);
        builder.setView(v);
        builder.setCancelable(false);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                op.close();
                paste.dismiss();
            }
        });

        final AlertDialog d = builder.create();

        TextView target = (TextView) v.findViewById(R.id.target);
        TextView source = (TextView) v.findViewById(R.id.source);

        View overwrite = v.findViewById(R.id.overwrite);
        overwrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.delete(t);
                op.run();
                d.dismiss();
            }
        });

        View overwriteall = v.findViewById(R.id.overwriteall);
        overwriteall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnumSet<PendingOperation.OPERATION> o = op.check(f, t);
                o.clear();
                o.add(PendingOperation.OPERATION.OVERWRITE);
                op.run();
                d.dismiss();
            }
        });

        View skip = v.findViewById(R.id.skip);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.filesIndex++;
                op.run();
                d.dismiss();
            }
        });

        View skipall = v.findViewById(R.id.skipall);
        skipall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnumSet<PendingOperation.OPERATION> o = op.check(f, t);
                o.clear();
                o.add(PendingOperation.OPERATION.SKIP);
                op.run();
                d.dismiss();
            }
        });

        target.setText(getString(R.string.copy_overwrite) + storage.getDisplayName(t.uri) + "\n\t\t" + BYTES.format(t.size) + " " + getString(R.string.size_bytes) + ", " + SIMPLE.format(t.last));
        source.setText(getString(R.string.copy_overwrite_with) + storage.getDisplayName(f.uri) + "\n\t\t" + BYTES.format(f.size) + " " + getString(R.string.size_bytes) + ", " + SIMPLE.format(f.last));

        d.show();
    }
}
