package com.github.axet.filemanager.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.ListPreference;
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

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
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
                            Toast.makeText(getContext(), r.getMessage(), Toast.LENGTH_LONG).show();
                            return false;
                        } else {
                            SuperUser.exitTest(); // second su invoke
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
