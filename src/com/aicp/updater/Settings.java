package com.aicp.updater;

import android.app.job.JobInfo;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

public class Settings extends PreferenceActivity {
    private static final int DEFAULT_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;
    private static final String KEY_AUTO_UPDATE = "auto_update";
    private static final String KEY_NETWORK_TYPE = "network_type";
    private static final String KEY_UPDATE_STATUS = "update_status";
    private static final String PROPERTY_CHECK_TIME = "checktime";
    private static final String DEFAULT_CHECK_TIME = "86400000"; // One day
    static final String KEY_BATTERY_NOT_LOW = "battery_not_low";
    static final String KEY_IDLE_REBOOT = "idle_reboot";
    static final String KEY_WAITING_FOR_REBOOT = "waiting_for_reboot";

    private Preference mUpdateStatusPref;

    private long mUpdateProgress = -1;

    static SharedPreferences getPreferences(final Context context) {
        final Context deviceContext = context.createDeviceProtectedStorageContext();
        return PreferenceManager.getDefaultSharedPreferences(deviceContext);
    }

    static boolean getAutoUpdate(final Context context) {
        return getPreferences(context).getBoolean(KEY_AUTO_UPDATE, false);
    }

    static int getNetworkType(final Context context) {
        return getPreferences(context).getInt(KEY_NETWORK_TYPE, DEFAULT_NETWORK_TYPE);
    }


    static boolean getBatteryNotLow(final Context context) {
        return getPreferences(context).getBoolean(KEY_BATTERY_NOT_LOW, false);
    }

    static String getCheckTime(final Context context) {
        return getPreferences(context).getString(PROPERTY_CHECK_TIME, DEFAULT_CHECK_TIME);
    }


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!UserManager.get(this).isSystemUser()) {
            throw new SecurityException("system user only");
        }
        getPreferenceManager().setStorageDeviceProtected();
        addPreferencesFromResource(R.xml.settings);

        final Preference networkType = findPreference(KEY_NETWORK_TYPE);
        networkType.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            final int value = Integer.parseInt((String) newValue);
            getPreferences(this).edit().putInt(KEY_NETWORK_TYPE, value).apply();
            if (!getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                PeriodicJob.schedule(this);
            }
            return true;
        });

        final Preference batteryNotLow = findPreference(KEY_BATTERY_NOT_LOW);
        batteryNotLow.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            getPreferences(this).edit().putBoolean(KEY_BATTERY_NOT_LOW, (boolean) newValue).apply();
            if (!getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                PeriodicJob.schedule(this);
            }
            return true;
        });

        final Preference idleReboot = findPreference(KEY_IDLE_REBOOT);
        idleReboot.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            final boolean value = (Boolean) newValue;
            if (!value) {
                IdleReboot.cancel(this);
            }
            return true;
        });

        final Preference autoUpdate = findPreference(KEY_AUTO_UPDATE);
        autoUpdate.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            final boolean value = (Boolean) newValue;
            if (!value) {
                getPreferences(this).edit().putBoolean(KEY_AUTO_UPDATE, (boolean) newValue).apply();
                if (!getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                    // This also cancels jobs if needed
                    PeriodicJob.schedule(this);
                    // If download already in progress, stop it in case it was started automatically
                    Service.requestStop();
                }
            }
            return true;
        });

        mUpdateStatusPref = findPreference(KEY_UPDATE_STATUS);
        mUpdateStatusPref.setOnPreferenceClickListener((final Preference preference) -> {
            if (mUpdateProgress == -1) {
                Service.allowStart();
                sendBroadcast(new Intent(this, TriggerUpdateReceiver.class));
            } else {
                Service.requestStop();
            }
            return true;
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mUpdateProgress = intent.getLongExtra(Service.EXTRA_PROGRESS, -1);
                updateUpdateStatus();
            }
        }, new IntentFilter(Service.INTENT_UPDATE));
    }

    @Override
    public void onResume() {
        super.onResume();
        final ListPreference networkType = (ListPreference) findPreference(KEY_NETWORK_TYPE);
        networkType.setValue(Integer.toString(getNetworkType(this)));
        updateUpdateStatus();
    }

    private void updateUpdateStatus() {
        if (mUpdateProgress == -1) {
            mUpdateStatusPref.setTitle(R.string.last_update_title);
            mUpdateStatusPref.setSummary("TODO WHEN LAST UPDATE");//TODO when last updated
        } else {
            mUpdateStatusPref.setTitle(R.string.update_status_downloading_title);
            mUpdateStatusPref.setSummary(getString(R.string.udpate_status_downloading_summary,
                    mUpdateProgress/1_000_000));
        }
    }
}
