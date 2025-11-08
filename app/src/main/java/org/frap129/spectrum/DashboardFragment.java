package org.frap129.spectrum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private TextView cpuUsage, cpuFrequency, cpuTemp;
    private TextView ramUsage, ramPercentage;
    private TextView batteryLevel, batteryTemp, batteryHealth, batteryTech;
    private TextView thermalStatus, thermalCpu, thermalGpu, thermalBattery;
    private LinearProgressIndicator cpuProgress, ramProgress, batteryProgress;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable monitorRunnable;
    private BroadcastReceiver batteryReceiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupBatteryMonitoring();
        startSystemMonitoring();
    }

    private void initViews(View view) {
        // CPU Views
        cpuUsage = view.findViewById(R.id.cpuUsage);
        cpuFrequency = view.findViewById(R.id.cpuFrequency);
        cpuTemp = view.findViewById(R.id.cpuTemp);
        cpuProgress = view.findViewById(R.id.cpuProgress);

        // RAM Views
        ramUsage = view.findViewById(R.id.ramUsage);
        ramPercentage = view.findViewById(R.id.ramPercentage);
        ramProgress = view.findViewById(R.id.ramProgress);

        // Battery Views
        batteryLevel = view.findViewById(R.id.batteryLevel);
        batteryTemp = view.findViewById(R.id.batteryTemp);
        batteryHealth = view.findViewById(R.id.batteryHealth);
        batteryTech = view.findViewById(R.id.batteryTech);
        batteryProgress = view.findViewById(R.id.batteryProgress);

        // Thermal Views
        thermalStatus = view.findViewById(R.id.thermalStatus);
        thermalCpu = view.findViewById(R.id.thermalCpu);
        thermalGpu = view.findViewById(R.id.thermalGpu);
        thermalBattery = view.findViewById(R.id.thermalBattery);
    }

    private void setupBatteryMonitoring() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateBatteryInfo(intent);
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        requireActivity().registerReceiver(batteryReceiver, filter);
    }

    private void startSystemMonitoring() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                updateCpuInfo();
                updateRamInfo();
                updateThermalInfo();
                handler.postDelayed(this, 2000); // Update every 2 seconds
            }
        };
        handler.post(monitorRunnable);
    }

    private void updateCpuInfo() {
        // CPU Usage
        double cpuUsageValue = getCpuUsage();
        cpuUsage.setText(String.format(Locale.getDefault(), "%.1f%%", cpuUsageValue));
        cpuProgress.setProgress((int) cpuUsageValue);

        // CPU Frequency
        String freq = getCpuFrequency();
        cpuFrequency.setText(freq);

        // CPU Temperature
        String temp = getCpuTemperature();
        cpuTemp.setText(temp);
    }

    private void updateRamInfo() {
        android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
        android.app.ActivityManager activityManager = (android.app.ActivityManager) requireActivity().getSystemService(Context.ACTIVITY_SERVICE);
        
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
            
            long totalMem = memoryInfo.totalMem / (1024 * 1024);
            long availableMem = memoryInfo.availMem / (1024 * 1024);
            long usedMem = totalMem - availableMem;
            int usagePercent = (int) ((usedMem * 100) / totalMem);
            
            ramUsage.setText(String.format(Locale.getDefault(), "%d MB / %d MB", usedMem, totalMem));
            ramPercentage.setText(String.format(Locale.getDefault(), "%d%%", usagePercent));
            ramProgress.setProgress(usagePercent);
        }
    }

    private void updateBatteryInfo(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPercent = (int) (level * 100 / (float) scale);
        
        batteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPercent));
        batteryProgress.setProgress(batteryPercent);

        // Battery temperature (in tenths of degrees Celsius)
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (temp != -1) {
            float tempC = temp / 10.0f;
            batteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        }

        // Battery health
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        batteryHealth.setText(getBatteryHealthString(health));

        // Battery technology
        String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        if (technology != null) {
            batteryTech.setText(technology);
        }
    }

    private void updateThermalInfo() {
        // Update thermal information from system files
        String cpuTemp = readThermalFile("/sys/class/thermal/thermal_zone0/temp");
        String gpuTemp = readThermalFile("/sys/class/thermal/thermal_zone1/temp");
        String batteryTemp = readThermalFile("/sys/class/power_supply/battery/temp");
        
        thermalCpu.setText(formatTemperature(cpuTemp));
        thermalGpu.setText(formatTemperature(gpuTemp));
        thermalBattery.setText(formatTemperature(batteryTemp));
        
        updateThermalStatus();
    }

    private String getBatteryHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "Failure";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "Cold";
            default:
                return "Unknown";
        }
    }

    private double getCpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();
            
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long total = user + nice + system + idle;
                
                // Simple calculation - in real app you'd compare with previous values
                return ((double) (user + system) / total) * 100;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private String getCpuFrequency() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"));
            String freq = reader.readLine();
            reader.close();
            
            if (freq != null) {
                int freqMHz = Integer.parseInt(freq) / 1000;
                return String.format(Locale.getDefault(), "%d MHz", freqMHz);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "N/A";
    }

    private String getCpuTemperature() {
        String temp = readThermalFile("/sys/class/thermal/thermal_zone0/temp");
        return formatTemperature(temp);
    }

    private String readThermalFile(String path) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String temp = reader.readLine();
            reader.close();
            return temp;
        } catch (IOException e) {
            return "0";
        }
    }

    private String formatTemperature(String temp) {
        try {
            if (temp != null && !temp.isEmpty()) {
                int tempValue = Integer.parseInt(temp.trim());
                if (tempValue > 1000) { // If value is in millidegrees
                    tempValue = tempValue / 1000;
                }
                return String.format(Locale.getDefault(), "%d°C", tempValue);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return "N/A";
    }

    private void updateThermalStatus() {
        // Simple thermal status based on CPU temperature
        try {
            String cpuTempStr = readThermalFile("/sys/class/thermal/thermal_zone0/temp");
            int temp = Integer.parseInt(cpuTempStr.trim()) / 1000;
            
            if (temp < 50) {
                thermalStatus.setText("Status: Cool");
                thermalStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
            } else if (temp < 70) {
                thermalStatus.setText("Status: Normal");
                thermalStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
            } else if (temp < 85) {
                thermalStatus.setText("Status: Warm");
                thermalStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
            } else {
                thermalStatus.setText("Status: Hot!");
                thermalStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            }
        } catch (Exception e) {
            thermalStatus.setText("Status: Unknown");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
        }
        if (batteryReceiver != null) {
            requireActivity().unregisterReceiver(batteryReceiver);
        }
    }
}
