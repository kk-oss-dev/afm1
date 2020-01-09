package com.github.axet.filemanager.fragments;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
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
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.MountInfo;
import com.github.axet.androidlibrary.crypto.MD5;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.androidlibrary.widgets.CacheImagesRecyclerAdapter;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.InvalidateOptionsMenuCompat;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.androidlibrary.widgets.ToolbarActionView;
import com.github.axet.androidlibrary.widgets.TopSnappedSmoothScroller;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.activities.FullscreenActivity;
import com.github.axet.filemanager.activities.MainActivity;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.Storage;
import com.github.axet.filemanager.app.SuperUser;
import com.github.axet.filemanager.services.StorageProvider;
import com.github.axet.filemanager.widgets.PathView;
import com.github.axet.wget.SpeedInfo;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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

    OperationBuilder delete;
    PasteBuilder paste;
    OperationBuilder archive;

    OpenChoicer choicer;

    HashMap<Uri, Integer> portables = new HashMap<>();
    Specials specials;
    PathView path;
    View button;
    TextView error;
    MenuItem toolbar; // toolbar select
    MenuItem pasteMenu;
    MenuItem pasteCancel;
    ToolbarActionView select;
    Storage.Nodes selected = new Storage.Nodes();
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
    Runnable invalidateOptionsMenu;

    public Uri old;

    public static String getFirst(String name) {
        String[] ss = Storage.splitPath(name);
        return ss[0];
    }

    public static String stripRight(String s, String right) {
        if (s.endsWith(right))
            s = s.substring(0, s.length() - right.length());
        return s;
    }

    public static Comparator<Storage.Node> sort(Context context) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int selected = context.getResources().getIdentifier(shared.getString(FilesApplication.PREFERENCE_SORT, context.getResources().getResourceEntryName(R.id.sort_name_ask)), "id", context.getPackageName());
        switch (selected) {
            case R.id.sort_modified_ask:
                return new SortByType(new SortByDate());
            case R.id.sort_modified_desc:
                return new SortByType(Collections.reverseOrder(new SortByDate()));
            case R.id.sort_name_ask:
                return new SortByType(new SortByName());
            case R.id.sort_name_desc:
                return new SortByType(Collections.reverseOrder(new SortByName()));
            case R.id.sort_size_ask:
                return new SortByType(new SortBySize());
            case R.id.sort_size_desc:
                return new SortByType(Collections.reverseOrder(new SortBySize()));
            default:
                return new SortByType(new SortByName());
        }
    }

    public static void pasteError(final OperationBuilder paste, final PendingOperation op, final Throwable e, final boolean move) {
        Log.e(TAG, "paste", e);
        AlertDialog.Builder builder = new AlertDialog.Builder(paste.getContext());
        builder.setCancelable(false);
        builder.setTitle("Error");
        View p = LayoutInflater.from(paste.getContext()).inflate(R.layout.paste_error, null);
        View sp = p.findViewById(R.id.skip_panel);
        TextView t = (TextView) p.findViewById(R.id.text);
        t.setText(ErrorDialog.toMessage(e));
        final View retry = p.findViewById(R.id.retry);
        final View s1 = p.findViewById(R.id.spacer1);
        final View del = p.findViewById(R.id.delete);
        final View s2 = p.findViewById(R.id.spacer2);
        final View ss = p.findViewById(R.id.skip);
        final View s3 = p.findViewById(R.id.spacer3);
        final View sa = p.findViewById(R.id.skipall);
        builder.setView(p);
        builder.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                paste.dismiss();
            }
        });
        if (!move || op.f == null) { // always hide del if "no move" operation, or file is unkown
            del.setVisibility(View.GONE);
            s2.setVisibility(View.GONE);
        }
        if (op.check(e).contains(PendingOperation.OPERATION.NONE)) { // skip alls not supported?
            if (move) {
                s3.setVisibility(View.GONE);
                sa.setVisibility(View.GONE);
            } else { // no move and no skip all -> move all remaining buttons to the dialog
                sp.setVisibility(View.GONE);
                builder.setNeutralButton(R.string.copy_retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        retry.performClick();
                    }
                });
                builder.setNegativeButton(R.string.button_skip, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ss.performClick();
                    }
                });
            }
        }
        final AlertDialog d = builder.create();
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.run();
                d.dismiss();
            }
        });
        ss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                op.filesIndex++;
                op.cancel(); // close curret is / os
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
                op.cancel();
                op.run();
                d.dismiss();
            }
        });
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!op.storage.delete(op.f.uri)) {
                    pasteError(paste, op, new RuntimeException("unable to delete: " + op.f.name), move);
                    d.dismiss();
                    return;
                }
                op.filesIndex++;
                op.cancel();
                op.run();
                d.dismiss();
            }
        });
        d.show();
    }

    public static void pasteConflict(final OperationBuilder paste, final PendingOperation op, final Storage.Node f, final Storage.Node t) {
        AlertDialog.Builder builder = new AlertDialog.Builder(paste.getContext());
        String n = paste.getContext().getString(R.string.files_conflict);
        builder.setTitle(n);
        LayoutInflater inflater = LayoutInflater.from(paste.getContext());
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
                op.storage.delete(t.uri);
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
                op.cancel();
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

        target.setText(paste.getContext().getString(R.string.copy_overwrite) + " " +
                op.storage.getDisplayName(paste.getContext(), t.uri) + "\n\t\t" + BYTES.format(t.size) + " " +
                paste.getContext().getString(R.string.size_bytes) + ", " + SIMPLE.format(t.last));
        source.setText(paste.getContext().getString(R.string.copy_overwrite_with) + " " +
                op.storage.getDisplayName(paste.getContext(), f.uri) + "\n\t\t" + BYTES.format(f.size) + " " +
                paste.getContext().getString(R.string.size_bytes) + ", " + SIMPLE.format(f.last));

        d.show();
    }

    public static void copy(Context context, String text) {
        if (Build.VERSION.SDK_INT >= 11) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("sha1", text);
            clipboard.setPrimaryClip(clip);
        }
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
        public Context context;
        public ContentResolver resolver;
        public Storage storage;
        public final Object lock = new Object();
        public final AtomicBoolean interrupt = new AtomicBoolean(); // soft interrupt
        public Thread thread;
        public Throwable delayed;

        public int calcIndex;
        public ArrayList<Storage.Node> calcs;
        public ArrayList<Storage.Node> calcsStart; // initial calcs dirs for UI
        public Uri calcUri; // root uri

        public int filesIndex;
        public ArrayList<Storage.Node> files = new ArrayList<>();

        public Storage.Node f;
        public InputStream is;
        public OutputStream os;
        public Uri t; // target file, to update last modified time or delete in case of errors

        public SpeedInfo info = new SpeedInfo(); // speed info
        public long current; // current file transfers
        public long processed; // processed files bytes
        public long total; // total size of all files

        public EnumSet<OPERATION> small = EnumSet.of(OPERATION.ASK); // overwrite file smaller then source
        public EnumSet<OPERATION> big = EnumSet.of(OPERATION.ASK); // overwrite file bigger then source
        public EnumSet<OPERATION> newer = EnumSet.of(OPERATION.ASK); // overwrite same size file but newer date
        public EnumSet<OPERATION> same = EnumSet.of(OPERATION.ASK); // same file size and date

        public SparseArray<EnumSet<OPERATION>> errno = new SparseArray<EnumSet<OPERATION>>() {
            @Override
            public EnumSet<OPERATION> get(int key) {
                EnumSet<OPERATION> v = super.get(key);
                if (v == null)
                    put(key, v = EnumSet.of(OPERATION.ASK));
                return v;
            }
        };

        public enum OPERATION {NONE, ASK, SKIP, OVERWRITE}

        public PendingOperation(Context context) {
            this.context = context;
            this.storage = new Storage(context);
            this.resolver = context.getContentResolver();
        }

        public PendingOperation(Context context, Uri root, ArrayList<Storage.Node> ff) {
            this(context);
            calcIndex = 0;
            calcs = new ArrayList<>(ff);
            calcsStart = new ArrayList<>(ff);
            calcUri = root;
        }

        public void walk(Uri uri) {
            ArrayList<Storage.Node> nn = storage.walk(calcUri, uri);
            for (Storage.Node n : nn) {
                if (n.dir) {
                    if (n.uri.equals(uri)) // walk return current dirs, do not follow it
                        files.add(n);
                    else
                        calcs.add(n);
                } else {
                    files.add(n);
                    total += n.size;
                }
            }
        }

        public boolean calc() {
            Storage.Node c = calcs.get(calcIndex);
            if (c.dir) {
                walk(c.uri);
            } else {
                files.add(c);
                total += c.size;
            }
            calcIndex++;
            return calcIndex < calcs.size();
        }

        public void open(final Storage.Node f, Uri to, String target) throws IOException {
            this.f = f;
            is = storage.open(f.uri);
            String s = to.getScheme();
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                File k = Storage.getFile(to);
                File m = new File(k, target);
                if (shared.getBoolean(FilesApplication.PREF_ROOT, false))
                    os = new SuperUser.FileOutputStream(m);
                else
                    os = new FileOutputStream(m);
                t = Uri.fromFile(m);
            } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                Uri doc = Storage.createFile(context, to, target); // target == path
                if (doc == null)
                    throw new IOException("no permission");
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
            storage.closeSu();
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
            Throwable p = ErrorDialog.getCause(e);
            if (Build.VERSION.SDK_INT >= 21) {
                if (p instanceof ErrnoException)
                    return errno.get(((ErrnoException) p).errno);
            } else {
                try {
                    Class klass = Class.forName("libcore.io.ErrnoException");
                    Field f = klass.getDeclaredField("errno");
                    if (klass.isInstance(p))
                        return errno.get(f.getInt(p));
                } catch (Exception ignore) {
                }
            }
            return EnumSet.of(OPERATION.NONE); // unknown error, always asking
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
                return Storage.getDisplayName(context, calcsStart.get(0).uri);
            } else {
                String str = Storage.getDisplayName(context, calcUri) + "{";
                for (Storage.Node u : calcsStart)
                    str += Storage.getName(context, u.uri) + ",";
                str = stripRight(str, ",");
                str += "}";
                return str;
            }
        }

        public String formatCalc() {
            return Storage.getDisplayName(context, files.get(files.size() - 1).uri);
        }

        public void post() {
        }
    }

    public static class OperationBuilder extends AlertDialog.Builder {
        public View v;

        public TextView title;
        public TextView from;
        public TextView to;
        public ProgressBar progressFile;
        public ProgressBar progressTotal;
        public TextView filesCount;
        public TextView filesTotal;

        public AlertDialog d;

        public DialogInterface.OnDismissListener dismiss = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
            }
        };
        public View.OnClickListener neutral = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        };
        public View.OnClickListener negative = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        };

        public OperationBuilder(Context context) {
            super(context);
        }

        void create(int id) {
            setCancelable(false);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            v = inflater.inflate(id, null);
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

            title = (TextView) v.findViewById(R.id.title);
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

    public static class DeleteBuilder extends OperationBuilder {
        public Handler handler = new Handler();
        public PendingOperation op;

        public DeleteBuilder(Context context) {
            super(context);
            create(R.layout.paste);
            setTitle(getContext().getString(R.string.files_deleting));
        }

        public DeleteBuilder(Context context, Uri uri, ArrayList<Storage.Node> selected) {
            this(context);
            create(uri, selected);
        }

        public void create(Uri uri, ArrayList<Storage.Node> selected) {
            op = new PendingOperation(getContext(), uri, selected) {
                @Override
                public void run() {
                    try {
                        if (calcIndex < calcs.size()) {
                            deleteCalc();
                            return;
                        }
                        if (filesIndex < files.size()) {
                            deleteProcess();
                            return;
                        }
                        success();
                    } catch (RuntimeException e) {
                        deleteError(e);
                    }
                }

                public void post() {
                    handler.removeCallbacks(this);
                    handler.post(this);
                }
            };
            neutral = new View.OnClickListener() { // pause/resume
                @Override
                public void onClick(View v) {
                    final View.OnClickListener neutral = this;
                    op.pause();
                    handler.removeCallbacks(op);
                    final Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setText(R.string.copy_resume);
                    DeleteBuilder.this.neutral = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            op.run();
                            b.setText(R.string.copy_pause);
                            DeleteBuilder.this.neutral = neutral;
                        }
                    };
                }
            };
            dismiss = new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    handler.removeCallbacks(op);
                    op.close();
                    dismiss();
                }
            };
        }

        public void deleteCalc() {
            if (!op.calc())
                Collections.sort(op.files, new SortDelete());
            title.setGravity(Gravity.NO_GRAVITY);
            title.setText(op.context.getString(R.string.files_deleting) + ": " + op.formatStart());
            update(op);
            progressFile.setVisibility(View.GONE);
            from.setText(op.context.getString(R.string.files_calculating) + ": " + op.formatCalc());
            to.setVisibility(View.GONE);
            op.post();
        }

        public void deleteProcess() {
            int old = op.filesIndex;
            op.f = op.files.get(op.filesIndex);
            if (!op.storage.delete(op.f.uri))
                throw new RuntimeException("Unable to delete: " + op.f.name);
            if (!op.f.dir)
                op.processed += op.f.size;
            op.filesIndex++;
            from.setText(op.context.getString(R.string.files_deleting) + ": " + op.formatStart());
            update(op, old, op.f);
            progressFile.setVisibility(View.GONE);
            from.setText(Storage.getDisplayName(getContext(), op.f.uri));
            to.setVisibility(View.GONE);
            op.f = null;
            op.post();
        }

        public void deleteError(Throwable e) {
            switch (op.check(e).iterator().next()) {
                case SKIP:
                    Log.e(TAG, "skip", e);
                    op.filesIndex++;
                    op.cancel();
                    op.post();
                    return;
            }
            pasteError(DeleteBuilder.this, op, e, true);
        }

        public void success() { // success!
            dismiss();
            Toast.makeText(getContext(), op.context.getString(R.string.toast_files_deleted, op.files.size()), Toast.LENGTH_SHORT).show();
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = super.show();
            op.run();
            return d;
        }
    }

    public class PropertiesBuilder extends OperationBuilder {
        public Handler handler = new Handler();
        public PendingOperation op;
        public TextView props;
        public View sums; //md5sums
        public View sumscalc;
        public ProgressBar progress;
        public TextView md5;
        public TextView sha1;
        public Thread thread; // sums stream

        public PropertiesBuilder(Context context) {
            super(context);
            create(R.layout.properties);
            setCancelable(true);
            setTitle(getString(R.string.properties));
        }

        public PropertiesBuilder(Context context, Uri uri, ArrayList<Storage.Node> selected) {
            this(context);
            create(uri, selected);
            props = (TextView) v.findViewById(R.id.properties);
        }

        public void create(Uri uri, ArrayList<Storage.Node> selected) {
            op = new PendingOperation(getContext(), uri, selected) {
                @Override
                public void run() {
                    try {
                        if (calcIndex < calcs.size()) {
                            propsCalc();
                            return;
                        }
                        success();
                    } catch (RuntimeException e) {
                        propsError(e);
                    }
                }

                public void post() {
                    handler.removeCallbacks(this);
                    handler.post(this);
                }
            };
            neutral = new View.OnClickListener() { // pause/resume
                @Override
                public void onClick(View v) {
                    final View.OnClickListener neutral = this;
                    op.pause();
                    handler.removeCallbacks(op);
                    final Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setText(R.string.copy_resume);
                    PropertiesBuilder.this.neutral = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            op.run();
                            b.setText(R.string.copy_pause);
                            PropertiesBuilder.this.neutral = neutral;
                        }
                    };
                }
            };
            dismiss = new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    handler.removeCallbacks(op);
                    op.close();
                    dismiss();
                }
            };
            sums = v.findViewById(R.id.sums);
            sumscalc = v.findViewById(R.id.sumscalc);
            md5 = (TextView) v.findViewById(R.id.md5);
            sha1 = (TextView) v.findViewById(R.id.sha1);
            progress = (ProgressBar) v.findViewById(R.id.progress);
            View copymd5 = v.findViewById(R.id.copy_md5);
            copymd5.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copy(getContext(), md5.getText().toString());
                    Toast.makeText(getContext(), "Copied", Toast.LENGTH_SHORT).show();
                }
            });
            View copysha1 = v.findViewById(R.id.copy_sha1);
            copysha1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copy(getContext(), sha1.getText().toString());
                    Toast.makeText(getContext(), "Copied", Toast.LENGTH_SHORT).show();
                }
            });
            final Button calc = (Button) v.findViewById(R.id.calc);
            calc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    calc.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
                    calc.setTextColor(Color.LTGRAY);
                    calc.setOnClickListener(null);
                    calcSums();
                }
            });
            dismiss = new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
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
            };
        }

        public void calcSums() {
            final InputStream is;
            String s = op.calcUri.getScheme();
            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                try {
                    is = getContext().getContentResolver().openInputStream(op.calcUri);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                File file = Storage.getFile(op.files.get(0).uri);
                try {
                    is = new FileInputStream(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new Storage.UnknownUri();
            }
            thread = new Thread() {
                @Override
                public void run() {
                    try {
                        final MessageDigest md5 = MessageDigest.getInstance("MD5");
                        final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                        IOUtils.copy(is, new OutputStream() {
                            long pos = 0;

                            @Override
                            public void write(int b) throws IOException {
                                md5.update((byte) b);
                                sha1.update((byte) b);
                                pos++;
                                update();
                            }

                            @Override
                            public void write(@NonNull byte[] b, int off, int len) throws IOException {
                                md5.update(b, off, len);
                                sha1.update(b, off, len);
                                pos += len;
                                update();
                            }

                            void update() throws IOException {
                                if (Thread.currentThread().isInterrupted())
                                    throw new IOException(new InterruptedException());
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        progress.setProgress((int) (pos * 100 / op.total));
                                    }
                                });
                            }
                        });
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                thread = null;
                                PropertiesBuilder.this.md5.setText(MD5.hex(md5.digest()));
                                PropertiesBuilder.this.sha1.setText(MD5.hex(sha1.digest()));
                                sums.setVisibility(View.VISIBLE);
                                sumscalc.setVisibility(View.GONE);
                            }
                        });
                    } catch (Exception e) {
                        Log.d(TAG, "crc error", e);
                    } finally {
                        Log.d(TAG, "crc done");
                    }
                }
            };
            thread.start();
        }

        public void propsCalc() {
            if (!op.calc())
                Collections.sort(op.files, new SortDelete());
            title.setGravity(Gravity.LEFT);
            title.setText(op.context.getString(R.string.files_calculating) + ": " + op.formatCalc());
            update(op);
            from.setText(op.formatStart());
            op.post();
        }

        public void update(final PendingOperation op) {
            filesCount.setText("" + op.files.size());
            filesTotal.setText(FilesApplication.formatSize(getContext(), op.total) + " (" + BYTES.format(op.total) + " " + getContext().getString(R.string.size_bytes) + ")");
            StringBuffer sb = new StringBuffer();
            String s = op.calcUri.getScheme();
            if (s.equals(ContentResolver.SCHEME_CONTENT) && op.calcUri.getAuthority().startsWith(Storage.SAF))
                sb.append("mount: SAF"); // Underlying filesystem: unknown, owners/group: unknown, attributes: unknown, thanks google!
            if (s.equals(ContentResolver.SCHEME_FILE)) {
                MountInfo mount = new MountInfo();
                MountInfo.Info info = mount.findMount(Storage.getFile(op.calcUri));
                if (info != null)
                    sb.append("mount: " + info.fs);
            }
            Uri uri = op.files.get(0).uri;
            File file = null;
            if (op.files.size() == 1) { // show attributes
                long last = 0;
                SuperUser.DF df = null;
                if (s.equals(ContentResolver.SCHEME_CONTENT) && op.calcUri.getAuthority().startsWith(Storage.SAF)) {
                    if (storage.getRoot()) {
                        Uri otg = StorageProvider.filterFolderIntent(getContext(), uri);
                        if (uri == otg)
                            otg = StorageProvider.filterOTGFolderIntent(storage, uri);
                        if (uri != otg) {
                            File file2 = Storage.getFile(otg);
                            MountInfo mount = new MountInfo();
                            MountInfo.Info info = mount.findMount(file2);
                            if (info != null)
                                sb.append("\nunderlying: " + info.fs);
                            else
                                sb.append("\nunderlying: unknown"); // Underlying filesystem: unknown, owners/group: unknown, attributes: unknown, thanks google!
                            df = new SuperUser.DF(storage.getSu(), file2);
                            last = SuperUser.lastModified(storage.getSu(), file2);
                        } // else we can open document inputstream and get real path using fd
                    } else {
                        last = Storage.getLastModified(getContext(), op.calcUri);
                    }
                }
                file = Storage.getFile(uri);
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    if (storage.getRoot()) {
                        df = new SuperUser.DF(storage.getSu(), file);
                        last = SuperUser.lastModified(storage.getSu(), file);
                    } else {
                        df = new SuperUser.DF(file);
                        last = file.lastModified();
                    }
                }
                if (df != null) {
                    if (df.nodes != 0) {
                        sb.append("\nnodes: " + df.nodes);
                        sb.append("\nnfree: " + df.nfree);
                        sb.append("\nblock size: " + df.bsize);
                        sb.append("\ntotal blocks: " + df.blocks);
                        sb.append("\n");
                    }
                    sb.append("\nmode: " + df.getMode());
                    sb.append("\ninode: " + df.inode);
                    sb.append("\nowner: " + df.user + "/" + df.group);
                }
                if (last != 0)
                    sb.append("\nmodified: " + SIMPLE.format(new Date(last)));
            }
            if (file != null && file.isFile()) {
                sums.setVisibility(View.GONE);
                sumscalc.setVisibility(View.VISIBLE);
                calcSums();
            } else {
                sums.setVisibility(View.GONE);
                sumscalc.setVisibility(View.GONE);
            }
            String str = sb.toString();
            if (str.isEmpty())
                props.setVisibility(View.GONE);
            else
                props.setText(sb.toString());
        }

        public void propsError(Throwable e) {
            switch (op.check(e).iterator().next()) {
                case SKIP:
                    Log.e(TAG, "skip", e);
                    op.filesIndex++;
                    op.cancel();
                    op.post();
                    return;
            }
            pasteError(PropertiesBuilder.this, op, e, true);
        }

        public void success() { // success!
            title.setVisibility(View.INVISIBLE);
            final Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
            b.setVisibility(View.INVISIBLE);
            op.storage.closeSu();
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = super.show();
            op.run();
            return d;
        }
    }

    public static class PasteBuilder extends OperationBuilder {
        Handler handler = new Handler();
        PendingOperation op;
        String n;
        HashMap<String, String> rename = new HashMap<>();

        public PasteBuilder(Context context) {
            super(context);
            create(R.layout.paste);
        }

        public void create(Uri calcUri, final ArrayList<Storage.Node> ff, final boolean move, final Uri uri) {
            if (move)
                n = getContext().getString(R.string.files_moving);
            else
                n = getContext().getString(R.string.files_copying);
            setTitle(n);

            op = new PendingOperation(getContext(), calcUri, ff) {
                int deleteIndex = -1;
                ArrayList<Storage.Node> delete = new ArrayList<>();

                @Override
                public void run() {
                    try {
                        if (calcIndex < calcs.size()) {
                            if (!calc()) {
                                if (move)
                                    Collections.sort(files, new SortMove()); // we have to sort only if array contains symlinks and move operation
                                calcDone();
                            }
                            title.setGravity(Gravity.NO_GRAVITY);
                            title.setText(context.getString(R.string.files_calculating) + ": " + formatCalc());
                            update(this);
                            from.setText(context.getString(R.string.copy_from) + " " + formatStart());
                            to.setText(context.getString(R.string.copy_to) + " " + Storage.getDisplayName(context, uri));
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
                                                    if (move)
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
                                title.setGravity(Gravity.CENTER);
                                title.setText(n + " " + FilesApplication.formatSize(context, a) + context.getString(R.string.per_second) + ", " + e);
                                update(this, old, f);
                                from.setText(context.getString(R.string.copy_from) + " " + Storage.getDisplayName(context, f.uri));
                                to.setText(context.getString(R.string.copy_to) + " " + Storage.getDisplayName(context, oldt));
                                return;
                            }
                        }
                        if (filesIndex < files.size()) {
                            cancel(); // cancel previous skiped operation if existed
                            Storage.Node f = files.get(filesIndex);
                            Storage.Node target = getTarget(f); // special Node with no 'uri' if not found, and full path 'name'
                            try {
                                if (f.dir) {
                                    if (!(target.uri != null && target.dir) && storage.mkdir(uri, target.name) == null)
                                        throw new RuntimeException("Unable create dir: " + target);
                                    filesIndex++;
                                    if (move) {
                                        delete.add(f);
                                        deleteIndex = delete.size() - 1; // reverse index
                                    }
                                } else {
                                    if (target.uri != null && !target.dir) {
                                        switch (check(f, target).iterator().next()) {
                                            case NONE:
                                                break;
                                            case ASK:
                                                pasteConflict(PasteBuilder.this, this, f, target);
                                                return;
                                            case SKIP:
                                                filesIndex++;
                                                post();
                                                return;
                                            case OVERWRITE:
                                                storage.delete(target.uri);
                                                break;
                                        }
                                    }
                                    if (f instanceof Storage.SymlinkNode) {
                                        Storage.SymlinkNode l = (Storage.SymlinkNode) f;
                                        if (storage.symlink(l, uri) || l.isSymDir()) { // if fails, continue with content copy
                                            filesIndex++;
                                            if (move)
                                                storage.delete(f.uri);
                                            post();
                                            return;
                                        } else { // we about to copy symlinks as files, update total!!!
                                            total += SuperUser.length(storage.getSu(), Storage.getFile(f.uri));
                                        }
                                    }
                                    if (move && storage.mv(f.uri, uri, target.name)) { // try same node device 'mv' operation
                                        filesIndex++;
                                        processed += f.size;
                                        post();
                                        return;
                                    }
                                    open(f, uri, target.name);
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
                            title.setVisibility(View.GONE);
                            progressFile.setVisibility(View.GONE);
                            progressTotal.setVisibility(View.GONE);
                            from.setText(context.getString(R.string.files_deleting) + ": " + Storage.getDisplayName(context, f.uri));
                            to.setVisibility(View.GONE);
                            post();
                            return;
                        }
                        success();
                    } catch (RuntimeException e) {
                        switch (check(e).iterator().next()) {
                            case SKIP:
                                Log.e(TAG, "skip", e);
                                filesIndex++;
                                cancel();
                                post();
                                return;
                        }
                        pasteError(PasteBuilder.this, this, e, move);
                    }
                }

                Storage.Node getTarget(Storage.Node f) {
                    String target;
                    String p = getFirst(f.name);
                    String r = rename.get(p);
                    if (r != null)
                        target = new File(r, Storage.relative(p, f.name)).getPath();
                    else
                        target = f.name;
                    Storage.Node t;
                    String s = uri.getScheme();
                    if (s.equals(ContentResolver.SCHEME_FILE)) {
                        File k = Storage.getFile(uri);
                        File m = new File(k, target);
                        if (storage.getRoot()) {
                            t = new Storage.Node(m);
                            if (SuperUser.exists(storage.getSu(), m))
                                t.dir = SuperUser.isDirectory(storage.getSu(), m);
                            else
                                t.uri = null;
                        } else {
                            t = new Storage.Node(m);
                            if (!m.exists())
                                t.uri = null;
                        }
                    } else if (Build.VERSION.SDK_INT >= 21 && s.equals(ContentResolver.SCHEME_CONTENT)) {
                        DocumentFile k = Storage.getDocumentFile(context, uri, target); // target == path
                        if (k != null && k.exists())
                            t = new Storage.Node(k);
                        else
                            t = new Storage.Node(); // t.uri = null && t.dir = false
                    } else {
                        throw new Storage.UnknownUri();
                    }
                    t.name = target;
                    return t;
                }

                public void post() {
                    post(0);
                }

                public void post(long l) {
                    handler.removeCallbacks(this);
                    handler.postDelayed(this, l);
                }
            };

            neutral = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final View.OnClickListener neutral = this;
                    op.pause();
                    handler.removeCallbacks(op);
                    final Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setText(R.string.copy_resume);
                    PasteBuilder.this.neutral = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            op.run();
                            b.setText(R.string.copy_pause);
                            PasteBuilder.this.neutral = neutral;
                        }
                    };
                }
            };

            dismiss = new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    op.close();
                    dismiss();
                    handler.removeCallbacks(op);
                }
            };
        }

        public void calcDone() {
        }

        public void success() {
            dismiss(); // all done!
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = super.show();
            op.run();
            return d;
        }
    }

    public static class RecycleBuilder extends PasteBuilder {
        File trash;

        public RecycleBuilder(Context context, Uri calcUri, ArrayList<Storage.Node> ff, File trash) {
            super(context);
            this.trash = trash;
            Uri uri = Uri.fromFile(trash);
            create(calcUri, ff, true, uri);
            setTitle(n = getContext().getString(R.string.files_deleting));
            for (Storage.Node f : ff) {
                File m = new File(trash, f.name);
                if (m.exists()) {
                    String next = null;
                    boolean dup = true;
                    for (int i = 0; dup; i++) {
                        String n = Storage.getNameNoExt(m);
                        String e = Storage.getExt(m);
                        n = Storage.filterDups(n);
                        next = Storage.getNextFile(trash, n, i, e).getName();
                        dup = false;
                        for (String v : rename.values()) {
                            if (v.equals(next)) {
                                dup = true;
                                break;
                            }
                        }
                    }
                    rename.put(f.name, next);
                }
            }
        }

        @Override
        public void calcDone() {
            if (!trash.exists())
                trash.mkdir();
        }

        public void success() { // success!
            super.success();
            Toast.makeText(getContext(), getContext().getString(R.string.toast_files_deleted, op.files.size()), Toast.LENGTH_SHORT).show();
        }
    }

    public static class SortByName implements Comparator<Storage.Node> { // by name files first
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            return o1.name.compareTo(o2.name);
        }
    }

    public static class SortByDate implements Comparator<Storage.Node> { // by last modified
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            return Long.valueOf(o1.last).compareTo(o2.last);
        }
    }

    public static class SortBySize implements Comparator<Storage.Node> { // by size
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            return Long.valueOf(o1.size).compareTo(o2.size);
        }
    }

    public static class SortByType implements Comparator<Storage.Node> { // by type folders first
        Comparator<Storage.Node> wrap;

        public SortByType(Comparator<Storage.Node> wrap) {
            this.wrap = wrap;
        }

        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            Boolean d1 = o1.dir || (o1 instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) o1).isSymDir());
            Boolean d2 = o2.dir || (o2 instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) o2).isSymDir());
            int c = d2.compareTo(d1);
            if (c != 0)
                return c;
            return wrap.compare(o1, o2);
        }
    }

    public static class SortMove implements Comparator<Storage.Node> { // we have to move symlinks first during move operation only
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            Boolean d1 = o1.dir;
            Boolean d2 = o2.dir;
            int c = d2.compareTo(d1);
            if (c != 0)
                return c; // directories first (ordered by lexagraphcally)
            Boolean s1 = o1 instanceof Storage.SymlinkNode;
            Boolean s2 = o2 instanceof Storage.SymlinkNode;
            if (s1 && !s2)
                return -1;
            if (!s1 && s2)
                return 1; // symlinks first (ordered by lexagraphcally)
            return o1.name.compareTo(o2.name);
        }
    }

    public static class SortDelete implements Comparator<Storage.Node> { // deepest files first
        @Override
        public int compare(Storage.Node o1, Storage.Node o2) {
            int c = Boolean.valueOf(o1.dir).compareTo(o2.dir);
            if (c != 0)
                return c;
            String[] cc2 = Storage.splitPath(o2.name);
            String[] cc1 = Storage.splitPath(o1.name);
            c = Integer.valueOf(cc2.length).compareTo(cc1.length);
            if (c != 0)
                return c;
            return cc1[cc1.length - 1].compareTo(cc2[cc2.length - 1]);
        }
    }

    public static class Holder extends RecyclerView.ViewHolder {
        int accent;
        int primary;
        public ImageView icon;
        public ImageView iconSmall;
        public ProgressBar progress;
        public TextView name;
        public View circleFrame;
        public View circle;
        public View unselected;
        public View selected;
        public TextView size;

        public Holder(View itemView) {
            super(itemView);
            accent = ThemeUtils.getThemeColor(itemView.getContext(), R.attr.colorAccent);
            primary = ThemeUtils.getThemeColor(itemView.getContext(), R.attr.colorPrimary);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            icon.setColorFilter(accent);
            progress = (ProgressBar) itemView.findViewById(R.id.progress);
            iconSmall = (ImageView) itemView.findViewById(R.id.icon_small);
            name = (TextView) itemView.findViewById(R.id.name);
            size = (TextView) itemView.findViewById(R.id.size);
            circleFrame = itemView.findViewById(R.id.circle_frame);
            circle = itemView.findViewById(R.id.circle);
            unselected = itemView.findViewById(R.id.unselected);
            selected = itemView.findViewById(R.id.selected);
        }
    }

    public class Adapter extends CacheImagesRecyclerAdapter<Holder> {
        ArrayList<Storage.Node> files = new ArrayList<>();

        public Adapter(Context context) {
            super(context);
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(getContext()).inflate(R.layout.file_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final Holder h, final int position) {
            final Storage.Node f = files.get(position);
            final boolean dir = f.dir || (f instanceof Storage.SymlinkNode && ((Storage.SymlinkNode) f).isSymDir());
            if (dir) {
                downloadTaskClean(h.itemView);
                downloadTaskUpdate(null, f, h.itemView);
                h.icon.setImageResource(R.drawable.ic_folder_black_24dp);
                h.icon.setColorFilter(h.accent);
                h.size.setVisibility(View.GONE);
            } else {
                if (CacheImagesAdapter.isVideo(f.name) || CacheImagesAdapter.isImage(f.name) || CacheImagesAdapter.isAudio(f.name)) {
                    downloadTask(f, h.itemView);
                } else {
                    downloadTaskClean(h.itemView);
                    downloadTaskUpdate(null, f, h.itemView);
                    h.icon.setImageResource(R.drawable.ic_file);
                }
                if (f.size == -1) {
                    h.size.setVisibility(View.GONE); // link points to non existent file or unable to stat file
                } else {
                    h.size.setText(FilesApplication.formatSize(getContext(), f.size));
                    h.size.setVisibility(View.VISIBLE);
                }
            }
            h.name.setText(f.name);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (MenuItemCompat.isActionViewExpanded(toolbar)) {
                        if (selected.contains(f.uri))
                            selected.remove(f.uri);
                        else
                            selected.add(f);
                        updateSelection();
                        return;
                    }
                    if (dir) {
                        load(f.uri, false);
                    } else {
                        PopupMenu menu = new PopupMenu(getContext(), v);
                        menu.inflate(R.menu.menu_file);
                        Storage.ArchiveReader r = storage.fromArchive(f.uri, true);
                        if (r != null && r.isDirectory()) {
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_view);
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_openaspicture);
                            r.close();
                        } else if (CacheImagesAdapter.isImage(Storage.getName(getContext(), f.uri))) {
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_view);
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_openasarchive);
                        } else {
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_openasarchive);
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_openaspicture);
                        }
                        ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_openasfolder);
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
                    try {
                        selected.clear();
                        selected.add(f);
                        openSelection();
                        PopupMenu menu = new PopupMenu(getContext(), v);
                        menu.inflate(R.menu.menu_select);
                        Storage.ArchiveReader r = storage.fromArchive(uri, true);
                        if (r != null) {
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_archive);
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_rename);
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_cut);
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_delete);
                            r.close();
                        }
                        if (dir)
                            ToolbarActionView.hideMenu(menu.getMenu(), R.id.action_share);
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
                    } catch (RuntimeException e) {
                        Log.e(TAG, "io", e);
                        error.setText(ErrorDialog.toMessage(e));
                        error.setVisibility(View.VISIBLE);
                    } finally {
                        storage.closeSu();
                    }
                    return true;
                }
            });
            h.circleFrame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (selected.contains(f.uri))
                            selected.remove(f.uri);
                        else
                            selected.add(f);
                        openSelection();
                    } catch (RuntimeException e) {
                        Log.e(TAG, "io", e);
                        error.setText(ErrorDialog.toMessage(e));
                        error.setVisibility(View.VISIBLE);
                    } finally {
                        storage.closeSu();
                    }
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

        @Override
        public Bitmap downloadImageTask(CacheImagesAdapter.DownloadImageTask task) {
            try {
                Storage.Node n = (Storage.Node) task.item;
                File cover = CacheImagesAdapter.cacheUri(getContext(), n.uri);
                Storage storage = new Storage(getContext());
                try {
                    if (!cover.exists() || cover.length() == 0) {
                        Bitmap bm;
                        if (CacheImagesAdapter.isVideo(n.name)) {
                            bm = storage.createVideoThumbnail(n.uri);
                        } else if (CacheImagesAdapter.isAudio(n.name)) {
                            bm = storage.createAudioThumbnail(n.uri);
                        } else {
                            InputStream is = storage.open(n.uri);
                            bm = CacheImagesAdapter.createThumbnail(is);
                            is.close();
                        }
                        if (bm == null)
                            return null;
                        OutputStream os = new FileOutputStream(cover);
                        os = new BufferedOutputStream(os);
                        bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                        os.close();
                        return bm;
                    }
                    return BitmapFactory.decodeStream(new FileInputStream(cover));
                } catch (IOException e) {
                    cover.delete();
                    throw new RuntimeException(e);
                } finally {
                    storage.closeSu();
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to load cover", e);
            }
            return null;
        }

        @Override
        public void downloadTaskUpdate(CacheImagesAdapter.DownloadImageTask task, Object item, Object view) {
            Holder h = new Holder((View) view);
            if (task == null || task.bm == null) {
                h.icon.setImageResource(R.drawable.ic_image_black_24dp);
                h.icon.setColorFilter(h.accent);
            } else {
                h.icon.setImageBitmap(task.bm);
                h.icon.setColorFilter(null);
            }
            h.progress.setVisibility((task == null || task.done) ? View.GONE : View.VISIBLE);
            h.progress.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        }

        public boolean pending(Storage.Node n) {
            return app.copy != null && app.copy.contains(n.uri) || app.cut != null && app.cut.contains(n.uri);
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
        storage = new Storage(getContext());
        adapter = new Adapter(getContext());
        specials = new Specials(getContext());
        IntentFilter filter = new IntentFilter();
        filter.addAction(PASTE_UPDATE);
        filter.addAction(MOVE_UPDATE);
        getContext().registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(receiver);
        Storage.SAF_CACHE.remove(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        uri = getUri(); // set uri after view created

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        path = (PathView) rootView.findViewById(R.id.path);
        path.listener = new PathView.Listener() {
            @Override
            public void onUriSelected(Uri u) {
                try {
                    load(u, false);
                } catch (RuntimeException e) {
                    Log.e(TAG, "reload()", e);
                    error.setText(e.getMessage());
                    error.setVisibility(View.VISIBLE);
                    storage.closeSu();
                }
            }
        };
        path.setUri(uri);

        layout = new LinearLayoutManager(getContext()) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                RecyclerView.SmoothScroller smoothScroller = new TopSnappedSmoothScroller(recyclerView.getContext());
                smoothScroller.setTargetPosition(position);
                startSmoothScroll(smoothScroller);
            }
        };

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
        return rootView;
    }

    void updatePaste() { // closeSu()
        if (app.copy != null || app.cut != null) {
            pasteMenu.setVisible(true);
            pasteCancel.setVisible(true);
            Storage.ArchiveReader r = storage.fromArchive(uri, true);
            if (r != null)
                ToolbarActionView.setEnable(pasteMenu, false);
            else
                ToolbarActionView.setEnable(pasteMenu, true);
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

    public void load(Uri u, boolean bookmark) {
        if (bookmark)
            Storage.SAF_CACHE.get(this).clear();
        else
            Storage.SAF_CACHE.get(this).removeParents(u, true);
        if (uri == null) {
            getArguments().putParcelable("uri", u);
        } else {
            old = uri;
            offsets.put(uri, findFirstVisibleItem());
            uri = u;
            updateButton();
            updatePaste(); // enabled / disabled
            closeSelection();
            reload();
            Storage.SAF_CACHE.get(this).addParents(uri, adapter.files);
            path.setUri(uri);
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

        path.updateHeader();

        adapter.files.clear();
        try {
            ArrayList<Storage.Node> nn = storage.list(uri);
            adapter.files.addAll(nn);
        } catch (RuntimeException e) {
            Log.e(TAG, "reload()", e);
            error.setText(e.getMessage());
            error.setVisibility(View.VISIBLE);
            return;
        } finally {
            storage.closeSu();
        }
        sort();

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

        Storage.SAF_CACHE.get(this).addParents(uri, adapter.files);
    }

    void sort() {
        Collections.sort(adapter.files, sort(getContext()));
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
        updateButton();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        invalidateOptionsMenu = InvalidateOptionsMenuCompat.onCreateOptionsMenu(this, menu, inflater);

        try {
            toolbar = menu.findItem(R.id.action_selected);
            MainActivity main = (MainActivity) getActivity();
            main.collapseListener.addItem(toolbar);
            pasteMenu = menu.findItem(R.id.action_paste);
            pasteCancel = menu.findItem(R.id.action_paste_cancel);
            updatePaste();
            select = (ToolbarActionView) MenuItemCompat.getActionView(toolbar);
            select.listener = new CollapsibleActionView() {
                @Override
                public void onActionViewExpanded() {
                    Storage.ArchiveReader r = storage.fromArchive(uri, true);
                    if (r != null) {
                        select.hide(R.id.action_archive);
                        select.hide(R.id.action_cut);
                        select.hide(R.id.action_delete);
                        r.close();
                    }
                }

                @Override
                public void onActionViewCollapsed() {
                    adapter.notifyDataSetChanged();
                    selected.clear();
                }
            };
            select.create(menu, R.menu.menu_select);
            select.hide(R.id.action_rename);
            MenuItem sort = menu.findItem(R.id.action_sort);
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
            int selected = getContext().getResources().getIdentifier(shared.getString(FilesApplication.PREFERENCE_SORT, getContext().getResources().getResourceEntryName(R.id.sort_name_ask)), "id", getContext().getPackageName());
            SubMenu sorts = sort.getSubMenu();
            for (int i = 0; i < sorts.size(); i++) {
                MenuItem m = sorts.getItem(i);
                if (m.getItemId() == selected)
                    m.setChecked(true);
                m.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return false;
                    }
                });
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "io", e);
            error.setText(ErrorDialog.toMessage(e));
            error.setVisibility(View.VISIBLE);
        } finally {
            storage.closeSu();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected " + Storage.getDisplayName(getContext(), uri) + " " + item);
        int id = item.getItemId();
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        switch (id) {
            case R.id.sort_modified_ask:
            case R.id.sort_modified_desc:
            case R.id.sort_name_ask:
            case R.id.sort_name_desc:
            case R.id.sort_size_ask:
            case R.id.sort_size_desc: {
                shared.edit().putString(FilesApplication.PREFERENCE_SORT, getContext().getResources().getResourceEntryName(item.getItemId())).commit();
                sort();
                invalidateOptionsMenu.run();
                return true;
            }
            case R.id.action_open: {
                Intent intent = item.getIntent();
                Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
                if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                    startActivity(open);
                else
                    Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
                return true;
            }
            case R.id.action_openas: {
                return true;
            }
            case R.id.action_openastext: {
                Intent intent = item.getIntent();
                Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
                open.setDataAndType(open.getData(), "text/*");
                if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                    startActivity(open);
                else
                    Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
                return true;
            }
            case R.id.action_openasarchive: {
                try {
                    Uri uri = item.getIntent().getData();
                    load(uri, false);
                } finally {
                    storage.closeSu();
                }
                return true;
            }
            case R.id.action_openaspicture: {
                try {
                    Uri uri = item.getIntent().getData();
                    FullscreenActivity.start(getContext(), uri);
                } finally {
                    storage.closeSu();
                }
                return true;
            }
            case R.id.action_view: {
                try {
                    Uri uri = item.getIntent().getData();
                    MainActivity main = (MainActivity) getActivity();
                    main.openHex(uri, true);
                } finally {
                    storage.closeSu();
                }
                return true;
            }
            case R.id.action_openasimage: {
                Intent intent = item.getIntent();
                Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
                open.setDataAndType(open.getData(), "image/*");
                if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                    startActivity(open);
                else
                    Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
                return true;
            }
            case R.id.action_openasaudio: {
                Intent intent = item.getIntent();
                Intent open = StorageProvider.getProvider().openIntent(intent.getData(), intent.getStringExtra("name"));
                open.setDataAndType(open.getData(), "audio/*");
                if (OptimizationPreferenceCompat.isCallable(getContext(), open))
                    startActivity(open);
                else
                    Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
                return true;
            }
            case R.id.action_share: {
                Intent intent = item.getIntent();
                Intent share;
                if (intent != null) {
                    share = StorageProvider.getProvider().shareIntent(intent.getData(), intent.getType(), intent.getStringExtra("name"));
                } else {
                    if (selected.size() == 1) {
                        Storage.Node n = selected.get(0);
                        share = StorageProvider.getProvider().shareIntent(n.uri, Storage.getTypeByName(n.name), Storage.getNameNoExt(n.name));
                    } else {
                        String name = "";
                        TreeMap<String, Integer> nn = new TreeMap<>();
                        ArrayList<Uri> ll = new ArrayList<>();
                        for (Storage.Node n : selected) {
                            ll.add(n.uri);
                            String e = Storage.getExt(n.name);
                            Integer old = nn.get(e);
                            if (old == null)
                                old = 0;
                            nn.put(e, old + 1);
                        }
                        for (String key : nn.keySet())
                            name += "(" + nn.get(key) + ") ." + key + ", ";
                        name = stripRight(name, ", ");
                        share = StorageProvider.getProvider().shareIntent(ll, "*/*", name); // need smart type? image/* || image/gif || */*
                    }
                }
                if (OptimizationPreferenceCompat.isCallable(getContext(), share))
                    startActivity(share);
                else
                    Toast.makeText(getContext(), R.string.unsupported, Toast.LENGTH_SHORT).show();
                return true;
            }
            case R.id.action_refresh: {
                reload();
                return true;
            }
            case R.id.action_paste_cancel: {
                app.copy = null;
                app.cut = null;
                updatePaste();
                storage.closeSu();
                return true;
            }
            case R.id.action_paste: {
                if (!item.isEnabled())
                    return true;
                if (paste != null)
                    return true;
                if (app.uri.equals(uri)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.duplicate_folder);
                    builder.setMessage(R.string.are_you_sure);
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            paste = new PasteBuilder(getContext()) {
                                @Override
                                public void dismiss() {
                                    super.dismiss();
                                    paste = null;
                                    reload();
                                }
                            };
                            ArrayList<Storage.Node> ff = null;
                            if (app.copy != null)
                                ff = app.copy;
                            if (app.cut != null)
                                ff = app.cut;
                            for (Storage.Node f : ff) {
                                String n = Storage.getNameNoExt(f.name);
                                String e = Storage.getExt(f.name);
                                String t = Storage.getNextName(getContext(), app.uri, n, e);
                                paste.rename.put(f.name, t);
                            }
                            paste.create(app.uri, ff, false, uri);
                            paste.show();
                        }
                    });
                    builder.show();
                    return true;
                } else {
                    paste = new PasteBuilder(getContext()) {
                        @Override
                        public void success() {
                            super.success();
                            if (app.cut != null) {
                                app.cut = null; // prevent move twice
                                updatePaste();
                            }
                        }

                        @Override
                        public void dismiss() {
                            super.dismiss();
                            paste = null;
                            reload();
                            getContext().sendBroadcast(new Intent(MOVE_UPDATE)); // update second tab
                        }
                    };
                    ArrayList<Storage.Node> ff = null;
                    boolean move = false;
                    if (app.copy != null)
                        ff = app.copy;
                    if (app.cut != null) {
                        ff = app.cut;
                        move = true;
                    }
                    paste.create(app.uri, ff, move, uri);
                    paste.show();
                    return true;
                }
            }
            case R.id.action_copy: {
                try {
                    app.copy = new Storage.Nodes(selected);
                    app.cut = null;
                    app.uri = uri;
                    updatePaste();
                    closeSelection();
                    Toast.makeText(getContext(), getString(R.string.toast_files_copied, app.copy.size()), Toast.LENGTH_SHORT).show();
                } catch (RuntimeException e) {
                    Log.e(TAG, "io", e);
                    error.setText(e.getMessage());
                    error.setVisibility(View.VISIBLE);
                } finally {
                    storage.closeSu();
                }
                return true;
            }
            case R.id.action_cut: {
                try {
                    app.copy = null;
                    app.cut = new Storage.Nodes(selected);
                    app.uri = uri;
                    updatePaste();
                    closeSelection();
                    Toast.makeText(getContext(), getString(R.string.toast_files_cut, app.cut.size()), Toast.LENGTH_SHORT).show();
                } catch (RuntimeException e) {
                    Log.e(TAG, "io", e);
                    error.setText(e.getMessage());
                    error.setVisibility(View.VISIBLE);
                } finally {
                    storage.closeSu();
                }
                return true;
            }
            case R.id.action_delete: {
                if (delete != null)
                    return true;
                final Runnable permanently = new Runnable() {
                    @Override
                    public void run() {
                        delete = new DeleteBuilder(getContext(), uri, selected) {
                            @Override
                            public void dismiss() {
                                super.dismiss();
                                delete = null;
                            }

                            @Override
                            public void success() {
                                super.success();
                                closeSelection();
                                reload();
                            }
                        };
                        delete.show();
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.files_delete);
                builder.setMessage(R.string.are_you_sure);
                if (shared.getBoolean(FilesApplication.PREF_RECYCLE, false)) {
                    builder.setNeutralButton(R.string.delete_permanently, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            permanently.run();
                        }
                    });
                }
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File trash = storage.getTrash();
                        boolean t = shared.getBoolean(FilesApplication.PREF_RECYCLE, false);
                        String s = uri.getScheme();
                        if (s.equals(ContentResolver.SCHEME_FILE)) {
                            File f = Storage.getFile(uri);
                            if (Storage.relative(trash, f) != null)
                                t = false; // delete permanenty, operation on trash folder
                        }
                        if (t) {
                            delete = new RecycleBuilder(getContext(), uri, selected, trash) {
                                @Override
                                public void dismiss() {
                                    super.dismiss();
                                    delete = null;
                                }

                                @Override
                                public void success() {
                                    super.success();
                                    closeSelection();
                                    reload();
                                }
                            };
                            delete.show();
                        } else {
                            permanently.run();
                        }
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
            case R.id.action_properties: {
                PropertiesBuilder builder = new PropertiesBuilder(getContext(), uri, selected);
                builder.show();
                return true;
            }
            case R.id.action_rename: {
                if (!item.isEnabled())
                    return true;
                final Uri f = selected.get(0).uri;
                final OpenFileDialog.EditTextDialog dialog = new OpenFileDialog.EditTextDialog(getContext());
                dialog.setTitle(R.string.files_rename);
                dialog.setText(Storage.getName(getContext(), f));
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
            case R.id.action_archive: {
                if (archive != null)
                    return true;
                final String name;
                if (selected.size() == 1)
                    name = selected.get(0).name;
                else
                    name = "Archive";
                String to = Storage.getNextName(getContext(), uri, name, "zip");
                Storage.UriOutputStream os;
                try {
                    os = storage.open(uri, to);
                } catch (IOException e) {
                    Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            final Runnable run = this;
                            choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FOLDER_DIALOG, false) {
                                @Override
                                public void onResult(Uri uri) {
                                    String to = Storage.getNextName(context, uri, name, "zip");
                                    try {
                                        Storage.UriOutputStream os = storage.open(uri, to);
                                        archive(os);
                                    } catch (IOException e) {
                                        ErrorDialog builder = new ErrorDialog(context, e);
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
                archive(os);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    void archive(final Storage.UriOutputStream uos) {
        archive = new OperationBuilder(getContext());
        archive.create(R.layout.paste);
        archive.setTitle(R.string.menu_archive);
        final PendingOperation op = new PendingOperation(getContext(), uri, selected) {
            ZipOutputStream zip;

            {
                t = uos.uri;
            }

            @Override
            public void run() {
                try {
                    if (calcIndex < calcs.size()) {
                        if (!calc())
                            os = zip = new ZipOutputStream(new BufferedOutputStream(uos.os));
                        archive.title.setGravity(Gravity.NO_GRAVITY);
                        archive.title.setText(getString(R.string.files_calculating) + ": " + formatCalc());
                        archive.update(this);
                        archive.from.setText(getString(R.string.files_archiving) + ": " + formatStart());
                        archive.to.setText(getString(R.string.copy_to) + Storage.getDisplayName(context, t));
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
                            archive.title.setGravity(Gravity.CENTER);
                            archive.title.setText(getString(R.string.files_archiving) + " " + FilesApplication.formatSize(context, a) + getString(R.string.per_second) + ", " + e);
                            archive.update(this, old, f);
                            archive.from.setText(getString(R.string.copy_from) + " " + Storage.getDisplayName(context, f.uri));
                            archive.to.setText(getString(R.string.copy_to) + " " + Storage.getDisplayName(context, oldt));
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
                        archive.title.setText(getString(R.string.files_archiving) + ": " + formatStart());
                        archive.update(this, old, f);
                        archive.from.setText(Storage.getDisplayName(context, f.uri));
                        post();
                        return;
                    }
                    Uri to = t;
                    t = null;
                    archive.dismiss();
                    closeSelection();
                    Toast.makeText(getContext(), getString(R.string.toast_files_archived, Storage.getName(context, to), files.size()), Toast.LENGTH_LONG).show();
                    Uri p = Storage.getParent(context, to);
                    if (p == null || !p.equals(uri)) {
                        getContext().sendBroadcast(new Intent(MOVE_UPDATE));
                    } else {
                        reload();
                        highlight(to);
                    }
                } catch (IOException | RuntimeException e) {
                    switch (check(e).iterator().next()) {
                        case SKIP:
                            Log.e(TAG, "skip", e);
                            filesIndex++;
                            cancel();
                            post();
                            return;
                    }
                    pasteError(archive, this, e, false);
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
                op.close();
                archive.dismiss();
                archive = null;
                handler.removeCallbacks(op);
                reload();
            }
        };
        archive.show();
        op.run();
    }

    public void openSelection() { // closeSu()
        app.copy = null;
        app.cut = null;
        updatePaste();
        MenuItemCompat.expandActionView(toolbar);
        updateSelection();
    }

    public void updateSelection() {
        boolean dir = false;
        for (Storage.Node n : selected) {
            if (n.dir) {
                dir = true;
                break;
            }
        }
        select.setEnable(R.id.action_share, !dir);
        adapter.notifyDataSetChanged();
    }

    public void closeSelection() {
        MenuItemCompat.collapseActionView(toolbar);
        updateSelection();
    }

    public void highlight(Uri uri) {
        for (int i = 0; i < adapter.files.size(); i++) {
            Storage.Node n = adapter.files.get(i);
            if (n.uri.equals(uri)) {
                list.smoothScrollToPosition(i);
                final int p = i;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        final RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(p);
                        if (h == null || list.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                            handler.post(this);
                            return;
                        }
                        final View v = new View(getContext());
                        v.setBackgroundColor(Color.YELLOW);
                        ((ViewGroup) h.itemView).addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        AlphaAnimation anim = new AlphaAnimation(0f, 0.5f);
                        anim.setInterpolator(new AccelerateDecelerateInterpolator());
                        anim.setRepeatMode(Animation.REVERSE);
                        anim.setRepeatCount(1);
                        anim.setDuration(250);
                        anim.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {
                            }

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                ((ViewGroup) h.itemView).removeView(v);
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {
                            }
                        });
                        v.startAnimation(anim);
                    }
                });
                return;
            }
        }
    }
}
