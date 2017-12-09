package com.tarcuri.til;

import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


public class Settings
        extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String KEY_PREF_LAMBDA_MULTI = "pref_LambdaMultiplier";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (key.equals(KEY_PREF_LAMBDA_MULTI)) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            connectionPref.setSummary(sharedPreferences.getString(key, "147"));
        }
    }
}
