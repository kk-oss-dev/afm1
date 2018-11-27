package com.github.axet.filemanager.fragments;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
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
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activities.MainActivity;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.app.SuperUser;
import com.github.axet.filemanager.services.StorageProvider;
import com.github.axet.filemanager.widgets.PathView;
import com.github.axet.filemanager.widgets.SelectView;
import com.github.axet.wget.SpeedInfo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FilesFragment extends Fragment {
    public static final String TAG = FilesFragment.class.getSimpleName();

    public static final int RESULT_PERMS = 1;
    public static final int RESULT_ARCHIVE = 2;

    public static final String PASTE_UPDATE = FilesFragment.class.getCanonicalName() + ".PASTE_UPDATE";
    public static final String MOVE_UPDATE = FilesFragment.class.getCanonicalName() + ".MOVE_UPDATE";

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyyMMdd\'T\'HHmmss");

    public static final String EXTERNAL_STORAGE = "EXTERNAL_STORAGE";

    public static final NumberFormat BYTES = new DecimalFormat("###,###,###,###,###.#", new DecimalFormatSymbols() {{
        setGroupingSeparator(' ');
    }});

    Handler handler = new Handler();
    FilesApplication app;
    Uri uri;
    Adapter adapter;
    RecyclerView list;
    LinearLayoutManager layout;
    Storage storage;

    PasteBuilder delete;
    PasteBuilder paste;
    PasteBuilder archive;

    OpenChoicer choicer;

    HashMap<Uri, Integer> portables = new HashMap<>();
    Specials specials;
    PathView path;
    View button;
    TextView error;
    MenuItem toolbar;
    MenuItem pasteMenu;
    MenuItem pasteCancel;
    SelectView select;
    ArrayList<Storage.Node> selected = new ArrayList<Storage.Node>() { // we have to keep Storage.Node and not Uri to know node type (file or dir) before operation begins
        @Override
        public boolean contains(Object o) { // in case if adapter.files got reloaded
            for (int i = 0; i < size(); i++) {
                Storage.Node n = get(i);
                if (n.uri.equals(((Storage.Node) o).uri))
                    return true;
            }
            return false;
        }
    };
    HashMap<Uri, Pos> offsets = new HashMap<>();
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

    public static String toMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            Throwable p = null;
            Throwable a = e;
            while (a != null) {
                p = a;
                a = a.getCause();
            }
            msg = p.getClass().getCanonicalName();
        }
        return msg;
    }

    public static String[] splitPath(String s) {
        return s.split("[//\\\\]");
    }

    public static String stripRight(String s, String right) {
        if (s.endsWith(right))
            s = s.substring(0, s.length() - right.length());
        return s;
    }

    public static class Pos {
        public int pos;
        public int off;
    }

    public static class Specials extends HashMap<Uri, Integer> {
        public Specials(Context context) {
            File e = Environment.getExternalStorageDirectory();
            add(e, R.drawable.ic_sd_card_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), R.drawable.ic_camera_alt_black_24dp);
            if (Build.VERSION.SDK_INT >= 19)
                add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), R.drawable.ic_library_books_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), R.drawable.ic_cloud_download_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), R.drawable.ic_photo_library_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS), R.drawable.ic_music_video_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), R.drawable.ic_video_library_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS), R.drawable.ic_music_video_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), R.drawable.ic_music_video_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS), R.drawable.ic_music_video_black_24dp);
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES), R.drawable.ic_music_video_black_24dp);

            File f = context.getExternalFilesDir(null);
            if (f != null) {
                File k = Storage.relative(e, f);
                if (k != f) {
                    File p = f;
                    File old = p;
                    while (!e.equals(p)) {
                        old = p;
                        p = p.getParentFile();
                    }
                    File n = new File(e, old.getName()); // ok it is 'Android'!
                    add(n, R.drawable.ic_settings_black_24dp);
                }
            }
        }

        public void add(File f, int id) {
            put(Uri.fromFile(f), id);
        }
    }

    public static class PendingOperation implements Runnable {
        Context context;
        ContentResolver resolver;
        Storage storage;
        SharedPreferences shared;
        final Object lock = new Object();
        final AtomicBoolean interrupt = new AtomicBoolean(); // soft interrupt
        Thread thread;
        Throwable delayed;

        int calcIndex;
        ArrayList<Storage.Node> calcs;
        ArrayList<Storage.Node> calcsStart; // initial calcs dir for UI
        Uri calcUri;

        int filesIndex;
        ArrayList<Storage.Node> files = new ArrayList<>();

        Storage.Node f;
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

        EnumSet<OPERATION> access = EnumSet.of(OPERATION.ASK); // ErrnoException ACCESS

        enum OPERATION {NONE, ASK, SKIP, OVERWRITE}

        public PendingOperation(Context context) {
            this.context = context;
            this.resolver = context.getContentResolver();
            this.storage = new Storage(context);
            this.shared = PreferenceManager.getDefaultSharedPreferences(context);
        }

        public PendingOperation(Context context, Uri root, ArrayList<Storage.Node> ff) {
            this(context);
            calcIndex = 0;
            calcs = new ArrayList<>(ff);
            calcsStart = new ArrayList<>(ff);
            calcUri = root;
        }

        public boolean calc() {
            Storage.Node c = calcs.get(calcIndex);
            if (c.dir) {
                ArrayList<Storage.Node> nn = storage.walk(calcUri, c.uri);
                for (Storage.Node n : nn) {
                    if (n.dir) {
                        if (n.uri.equals(c.uri)) // walk return current dirs, do not follow it
                            files.add(n);
                        else
                            calcs.add(n);
                    } else {
                        files.add(n);
                        total += n.size;
                    }
                }
            } else {
                files.add(c);
                total += c.size;
            }
            calcIndex++;
            return calcIndex < calcs.size();
        }

        public void open(final Storage.Node f, Uri to) throws IOException {
            this.f = f;
            is = storage.open(f.uri);
            String s = to.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                File k = Storage.getFile(to);
                final File m = new File(k, f.name);
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false))
                    os = SuperUser.open(m);
                else
                    os = new FileOutputStream(m);
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

        public void finish() throws IOException {
            if (is != null) {
                is.close();
                is = null;
            }
            if (os != null) {
                os.close();
                os = null;
            }
            if (t != null) {
                storage.touch(t, f.last);
                t = null;
            }
            f = null;
        }

        public void cancel() {
            if (thread != null) {
                interrupt.set(true);
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                thread = null;
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "close error", e);
                }
                is = null;
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e(TAG, "close error", e);
                }
                os = null;
            }
            if (t != null) {
                storage.delete(t);
                t = null;
            }
            f = null;
        }

        public void close() {
            cancel();
        }

        public EnumSet<OPERATION> check(Storage.Node f, Storage.Node t) { // ask user for confirmations?
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

        public EnumSet<OPERATION> check(Throwable e) { // ask user for confirmations?
            Throwable c = e.getCause();
            if (Build.VERSION.SDK_INT >= 21) {
                if (e instanceof FileNotFoundException && c instanceof ErrnoException && ((ErrnoException) c).errno == OsConstants.EACCES)
                    return access;
            } else {
                try {
                    final int EACCES = 13; // OsConstants.EACCES
                    Class klass = Class.forName("libcore.io.ErrnoException");
                    Field f = klass.getDeclaredField("errno");
                    if (e instanceof FileNotFoundException && klass.isInstance(c) && f.getInt(c) == EACCES)
                        return access;
                } catch (Exception ignore) {
                }
            }
            return EnumSet.of(OPERATION.NONE); // asking
        }

        public int copy(byte[] buf) throws IOException {
            int len;
            if ((len = is.read(buf)) < 0)
                return len;
            os.write(buf, 0, len);
            return len;
        }

        @Override
        public void run() {
        }

        public void pause() {
            if (thread != null) {
                interrupt.set(true);
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
                return storage.getDisplayName(calcsStart.get(0).uri);
            } else {
                String str = storage.getDisplayName(calcUri) + "{";
                for (Storage.Node u : calcsStart)
                    str += Storage.getName(context, u.uri) + ",";
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

        public void update(PendingOperation op, int old, Storage.Node f) {
            filesCount.setText(old + " / " + op.files.size());
            progressFile.setProgress(f.size == 0 ? 0 : (int) (op.current * 100 / f.size));
            progressTotal.setProgress(op.total == 0 ? 0 : (int) (op.processed * 100 / op.total));
            filesTotal.setText(FilesApplication.formatSize(getContext(), op.processed) + " / " + FilesApplication.formatSize(getContext(), op.total));
        }
    }

    public static class SortByName implements Comparator<Storage.Node> { // by name files first
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            Boolean d1 = o1.dir || (o1 instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) o1).isSymDir());
            Boolean d2 = o2.dir || (o2 instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) o2).isSymDir());
            int c = d2.compareTo(d1);
            if (c != 0)
                return c;
            return o1.name.compareTo(o2.name);
        }
    }

    public static class SortDelete implements Comparator<Storage.Node> { // deepest files first
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            int c = Boolean.valueOf(o1.dir).compareTo(o2.dir);
            if (c != 0)
                return c;
            String[] cc2 = splitPath(o2.name);
            String[] cc1 = splitPath(o1.name);
            c = Integer.valueOf(cc2.length).compareTo(cc1.length);
            if (c != 0)
                return c;
            return cc1[cc1.length - 1].compareTo(cc2[cc2.length - 1]);
        }
    }

    public class Holder extends RecyclerView.ViewHolder {
        public ImageView icon;
        public ImageView iconSmall;
        public TextView name;
        public View circleFrame;
        public View circle;
        public View unselected;
        public View selected;

        public Holder(View itemView) {
            super(itemView);
            int accent = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            icon.setColorFilter(accent);
            iconSmall = (ImageView) itemView.findViewById(R.id.icon_small);
            name = (TextView) itemView.findViewById(R.id.name);
            circleFrame = itemView.findViewById(R.id.circle_frame);
            circle = itemView.findViewById(R.id.circle);
            unselected = itemView.findViewById(R.id.unselected);
            selected = itemView.findViewById(R.id.selected);
        }
    }

    public class Adapter extends RecyclerView.Adapter<Holder> {
        ArrayList<Storage.Node> files = new ArrayList<>();

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(getContext()).inflate(R.layout.file_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final Holder h, final int position) {
            final Storage.Node f = files.get(position);
            final boolean dir = f.dir || (f instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) f).isSymDir());
            if (dir)
                h.icon.setImageResource(R.drawable.ic_folder_black_24dp);
            else
                h.icon.setImageResource(R.drawable.ic_file);
            h.name.setText(f.name);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (MenuItemCompat.isActionViewExpanded(toolbar)) {
                        if (selected.contains(f))
                            selected.remove(f);
                        else
                            selected.add(f);
                        updateSelection();
                        return;
                    }
                    if (dir) {
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
                    selected.clear();
                    selected.add(f);
                    openSelection();
                    PopupMenu menu = new PopupMenu(getContext(), v);
                    menu.inflate(R.menu.menu_select);
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
            h.circleFrame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selected.contains(f))
                        selected.remove(f);
                    else
                        selected.add(f);
                    openSelection();
                }
            });
            ViewCompat.setAlpha(h.selected, 1f);
            if (toolbar != null && MenuItemCompat.isActionViewExpanded(toolbar)) {
                h.circle.setVisibility(View.INVISIBLE);
                if (selected.contains(f)) {
                    h.unselected.setVisibility(View.INVISIBLE);
                    h.selected.setVisibility(View.VISIBLE);
                } else {
                    h.unselected.setVisibility(View.VISIBLE);
                    h.selected.setVisibility(View.INVISIBLE);
                }
            } else {
                if (pending(f)) {
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
            Integer id = getSpecialIcon(f);
            if (id != null) {
                h.iconSmall.setImageResource(id);
                h.iconSmall.setVisibility(View.VISIBLE);
            } else {
                h.iconSmall.setVisibility(View.GONE);
            }
        }

        public Integer getSpecialIcon(Storage.Node f) {
            Integer id = portables.get(f.uri);
            if (id != null)
                return id;
            if (f instanceof Storage.SymlinkNode)
                return R.drawable.ic_link_black_24dp;
            if (f.name.startsWith("."))
                return R.drawable.ic_visibility_off_black_24dp;
            return specials.get(f.uri);
        }

        public boolean pending(Storage.Node n) {
            return app.copy != null && app.copy.contains(n) || app.cut != null && app.cut.contains(n);
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
        specials = new Specials(getContext());
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
        path.listener = new PathView.Listener() {
            @Override
            public void onUriSelected(Uri u) {
                load(u);
            }
        };
        path.setUri(uri);

        layout = new LinearLayoutManager(getContext());

        list = (RecyclerView) rootView.findViewById(R.id.list);
        list.setLayoutManager(layout);
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
            pasteMenu.setVisible(true);
            pasteCancel.setVisible(true);
        } else {
            pasteMenu.setVisible(false);
            pasteCancel.setVisible(false);
        }
        adapter.notifyDataSetChanged();
        Intent intent = new Intent(PASTE_UPDATE);
        getContext().sendBroadcast(intent);
    }

    void updateButton() {
        button.setVisibility(View.VISIBLE);
        String s = uri.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File f = Storage.getFile(uri);
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (f.canRead() || shared.getBoolean(FilesApplication.PREF_ROOT, false) || Storage.permitted(getContext(), Storage.PERMISSIONS_RW))
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
            case RESULT_ARCHIVE:
                choicer.onRequestPermissionsResult(permissions, grantResults);
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_ARCHIVE:
                choicer.onActivityResult(resultCode, data);
                break;
        }
    }

    public Uri getUri() {
        if (uri == null)
            return getArguments().getParcelable("uri");
        return uri;
    }

    Pos findFirstVisibleItem() {
        TreeMap<Integer, Pos> map = new TreeMap<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.getParent() != null && child.getVisibility() == View.VISIBLE) {
                RecyclerView.ViewHolder h = list.findContainingViewHolder(child);
                Pos p = new Pos();
                p.pos = h.getAdapterPosition();
                p.off = child.getTop();
                map.put(child.getTop(), p);
            }
        }
        if (map.size() > 0)
            return map.get(map.firstKey());
        return null;
    }

    public void load(Uri u) {
        if (uri == null) {
            getArguments().putParcelable("uri", u);
        } else {
            offsets.put(uri, findFirstVisibleItem());
            uri = u;
            updateButton();
            path.setUri(uri);
            reload();
            MainActivity main = (MainActivity) getActivity();
            main.update();
            Pos p = offsets.get(uri);
            if (p != null)
                layout.scrollToPositionWithOffset(p.pos, p.off);
            else
                layout.scrollToPositionWithOffset(0, 0);
        }
    }

    public void reload() {
        error.setVisibility(View.GONE);

        adapter.files.clear();
        try {
            adapter.files.addAll(storage.list(uri));
        } catch (RuntimeException e) {
            Log.e(TAG, "reload()", e);
            error.setText(e.getMessage());
            error.setVisibility(View.VISIBLE);
        }

        ArrayList<Storage.SymlinkNode> calcs = new ArrayList<>();
        ArrayList<File> a = new ArrayList<>();
        for (Storage.Node n : adapter.files) {
            if (n instanceof Storage.SymlinkNode) {
                calcs.add((Storage.SymlinkNode) n);
                a.add(Storage.getFile(n.uri));
            }
        }
        if (calcs.size() > 0) {
            a = SuperUser.isDirectory(a);
            for (Storage.SymlinkNode n : calcs) {
                File k = Storage.getFile(n.uri);
                n.setSymDir(a.contains(k));
            }
            calcs.clear();
        }
        Collections.sort(adapter.files, new SortByName());
        adapter.notifyDataSetChanged();

        portables.clear();
        File[] ff = OpenFileDialog.getPortableList();
        if (ff != null) {
            for (File f : ff)
                portables.put(Uri.fromFile(f), R.drawable.ic_sd_card_black_24dp);
        }
        String e = System.getenv(EXTERNAL_STORAGE);
        if (e != null && !e.isEmpty())
            portables.put(Uri.fromFile(new File(e)), R.drawable.ic_sd_card_black_24dp);
        for (Uri p : new TreeSet<>(portables.keySet())) {
            File z = Storage.getFile(p);
            for (Uri k : specials.keySet()) {
                File f = Storage.getFile(k);
                File m = Environment.getExternalStorageDirectory();
                File r = Storage.relative(m, f);
                File n = new File(z, r.getPath());
                portables.put(Uri.fromFile(n), specials.get(k));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
        updateButton();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        toolbar = menu.findItem(R.id.action_selected);
        pasteMenu = menu.findItem(R.id.action_paste);
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
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected " + storage.getDisplayName(uri) + " " + item);
        int id = item.getItemId();
        if (id == R.id.action_open) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                startActivity(open);
            else
                Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_view) {
            MainActivity main = (MainActivity) getActivity();
            main.openHex(item.getIntent().getData());
            return true;
        }
        if (id == R.id.action_openas) {
            return true;
        }
        if (id == R.id.action_openastext) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "text/*");
            if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                startActivity(open);
            else
                Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_openasimage) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "image/*");
            if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                startActivity(open);
            else
                Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_openasaudio) {
            Intent intent = item.getIntent();
            Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
            open.setDataAndType(open.getData(), "audio/*");
            if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                startActivity(open);
            else
                Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_share) {
            Intent intent = item.getIntent();
            Intent share = StorageProvider.getProvider().shareIntent(intent.getData(), intent.getType(), intent.getStringExtra("name"));
            if (OptimizationPreferenceCompat.isCallable(getContext(), share))
                startActivity(share);
            else
                Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
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
        if (id == R.id.action_copy) {
            app.copy = new ArrayList<>(selected);
            app.cut = null;
            app.uri = uri;
            updatePaste();
            closeSelection();
            Toast.makeText(getContext(), getString(R.string.toast_files_copied, app.copy.size()), Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_cut) {
            app.copy = null;
            app.cut = new ArrayList<>(selected);
            app.uri = uri;
            updatePaste();
            closeSelection();
            Toast.makeText(getContext(), getString(R.string.toast_files_cut, app.cut.size()), Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.files_delete);
            builder.setMessage(R.string.are_you_sure);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (delete != null)
                        return;
                    delete = new PasteBuilder(getContext());
                    delete.setTitle(getString(R.string.files_deleting));
                    final PendingOperation op = new PendingOperation(getContext(), uri, selected) {
                        @Override
                        public void run() {
                            try {
                                if (calcIndex < calcs.size()) {
                                    if (!calc())
                                        Collections.sort(files, new SortDelete());
                                    delete.copy.setGravity(Gravity.NO_GRAVITY);
                                    delete.copy.setText(getString(R.string.files_calculating) + ": " + formatCalc());
                                    delete.update(this);
                                    delete.progressFile.setVisibility(View.GONE);
                                    delete.from.setText(getString(R.string.files_deleting) + ": " + formatStart());
                                    delete.to.setVisibility(View.GONE);
                                    post();
                                    return;
                                }
                                if (filesIndex < files.size()) {
                                    int old = filesIndex;
                                    Storage.Node f = files.get(filesIndex);
                                    if (!storage.delete(f.uri))
                                        throw new RuntimeException("Unable to delete: " + f.name);
                                    if (!f.dir)
                                        processed += f.size;
                                    filesIndex++;
                                    delete.copy.setText(getString(R.string.files_deleting) + ": " + formatStart());
                                    delete.update(this, old, f);
                                    delete.progressFile.setVisibility(View.GONE);
                                    delete.from.setText(storage.getDisplayName(f.uri));
                                    delete.to.setVisibility(View.GONE);
                                    post();
                                    return;
                                }
                                delete.dismiss();
                                closeSelection();
                                reload();
                                Toast.makeText(getContext(), getString(R.string.toast_files_deleted, files.size()), Toast.LENGTH_SHORT).show();
                            } catch (RuntimeException e) {
                                switch (check(e).iterator().next()) {
                                    case SKIP:
                                        Log.d(TAG, "skip", e);
                                        filesIndex++;
                                        post();
                                        return;
                                }
                                pasteError(delete, this, e);
                            }
                        }

                        public void post() {
                            handler.removeCallbacks(this);
                            handler.post(this);
                        }
                    };
                    delete.neutral = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final View.OnClickListener neutral = this;
                            op.pause();
                            handler.removeCallbacks(op);
                            final Button b = delete.d.getButton(DialogInterface.BUTTON_NEUTRAL);
                            b.setText(R.string.copy_resume);
                            delete.neutral = new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    op.run();
                                    b.setText(R.string.copy_pause);
                                    delete.neutral = neutral;
                                }
                            };
                        }
                    };
                    delete.dismiss = new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            handler.removeCallbacks(op);
                            delete.dismiss();
                            delete = null;
                        }
                    };
                    delete.show();
                    op.run();
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
        if (id == R.id.action_rename && item.isEnabled()) {
            final Uri f = selected.get(0).uri;
            final OpenFileDialog.EditTextDialog dialog = new OpenFileDialog.EditTextDialog(getContext());
            dialog.setTitle(R.string.files_rename);
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
            return true;
        }
        if (id == R.id.action_archive) {
            if (archive != null)
                return true;
            final String name;
            if (selected.size() == 1)
                name = selected.get(0).name;
            else
                name = "Archive";
            Uri to = storage.getNextFile(uri, name, "zip");
            OutputStream os;
            try {
                os = storage.open(uri, storage.getName(to));
            } catch (IOException e) {
                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        final Runnable run = this;
                        choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                            @Override
                            public void onResult(Uri uri) {
                                Uri to = storage.getNextFile(uri, name, "zip");
                                try {
                                    OutputStream os = storage.open(uri, storage.getName(to));
                                    archive(to, os);
                                } catch (IOException e) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                    builder.setTitle("Error");
                                    builder.setMessage(toMessage(e));
                                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    });
                                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            run.run();
                                        }
                                    });
                                    builder.show();
                                }
                            }
                        };
                        choicer.setTitle(getString(R.string.save_to));
                        choicer.setPermissionsDialog(FilesFragment.this, Storage.PERMISSIONS_RW, RESULT_ARCHIVE);
                        if (Build.VERSION.SDK_INT >= 21)
                            choicer.setStorageAccessFramework(FilesFragment.this, RESULT_ARCHIVE);
                        choicer.show(uri);
                    }
                };
                run.run();
                return true;
            }
            archive(to, os);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void archive(final Uri to, final OutputStream fos) {
        archive = new PasteBuilder(getContext());
        archive.setTitle(R.string.menu_archive);
        final PendingOperation op = new PendingOperation(getContext(), uri, selected) {
            ZipOutputStream zip;

            {
                t = to;
            }

            @Override
            public void run() {
                try {
                    if (calcIndex < calcs.size()) {
                        if (!calc()) {
                            os = zip = new ZipOutputStream(new BufferedOutputStream(fos));
                        }
                        archive.copy.setGravity(Gravity.NO_GRAVITY);
                        archive.copy.setText(getString(R.string.files_calculating) + ": " + formatCalc());
                        archive.update(this);
                        archive.from.setText(getString(R.string.files_archiving) + ": " + formatStart());
                        archive.to.setText("To: " + storage.getDisplayName(t));
                        post();
                        return;
                    }
                    synchronized (lock) {
                        if (is != null) {
                            int old = filesIndex;
                            Uri oldt = t;
                            final Storage.Node f = files.get(filesIndex);
                            if (thread == null) {
                                interrupt.set(false);
                                thread = new Thread("Zip") {
                                    @Override
                                    public void run() {
                                        byte[] buf = new byte[SuperUser.BUF_SIZE];
                                        try {
                                            int len;
                                            while ((len = copy(buf)) > 0) {
                                                synchronized (lock) {
                                                    current += len;
                                                    processed += len;
                                                    info.step(current);
                                                }
                                                if (Thread.currentThread().isInterrupted() || interrupt.get())
                                                    return;
                                            }
                                            synchronized (lock) {
                                                is.close();
                                                is = null;
                                                filesIndex++;
                                                thread = null; // close thread
                                                post();
                                            }
                                        } catch (Exception e) {
                                            synchronized (lock) {
                                                delayed = e; // thread != null
                                                post();
                                            }
                                        }
                                    }
                                };
                                thread.start();
                            } else {
                                if (delayed != null) {
                                    Throwable d = delayed;
                                    thread = null;
                                    delayed = null;
                                    throw new RuntimeException(d);
                                }
                            }
                            post(thread == null ? 0 : 1000);
                            int a = info.getAverageSpeed();
                            String e;
                            long diff = 0;
                            if (a > 0)
                                diff = (f.size - current) * 1000 / a;
                            if (diff >= 1000)
                                e = FilesApplication.formatLeftExact(context, diff);
                            else
                                e = "∞";
                            archive.copy.setGravity(Gravity.CENTER);
                            archive.copy.setText(getString(R.string.files_archiving) + " " + FilesApplication.formatSize(context, a) + getString(R.string.per_second) + ", " + e);
                            archive.update(this, old, f);
                            archive.from.setText(getString(R.string.copy_from) + " " + storage.getDisplayName(f.uri));
                            archive.to.setText(getString(R.string.copy_to) + " " + storage.getDisplayName(oldt));
                            return;
                        }
                    }
                    if (filesIndex < files.size()) {
                        int old = filesIndex;
                        final Storage.Node f = files.get(filesIndex);

                        if (f.dir) {
                            ZipEntry entry = new ZipEntry(f.name + "/");
                            zip.putNextEntry(entry);
                        } else {
                            ZipEntry entry = new ZipEntry(f.name);
                            zip.putNextEntry(entry);
                            is = storage.open(f.uri);
                            current = 0;
                            post();
                            return;
                        }

                        filesIndex++;
                        archive.copy.setText(getString(R.string.files_archiving) + ": " + formatStart());
                        archive.update(this, old, f);
                        archive.from.setText(storage.getDisplayName(f.uri));
                        post();
                        return;
                    }
                    Uri to = t;
                    t = null;
                    archive.dismiss();
                    closeSelection();
                    reload();
                    Toast.makeText(getContext(), getString(R.string.toast_files_archived, storage.getName(to), files.size()), Toast.LENGTH_LONG).show();
                    select(to);
                } catch (IOException | RuntimeException e) {
                    switch (check(e).iterator().next()) {
                        case SKIP:
                            Log.d(TAG, "skip", e);
                            filesIndex++;
                            post();
                            return;
                    }
                    pasteError(archive, this, e);
                }
            }

            public void post(long d) {
                handler.removeCallbacks(this);
                handler.postDelayed(this, d);
            }

            public void post() {
                post(0);
            }
        };
        archive.neutral = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View.OnClickListener neutral = this;
                op.pause();
                handler.removeCallbacks(op);
                final Button b = archive.d.getButton(DialogInterface.BUTTON_NEUTRAL);
                b.setText(R.string.copy_resume);
                archive.neutral = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        op.run();
                        b.setText(R.string.copy_pause);
                        archive.neutral = neutral;
                    }
                };
            }
        };
        archive.dismiss = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                archive.dismiss();
                archive = null;
                op.close();
                handler.removeCallbacks(op);
                reload();
            }
        };
        archive.show();
        op.run();
    }

    public void openSelection() {
        app.copy = null;
        app.cut = null;
        updatePaste();
        MenuItemCompat.expandActionView(toolbar);
        updateSelection();
    }

    public void updateSelection() {
        adapter.notifyDataSetChanged();
    }

    public void closeSelection() {
        MenuItemCompat.collapseActionView(toolbar);
        updateSelection();
    }

    public void paste() {
        if (paste != null)
            return;
        if (app.uri.equals(uri)) // prevent paste to the original 'tab'
            return;
        ArrayList<Storage.Node> ff = null;
        if (app.copy != null)
            ff = app.copy;
        if (app.cut != null)
            ff = app.cut;
        paste = new PasteBuilder(getContext());
        final String n;
        if (app.copy != null)
            n = getString(R.string.files_copying);
        else if (app.cut != null)
            n = getString(R.string.files_moving);
        else
            n = "Paste";
        paste.setTitle(n);

        final PendingOperation op = new PendingOperation(getContext(), app.uri, ff) {
            int deleteIndex = -1;
            ArrayList<Storage.Node> delete = new ArrayList<>();

            @Override
            public void run() {
                try {
                    if (calcIndex < calcs.size()) {
                        calc();
                        paste.copy.setGravity(Gravity.NO_GRAVITY);
                        paste.copy.setText(getString(R.string.files_calculating) + ": " + formatCalc());
                        paste.update(this);
                        paste.from.setText(getString(R.string.copy_from) + " " + formatStart());
                        paste.to.setText(getString(R.string.copy_to) + " " + storage.getDisplayName(uri));
                        post();
                        return;
                    }
                    synchronized (lock) {
                        if (is != null && os != null) {
                            final Storage.Node f = files.get(filesIndex);
                            int old = filesIndex;
                            Uri oldt = t;
                            if (thread == null) {
                                interrupt.set(false);
                                thread = new Thread("Copy thread") {
                                    @Override
                                    public void run() {
                                        byte[] buf = new byte[SuperUser.BUF_SIZE];
                                        try {
                                            int len;
                                            while ((len = copy(buf)) > 0) {
                                                synchronized (lock) {
                                                    current += len;
                                                    processed += len;
                                                    info.step(current);
                                                }
                                                if (Thread.currentThread().isInterrupted() || interrupt.get())
                                                    return;
                                            }
                                            synchronized (lock) {
                                                finish();
                                                if (app.cut != null)
                                                    storage.delete(f.uri);
                                                filesIndex++;
                                                thread = null; // close thread
                                                post();
                                            }
                                        } catch (Exception e) {
                                            synchronized (lock) {
                                                delayed = e; // thread != null
                                                post();
                                            }
                                        }
                                    }
                                };
                                thread.start();
                            } else {
                                if (delayed != null) {
                                    Throwable d = delayed;
                                    thread = null;
                                    delayed = null;
                                    throw new RuntimeException(d);
                                }
                            }
                            post(thread == null ? 0 : 1000);
                            int a = info.getAverageSpeed();
                            String e;
                            long diff = 0;
                            if (a > 0)
                                diff = (f.size - current) * 1000 / a;
                            if (diff >= 1000)
                                e = FilesApplication.formatLeftExact(context, diff);
                            else
                                e = "∞";
                            paste.copy.setGravity(Gravity.CENTER);
                            paste.copy.setText(n + " " + FilesApplication.formatSize(context, a) + getString(R.string.per_second) + ", " + e);
                            paste.update(this, old, f);
                            paste.from.setText(getString(R.string.copy_from) + " " + storage.getDisplayName(f.uri));
                            paste.to.setText(getString(R.string.copy_to) + " " + storage.getDisplayName(oldt));
                            return;
                        }
                    }
                    if (filesIndex < files.size()) {
                        cancel(); // cancel previous skped operation if existed
                        Storage.Node f = files.get(filesIndex);
                        try {
                            if (f.dir) {
                                Storage.Node t = null;
                                String s = uri.getScheme();
                                if (s.equals(ContentResolver.SCHEME_FILE)) {
                                    File k = Storage.getFile(uri);
                                    File m = new File(k, f.name);
                                    if (m.exists() && m.isDirectory())
                                        t = new Storage.Node(m);
                                } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                                    Uri doc = storage.child(uri, f.name);
                                    DocumentFile k = DocumentFile.fromSingleUri(context, doc);
                                    if (k.exists() && k.isDirectory())
                                        t = new Storage.Node(k);
                                } else {
                                    throw new Storage.UnknownUri();
                                }
                                if (t == null && storage.mkdir(uri, f.name) == null)
                                    throw new RuntimeException("unable create dir: " + f.name);
                                filesIndex++;
                                if (app.cut != null) {
                                    delete.add(f);
                                    deleteIndex = delete.size() - 1; // reverse index
                                }
                            } else {
                                Storage.Node t = null;
                                String s = uri.getScheme();
                                if (s.equals(ContentResolver.SCHEME_FILE)) {
                                    File k = Storage.getFile(uri);
                                    File m = new File(k, f.name);
                                    if (m.exists() && m.isFile())
                                        t = new Storage.Node(m);
                                } else if (Build.VERSION.SDK_INT >= 23 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                                    Uri doc = storage.child(uri, f.name);
                                    DocumentFile k = DocumentFile.fromSingleUri(context, doc);
                                    if (k.exists() && k.isFile())
                                        t = new Storage.Node(k);
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
                                            storage.delete(t.uri);
                                            break;
                                    }
                                }
                                if (f instanceof Storage.SymlinkNode) {
                                    Storage.SymlinkNode l = (Storage.SymlinkNode) f;
                                    if (storage.symlink(l, uri) || l.isSymDir()) { // if fails, continue with content copy
                                        filesIndex++;
                                        if (app.cut != null)
                                            storage.delete(f.uri);
                                        post();
                                        return;
                                    } else { // we about to copy symlinks as files, update total!!!
                                        total += SuperUser.length(l.getTarget());
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
                        Storage.Node f = delete.get(deleteIndex);
                        storage.delete(f.uri);
                        deleteIndex--;
                        paste.copy.setVisibility(View.GONE);
                        paste.progressFile.setVisibility(View.GONE);
                        paste.progressTotal.setVisibility(View.GONE);
                        paste.from.setText(getString(R.string.files_deleting) + ": " + storage.getDisplayName(f.uri));
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
                    switch (check(e).iterator().next()) {
                        case SKIP:
                            Log.d(TAG, "skip", e);
                            filesIndex++;
                            post();
                            return;
                    }
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
                paste = null;
            }
        };
        final AlertDialog d = paste.create();
        d.show();

        op.run();
    }

    public void pasteError(final PasteBuilder paste, final PendingOperation op, final Throwable e) {
        Log.e(TAG, "paste", e);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setCancelable(false);
        builder.setTitle("Error");
        View p = LayoutInflater.from(getContext()).inflate(R.layout.paste_error, null);
        View sp = p.findViewById(R.id.skip_panel);
        TextView t = (TextView) p.findViewById(R.id.text);
        t.setText(toMessage(e));
        final View ss = p.findViewById(R.id.skip);
        View sa = p.findViewById(R.id.skipall);
        builder.setView(p);
        builder.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
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
        EnumSet<PendingOperation.OPERATION> o = op.check(e);
        if (o.contains(PendingOperation.OPERATION.NONE)) {
            sp.setVisibility(View.GONE);
            builder.setNegativeButton(R.string.button_skip, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ss.performClick();
                }
            });
        }
        final AlertDialog d = builder.create();
        ss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.filesIndex++;
                op.run();
                d.dismiss();
            }
        });
        sa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnumSet<PendingOperation.OPERATION> o = op.check(e);
                o.clear();
                o.add(PendingOperation.OPERATION.SKIP);
                op.filesIndex++;
                op.run();
                d.dismiss();
            }
        });
        d.show();
    }

    public void pasteConflict(final PendingOperation op, final PasteBuilder paste, final Storage.Node f, final Storage.Node t) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        String n = getString(R.string.files_conflict);
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
                storage.delete(t.uri);
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

        target.setText(getString(R.string.copy_overwrite) + " " + storage.getDisplayName(t.uri) + "\n\t\t" + BYTES.format(t.size) + " " + getString(R.string.size_bytes) + ", " + SIMPLE.format(t.last));
        source.setText(getString(R.string.copy_overwrite_with) + " " + storage.getDisplayName(f.uri) + "\n\t\t" + BYTES.format(f.size) + " " + getString(R.string.size_bytes) + ", " + SIMPLE.format(f.last));

        d.show();
    }

    public void select(Uri uri) {
        for (int i = 0; i < adapter.files.size(); i++) {
            Storage.Node n = adapter.files.get(i);
            if (n.uri.equals(uri)) {
                layout.scrollToPositionWithOffset(i, 0);
                final int p = i;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(p);
                        if (h == null) {
                            handler.post(this);
                            return;
                        }
                        AlphaAnimation anim = new AlphaAnimation(1.0f, 0.3f);
                        anim.setInterpolator(new AccelerateDecelerateInterpolator());
                        anim.setRepeatMode(Animation.REVERSE);
                        anim.setRepeatCount(3);
                        anim.setDuration(200);
                        h.itemView.startAnimation(anim);
                    }
                });
            }
        }
    }
}
