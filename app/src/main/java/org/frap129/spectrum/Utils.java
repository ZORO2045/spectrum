package org.frap129.spectrum;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

class Utils {

    private static String kpmSupport = "/proc/kpm_supported";

    public static String kpmPath = "/sys/module/profiles_manager/parameters/kpm_profile";

    private static String kpmDisabledProfilesPath = "/proc/kpm_disabled_profiles";

    private static String kpmDisabledProfiles = null;

    private static String kpmNotTuned = "/proc/kpm_not_tuned";

    public static String  kpmFinal = "/proc/kpm_final";

    public static String profileProp = "persist.spectrum.profile";

    public static String kernelProp = "persist.spectrum.kernel";

    public static String kpmPropPath = "/proc/kpm_name";

    public static Boolean KPM;

    public static String notTunedGov = listToString(Shell.SU.run(String.format("cat %s", kpmNotTuned)));

    public static String finalGov;

    public static String cpuScalingGovernorPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";

    // Method to check if kernel supports
    public static boolean checkSupport(final Context context) {
        List<String> shResult;
        String supportProp = "spectrum.support";
        shResult = Shell.SH.run(String.format("getprop %s", supportProp));
        if(listToString(shResult).isEmpty()){
            shResult = Shell.SU.run(String.format("cat %s", kpmSupport));
            KPM = true;

            List<String> anyDisabledProfile;
            anyDisabledProfile = Shell.SU.run(String.format("cat %s", kpmDisabledProfilesPath));
            if(!listToString(anyDisabledProfile).isEmpty()){
                kpmDisabledProfiles = listToString(anyDisabledProfile);
            }

        } else {
            KPM = false;
        }
        String support = listToString(shResult);

        return !support.isEmpty();
    }

    // Method to check if the device is rooted
    public static boolean checkSU() {
        return Shell.SU.available();
    }

    // Method that converts List<String> to String
    public static String listToString(List<String> list) {
        StringBuilder Builder = new StringBuilder();
        for(String out : list){
            Builder.append(out);
        }
        return Builder.toString();
    }

    // Method that interprets a profile and sets it
    public static void setProfile(int profile) {
        int numProfiles = 3;
        if (profile > numProfiles || profile < 0) {
            setProp(0);
        } else {
            setProp(profile);
        }
    }

    // Method that sets system property
    private static void setProp(final int profile) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(KPM) {
                    Shell.SU.run(String.format("echo %s > %s", profile, kpmPath));
                } else {
                    Shell.SU.run(String.format("setprop %s %s", profileProp, profile));
                }
            }
        }).start();
    }

    public static String disabledProfiles(){
        String disabledProfilesProp = "spectrum.disabledprofiles";
        if(KPM && kpmDisabledProfiles != null){
            return kpmDisabledProfiles;
        }
        return listToString(Shell.SH.run(String.format("getprop %s", disabledProfilesProp)));
    }

    private static String readString(File file, String profileName) {
        String returnValue = null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file), 512);
            returnValue = reader.readLine();
            while ( returnValue != null && !returnValue.contains(profileName)){
                returnValue = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                // Ignored, not much we can do anyway
            }
        }
        return returnValue;
    }

    public static String getCustomDesc(String profileName) {
        File customDescFile = new File(Environment.getExternalStorageDirectory() + File.separator +".spectrum_descriptions");
        String retVal = readString(customDescFile, profileName);
        if (retVal != null) {
            return retVal.split(":")[1];
        } else {
            return "fail";
        }
    }

    public static boolean supportsCustomDesc(){
        return new File(Environment.getExternalStorageDirectory() + File.separator +".spectrum_descriptions").exists();
    }

    // New methods for dialogs
    public static void showNoSupportDialog(Context context) {
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
    }

    public static void showNoRootDialog(Context context) {
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
    }

    // ==================== KILL CAMERA METHODS ====================

    // Check if camera services are running
    public static boolean isCameraServiceRunning(Context context) {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
            
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
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                String processName = process.processName.toLowerCase();
                for (String keyword : cameraKeywords) {
                    if (processName.contains(keyword)) {
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
            return false;
        }
    }

    // Check camera hardware status
    public static boolean isCameraHardwareAvailable(Context context) {
        try {
            // Check if camera hardware is present
            List<String> result = Shell.SU.run("ls /dev/ | grep camera");
            return !listToString(result).isEmpty();
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get camera processes info
    public static String getCameraProcessesInfo() {
        try {
            // Get detailed info about camera processes
            List<String> psResult = Shell.SU.run("ps -A | grep -i camera");
            return listToString(psResult);
            
        } catch (Exception e) {
            e.printStackTrace();
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
                result = Shell.SU.run(command);
            } else {
                result = Shell.SH.run(command);
            }
            
            String output = Utils.listToString(result);
            return new CommandResult(output, "", 0);
            
        } catch (Exception e) {
            return new CommandResult("", e.getMessage(), -1);
        }
    }
}
