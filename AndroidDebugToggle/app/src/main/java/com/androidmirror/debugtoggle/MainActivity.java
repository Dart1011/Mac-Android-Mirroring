package com.androidmirror.debugtoggle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String ACTION_TOGGLE = "com.androidmirror.debugtoggle.action.TOGGLE";
    private TextView statusView;
    private Button toggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ACTION_TOGGLE.equals(getIntent().getAction())) {
            showToggleConfirmation(true);
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(32);
        layout.setPadding(padding, padding, padding, padding);

        statusView = new TextView(this);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextSize(18);
        layout.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        toggleButton = new Button(this);
        toggleButton.setOnClickListener(view -> showToggleConfirmation(false));
        layout.addView(toggleButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button pinButton = new Button(this);
        pinButton.setText(R.string.add_shortcut);
        pinButton.setOnClickListener(view -> requestPinnedShortcut());
        layout.addView(pinButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button developerOptionsButton = new Button(this);
        developerOptionsButton.setText(R.string.open_developer_options);
        developerOptionsButton.setOnClickListener(view -> openDeveloperOptions());
        layout.addView(developerOptionsButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(layout);
        updateStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            showToggleConfirmation(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null) {
            updateStatus();
        }
    }

    private void showToggleConfirmation(boolean closeAfterToggle) {
        boolean enable = !isDebuggingEnabled();
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(enable ? R.string.confirm_enable : R.string.confirm_disable)
                .setPositiveButton(enable ? R.string.turn_on : R.string.turn_off,
                        (dialog, which) -> {
                            toggleDebugging();
                            if (closeAfterToggle) {
                                finish();
                            }
                        })
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> {
                            if (closeAfterToggle) {
                                finish();
                            }
                        })
                .setOnCancelListener(dialog -> {
                    if (closeAfterToggle) {
                        finish();
                    }
                })
                .show();
    }

    private boolean isDebuggingEnabled() {
        ContentResolver resolver = getContentResolver();
        boolean usb = Settings.Global.getInt(resolver, "adb_enabled", 0) == 1;
        boolean wireless = Settings.Global.getInt(resolver, "adb_wifi_enabled", 0) == 1;
        return usb || wireless;
    }

    private void toggleDebugging() {
        boolean enable = !isDebuggingEnabled();
        ContentResolver resolver = getContentResolver();
        try {
            Settings.Global.putInt(resolver, "development_settings_enabled", enable ? 1 : 0);
            Settings.Global.putInt(resolver, "adb_enabled", enable ? 1 : 0);
            Settings.Global.putInt(resolver, "adb_wifi_enabled", enable ? 1 : 0);
            Toast.makeText(this, enable ? R.string.enabled_toast : R.string.disabled_toast,
                    Toast.LENGTH_LONG).show();
        } catch (SecurityException error) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void updateStatus() {
        if (statusView == null || toggleButton == null) {
            return;
        }
        boolean enabled = isDebuggingEnabled();
        statusView.setText(enabled ? R.string.status_on : R.string.status_off);
        toggleButton.setText(enabled ? R.string.turn_off : R.string.turn_on);
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show();
        }
    }

    private void requestPinnedShortcut() {
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Toast.makeText(this, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show();
            return;
        }

        Intent toggleIntent = new Intent(this, MainActivity.class).setAction(ACTION_TOGGLE);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "mirror_debug_toggle")
                .setShortLabel(getString(R.string.app_name))
                .setLongLabel(getString(R.string.app_name))
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_mirror_debug))
                .setIntent(toggleIntent)
                .build();
        Intent callbackIntent = new Intent(this, MainActivity.class);
        android.app.PendingIntent callback = android.app.PendingIntent.getActivity(
                this,
                0,
                callbackIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        | android.app.PendingIntent.FLAG_IMMUTABLE);
        manager.requestPinShortcut(shortcut, callback.getIntentSender());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
