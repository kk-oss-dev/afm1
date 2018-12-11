package com.github.axet.filemanager.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.MenuItem;

import com.github.axet.androidlibrary.widgets.AppCompatSettingsThemeActivity;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.filemanager.R;
import com.github.axet.filemanager.app.FilesApplication;
import com.github.axet.filemanager.app.SuperUser;

public class SettingsActivity extends AppCompatSettingsThemeActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    @Override
    public int getAppTheme() {
        return FilesApplication.getTheme(this, FilesApplication.PREF_THEME, R.style.AppThemeLight, R.style.AppThemeDark, getString(R.string.Theme_Dark));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new GeneralPreferenceFragment()).commit();
        setupActionBar();
    }

    @Override
    public String getAppThemeKey() {
        return FilesApplication.PREF_THEME;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        MainActivity.start(this);
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);
            Preference root = findPreference(FilesApplication.PREF_ROOT);
            root.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if ((boolean) newValue) {
                        SuperUser.Result r = SuperUser.rootTest();
                        if (!r.ok()) {
                            Toast.Error(getContext(), r.errno());
                            return false;
                        } else {
                            SuperUser.exitTest(); // second su invoke
                            if (SuperUser.binSuio(getContext()) == null) {
                                Toast.Error(getContext(), "no libsuio.so found");
                                return false;
                            }
                            return true;
                        }
                    }
                    return true;
                }
            });
            if (!SuperUser.isRooted())
                root.setVisible(false);
            bindPreferenceSummaryToValue(findPreference(FilesApplication.PREF_THEME));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
