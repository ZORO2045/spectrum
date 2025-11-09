package org.frap129.spectrum;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

class Utils {
    private static final String TAG = "SpectrumUtils";

    private static String kpmSupport = "/proc/kpm_supported";
    public static String kpmPath = "/sys/module/profiles_manager/parameters/kpm_profile";
    private static String kpmDisabledProfilesPath = "/proc/kpm_disabled_profiles";
    private static String kpmDisabledProfiles = null;
    private static String kpmNotTuned = "/proc/kpm_not_tuned";
    public static String kpmFinal = "/proc/kpm_final";
    public static String profileProp = "persist.spectrum.profile";
    public static String kernelProp = "persist.spectrum.kernel";
    public static String kpmPropPath = "/proc/kpm_name";
    public static Boolean KPM = false;
    public static String notTunedGov = "";
    public static String finalGov;
    public static String cpuScalingGovernorPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";

    // Method to check if kernel supports
    public static boolean checkSupport(final Context context) {
        try {
            List<String> shResult;
            String supportProp = "spectrum.support";
            
            // Safe shell command execution
            shResult = safeShellRun(String.format("getprop %s", supportProp), false);
            
            if (listToString(shResult).isEmpty()) {
                shResult = safeShellRun(String.format("cat %s", kpmSupport), true);
                KPM = true;

                List<String> anyDisabledProfile = safeShellRun(String.format("cat %s", kpmDisabledProfilesPath), true);
                if (anyDisabledProfile != null && !listToString(anyDisabledProfile).isEmpty()) {
                    kpmDisabledProfiles = listToString(anyDisabledProfile);
                }
            } else {
                KPM = false;
            }
            
            String support = listToString(shResult);
            return !support.isEmpty();
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking Spectrum support", e);
            return false;
        }
    }

    // Safe shell command execution with null checks
    private static List<String> safeShellRun(String command, boolean useRoot) {
        try {
            if (useRoot && !Shell.SU.available()) {
                return null;
            }
            List<String> result = useRoot ? Shell.SU.run(command) : Shell.SH.run(command);
            return result != null ? result : java.util.Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "Error executing shell command: " + command, e);
            return java.util.Collections.emptyList();
        }
    }

    // Method to check if the device is rooted
    public static boolean checkSU() {
        try {
            return Shell.SU.available();
        } catch (Exception e) {
            Log.e(TAG, "Error checking root access", e);
            return false;
        }
    }

    // Method that converts List<String> to String with null safety
    public static String listToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder Builder = new StringBuilder();
        for (String out : list) {
            if (out != null) {
                Builder.append(out);
            }
        }
        return Builder.toString();
    }

    // Method that interprets a profile and sets it
    public static void setProfile(int profile) {
        try {
            int numProfiles = 3;
            if (profile > numProfiles || profile < 0) {
                setProp(0);
            } else {
                setProp(profile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting profile", e);
        }
    }

    // Method that sets system property
    private static void setProp(final int profile) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (KPM) {
                        safeShellRun(String.format("echo %s > %s", profile, kpmPath), true);
                    } else {
                        safeShellRun(String.format("setprop %s %s", profileProp, profile), true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting system property", e);
                }
            }
        }).start();
    }

    public static String disabledProfiles() {
        try {
            String disabledProfilesProp = "spectrum.disabledprofiles";
            if (KPM && kpmDisabledProfiles != null) {
                return kpmDisabledProfiles;
            }
            List<String> result = safeShellRun(String.format("getprop %s", disabledProfilesProp), false);
            return listToString(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting disabled profiles", e);
            return "";
        }
    }

    private static String readString(File file, String profileName) {
        String returnValue = null;
        BufferedReader reader = null;
        try {
            if (file == null || !file.exists()) {
                return null;
            }
            reader = new BufferedReader(new FileReader(file), 512);
            returnValue = reader.readLine();
            while (returnValue != null && !returnValue.contains(profileName)) {
                returnValue = reader.readLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + file, e);
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing reader", e);
            }
        }
        return returnValue;
    }

    public static String getCustomDesc(String profileName) {
        try {
            // Use scoped storage compatible path for Android 11+
            File customDescFile = new File(Environment.getExternalStorageDirectory() + File.separator + ".spectrum_descriptions");
            String retVal = readString(customDescFile, profileName);
            if (retVal != null) {
                String[] parts = retVal.split(":");
                return parts.length > 1 ? parts[1] : "fail";
            } else {
                return "fail";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting custom description", e);
            return "fail";
        }
    }

    public static boolean supportsCustomDesc() {
        try {
            File customDescFile = new File(Environment.getExternalStorageDirectory() + File.separator + ".spectrum_descriptions");
            return customDescFile.exists();
        } catch (Exception e) {
            Log.e(TAG, "Error checking custom description support", e);
            return false;
        }
    }

    // New methods for dialogs
    public static void showNoSupportDialog(Context context) {
        try {
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.no_spectrum_support_dialog_title))
                    .setMessage(context.getString(R.string.no_spectrum_support_dialog_message))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            if (context instanceof Activity) {
                                ((Activity) context).finish();
                            }
                        }
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing no support dialog", e);
        }
    }

    public static void showNoRootDialog(Context context) {
        try {
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.no_root_detected_dialog_title))
                    .setMessage(context.getString(R.string.no_root_detected_dialog_message))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            if (context instanceof Activity) {
                                ((Activity) context).finish();
                            }
                        }
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing no root dialog", e);
        }
    }

    // ==================== KILL CAMERA METHODS ====================

    // Check if camera services are running
    public static boolean isCameraServiceRunning(Context context) {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return false;

            List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
            if (services == null) return false;

            // Common camera-related service names
            String[] cameraKeywords = {
                    "camera",
                    "mediaserver",
                    "cameraserver",
                    "camera.provider",
                    "com.android.camera",
                    "com.google.android.GoogleCamera",
                    "com.sec.android.app.camera"
            };

            for (ActivityManager.RunningServiceInfo service : services) {
                if (service.service == null) continue;
                
                String serviceName = service.service.getClassName().toLowerCase();
                String packageName = service.service.getPackageName().toLowerCase();

                for (String keyword : cameraKeywords) {
                    if (serviceName.contains(keyword) || packageName.contains(keyword)) {
                        return true;
                    }
                }
            }

            // Also check running processes
            List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.processName == null) continue;
                    
                    String processName = process.processName.toLowerCase();
                    for (String keyword : cameraKeywords) {
                        if (processName.contains(keyword)) {
                            return true;
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking camera services", e);
        }
        return false;
    }

    // Kill camera services and processes
    public static boolean killCameraServices(Context context) {
        try {
            // Method 1: Kill camera processes using pkill
            ShellUtils.CommandResult result1 = ShellUtils.execCommand("pkill -f camera", true);

            // Method 2: Stop camera-related packages
            String[] cameraPackages = {
                    "com.android.camera",
                    "com.google.android.GoogleCamera",
                    "com.sec.android.app.camera",
                    "com.oneplus.camera",
                    "com.miui.camera",
                    "com.huawei.camera",
                    "com.coloros.camera",
                    "com.realme.camera",
                    "com.vivo.camera"
            };

            for (String pkg : cameraPackages) {
                ShellUtils.execCommand("am force-stop " + pkg, true);
            }

            // Method 3: Kill camera system services
            ShellUtils.execCommand("killall cameraserver", true);
            ShellUtils.execCommand("killall camera-provider", true);
            ShellUtils.execCommand("killall mediaserver", true);

            // Method 4: Use setprop to disable camera temporarily
            ShellUtils.execCommand("setprop camera.disable_zsl_mode 1", true);
            ShellUtils.execCommand("setprop camera.hal1.packagelist ''", true);

            // Method 5: Clear camera cache
            ShellUtils.execCommand("pm clear com.android.camera", true);

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error killing camera services", e);
            return false;
        }
    }

    // Restart camera services
    public static boolean restartCameraServices(Context context) {
        try {
            // Restart camera HAL
            ShellUtils.execCommand("start cameraserver", true);
            ShellUtils.execCommand("start camera-provider", true);

            // Reset camera properties
            ShellUtils.execCommand("setprop camera.disable_zsl_mode 0", true);

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error restarting camera services", e);
            return false;
        }
    }

    // Check camera hardware status
    public static boolean isCameraHardwareAvailable(Context context) {
        try {
            // Check if camera hardware is present
            List<String> result = safeShellRun("ls /dev/ | grep camera", true);
            return !listToString(result).isEmpty();

        } catch (Exception e) {
            Log.e(TAG, "Error checking camera hardware", e);
            return false;
        }
    }

    // Get camera processes info
    public static String getCameraProcessesInfo() {
        try {
            // Get detailed info about camera processes
            List<String> psResult = safeShellRun("ps -A | grep -i camera", true);
            return listToString(psResult);

        } catch (Exception e) {
            Log.e(TAG, "Error getting camera processes info", e);
            return "Unable to get camera processes info";
        }
    }
}

// ShellUtils class for executing shell commands
class ShellUtils {

    public static class CommandResult {
        public String result;
        public String error;
        public int exitValue;

        public CommandResult(String result, String error, int exitValue) {
            this.result = result;
            this.error = error;
            this.exitValue = exitValue;
        }
    }

    public static CommandResult execCommand(String command, boolean isRoot) {
        try {
            List<String> result;
            if (isRoot) {
                if (!Shell.SU.available()) {
                    return new CommandResult("", "Root not available", -1);
                }
                result = Shell.SU.run(command);
            } else {
                result = Shell.SH.run(command);
            }

            String output = Utils.listToString(result);
            return new CommandResult(output, "", 0);

        } catch (Exception e) {
            Log.e("ShellUtils", "Error executing command: " + command, e);
            return new CommandResult("", e.getMessage(), -1);
        }
    }
    }
