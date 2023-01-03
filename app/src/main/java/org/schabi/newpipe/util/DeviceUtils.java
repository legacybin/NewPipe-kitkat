package org.schabi.newpipe.util;

import static android.content.Context.INPUT_SERVICE;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;

import java.lang.reflect.Method;

public final class DeviceUtils {

    private static final String AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv";
	private static final boolean SAMSUNG = Build.MANUFACTURER.equals("samsung");
    private static Boolean isTV = null;
    private static Boolean isFireTV = null;

    /*
     * Devices that do not support media tunneling
     */
    // Formuler Z8 Pro, Z8, CC, Z Alpha, Z+ Neo
    private static final boolean HI3798MV200 = Build.VERSION.SDK_INT == 24
            && Build.DEVICE.equals("Hi3798MV200");
    // Zephir TS43UHD-2
    private static final boolean CVT_MT5886_EU_1G = Build.VERSION.SDK_INT == 24
            && Build.DEVICE.equals("cvt_mt5886_eu_1g");
    // Hilife TV
    private static final boolean REALTEKATV = Build.VERSION.SDK_INT == 25
            && Build.DEVICE.equals("RealtekATV");
    // Philips QM16XE
    private static final boolean QM16XE_U = Build.VERSION.SDK_INT == 23
            && Build.DEVICE.equals("QM16XE_U");

    private DeviceUtils() {
    }

    public static boolean isFireTv() {
        if (isFireTV != null) {
            return isFireTV;
        }

        isFireTV =
                App.getApp().getPackageManager().hasSystemFeature(AMAZON_FEATURE_FIRE_TV);
        return isFireTV;
    }

    public static boolean isTv(final Context context) {
        if (isTV != null) {
            return isTV;
        }

        final PackageManager pm = App.getApp().getPackageManager();

        // from doc: https://developer.android.com/training/tv/start/hardware.html#runtime-check
        boolean isTv = ContextCompat.getSystemService(context, UiModeManager.class)
                .getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION
                || isFireTv()
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION);

        // from https://stackoverflow.com/a/58932366
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final boolean isBatteryAbsent = context.getSystemService(BatteryManager.class)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) == 0;
            isTv = isTv || (isBatteryAbsent
                    && !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                    && pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                    && pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            isTv = isTv || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        }

        DeviceUtils.isTV = isTv;
        return DeviceUtils.isTV;
    }

    /**
     * Checks if the device is in desktop or DeX mode. This function should only
     * be invoked once on view load as it is using reflection for the DeX checks.
     * @param context the context to use for services and config.
     * @return true if the Android device is in desktop mode or using DeX.
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    public static boolean isDesktopMode(@NonNull final Context context) {
        // Adapted from https://stackoverflow.com/a/64615568
        // to check for all input devices that have an active cursor
        final InputManager im = (InputManager) context.getSystemService(INPUT_SERVICE);
        for (final int id : im.getInputDeviceIds()) {
            final InputDevice inputDevice = im.getInputDevice(id);
            if (inputDevice.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS)
                    || inputDevice.supportsSource(InputDevice.SOURCE_MOUSE)
                    || inputDevice.supportsSource(InputDevice.SOURCE_STYLUS)
                    || inputDevice.supportsSource(InputDevice.SOURCE_TOUCHPAD)
                    || inputDevice.supportsSource(InputDevice.SOURCE_TRACKBALL)) {
                return true;
            }
        }

        final UiModeManager uiModeManager =
                ContextCompat.getSystemService(context, UiModeManager.class);
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_DESK) {
            return true;
        }

        if (!SAMSUNG) {
            return false;
            // DeX is Samsung-specific, skip the checks below on non-Samsung devices
        }
        // DeX check for standalone and multi-window mode, from:
        // https://developer.samsung.com/samsung-dex/modify-optimizing.html
        try {
            final Configuration config = context.getResources().getConfiguration();
            final Class<?> configClass = config.getClass();
            final int semDesktopModeEnabledConst =
                    configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass);
            final int currentMode =
                    configClass.getField("semDesktopModeEnabled").getInt(config);
            if (semDesktopModeEnabledConst == currentMode) {
                return true;
            }
        } catch (final NoSuchFieldException | IllegalAccessException ignored) {
            // Device doesn't seem to support DeX
        }

        @SuppressLint("WrongConstant") final Object desktopModeManager = context
                .getApplicationContext()
                .getSystemService("desktopmode");

        if (desktopModeManager != null) {
            try {
                final Method getDesktopModeStateMethod = desktopModeManager.getClass()
                        .getDeclaredMethod("getDesktopModeState");
                final Object desktopModeState = getDesktopModeStateMethod
                        .invoke(desktopModeManager);
                final Class<?> desktopModeStateClass = desktopModeState.getClass();
                final Method getEnabledMethod = desktopModeStateClass
                        .getDeclaredMethod("getEnabled");
                final int enabledStatus = (int) getEnabledMethod.invoke(desktopModeState);
                if (enabledStatus == desktopModeStateClass
                        .getDeclaredField("ENABLED").getInt(desktopModeStateClass)) {
                    return true;
                }
            } catch (final Exception ignored) {
                // Device does not support DeX 3.0 or something went wrong when trying to determine
                // if it supports this feature
            }
        }

        return false;
    }

    public static boolean isTablet(@NonNull final Context context) {
        final String tabletModeSetting = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.tablet_mode_key), "");

        if (tabletModeSetting.equals(context.getString(R.string.tablet_mode_on_key))) {
            return true;
        } else if (tabletModeSetting.equals(context.getString(R.string.tablet_mode_off_key))) {
            return false;
        }

        // else automatically determine whether we are in a tablet or not
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static boolean isConfirmKey(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }

    public static int dpToPx(@Dimension(unit = Dimension.DP) final int dp,
                             @NonNull final Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics());
    }

    public static int spToPx(@Dimension(unit = Dimension.SP) final int sp,
                             @NonNull final Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                context.getResources().getDisplayMetrics());
    }

    /**
     * Some devices have broken tunneled video playback but claim to support it.
     * See https://github.com/TeamNewPipe/NewPipe/issues/5911
     * @return false if Kitkat (does not support tunneling) or affected device
     */
    public static boolean shouldSupportMediaTunneling() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && !HI3798MV200
                && !CVT_MT5886_EU_1G
                && !REALTEKATV
                && !QM16XE_U;
    }

    public static boolean isLandscape(final Context context) {
        return context.getResources().getDisplayMetrics().heightPixels < context.getResources()
                .getDisplayMetrics().widthPixels;
    }

    public static boolean isInMultiWindow(final AppCompatActivity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }

    public static boolean hasAnimationsAnimatorDurationEnabled(final Context context) {
        return Settings.System.getFloat(
                context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1F) != 0F;
    }
}