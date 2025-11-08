package org.frap129.spectrum;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.io.BufferedReader;
import java.io.FileReader;

public class DashboardFragment extends Fragment {

    private LinearProgressIndicator cpuProgress, memoryProgress, batteryProgress;
    private TextView tvCpuUsage, tvCpuCores, tvMemoryUsage, tvMemoryPercent, tvMemoryAvailable;
    private TextView tvBatteryLevel, tvBatteryStatus, tvTemperature, tvTemperatureStatus;
    private MaterialButton btnRefresh;
    
    private Handler handler = new Handler();
    private Runnable updateRunnable;

    private long previousIdleTime = 0;
    private long previousTotalTime = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        
        initViews(view);
        setupClickListeners();
        startMonitoring();
        
        return view;
    }

    private void initViews(View view) {
        cpuProgress = view.findViewById(R.id.cpuProgress);
        memoryProgress = view.findViewById(R.id.memoryProgress);
        batteryProgress = view.findViewById(R.id.batteryProgress);
        
        tvCpuUsage = view.findViewById(R.id.tvCpuUsage);
        tvCpuCores = view.findViewById(R.id.tvCpuCores);
        tvMemoryUsage = view.findViewById(R.id.tvMemoryUsage);
        tvMemoryPercent = view.findViewById(R.id.tvMemoryPercent);
        tvMemoryAvailable = view.findViewById(R.id.tvMemoryAvailable);
        tvBatteryLevel = view.findViewById(R.id.tvBatteryLevel);
        tvBatteryStatus = view.findViewById(R.id.tvBatteryStatus);
        tvTemperature = view.findViewById(R.id.tvTemperature);
        tvTemperatureStatus = view.findViewById(R.id.tvTemperatureStatus);
        
        btnRefresh = view.findViewById(R.id.btnRefresh);
    }

    private void setupClickListeners() {
        btnRefresh.setOnClickListener(v -> refreshData());
    }

    private void startMonitoring() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateSystemInfo();
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(updateRunnable);
    }

    private void refreshData() {
        btnRefresh.setEnabled(false);
        updateSystemInfo();
        
        new Handler().postDelayed(() -> {
            btnRefresh.setEnabled(true);
        }, 1000);
    }

    private void updateSystemInfo() {
        updateCpuInfo();
        updateMemoryInfo();
        updateBatteryInfo();
        updateTemperatureInfo();
    }

    private void updateCpuInfo() {
        try {
            int cores = Runtime.getRuntime().availableProcessors();
            tvCpuCores.setText(String.valueOf(cores));

            double cpuUsage = calculateRealCpuUsage();
            int cpuUsagePercent = (int) cpuUsage;
            
            cpuProgress.setProgress(cpuUsagePercent);
            tvCpuUsage.setText(cpuUsagePercent + "%");

        } catch (Exception e) {
            tvCpuUsage.setText("N/A");
            cpuProgress.setProgress(0);
        }
    }

    private double calculateRealCpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();

            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                
                long idle = Long.parseLong(parts[4]);
                long total = 0;
                for (int i = 1; i < parts.length; i++) {
                    total += Long.parseLong(parts[i]);
                }
                
                long idleTime = idle;
                long totalTime = total;
                
                double usage = 0;
                if (previousTotalTime != 0 && previousIdleTime != 0) {
                    long totalDiff = totalTime - previousTotalTime;
                    long idleDiff = idleTime - previousIdleTime;
                    
                    if (totalDiff > 0) {
                        usage = 100.0 * (totalDiff - idleDiff) / totalDiff;
                    }
                }
                
                previousIdleTime = idleTime;
                previousTotalTime = totalTime;
                
                return Math.min(100, Math.max(0, usage));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void updateMemoryInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            long totalMemory = 0;
            long freeMemory = 0;
            long availableMemory = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    totalMemory = parseMemInfoLine(line);
                } else if (line.startsWith("MemFree:")) {
                    freeMemory = parseMemInfoLine(line);
                } else if (line.startsWith("MemAvailable:")) {
                    availableMemory = parseMemInfoLine(line);
                }
            }
            reader.close();

            totalMemory = totalMemory / 1024;
            freeMemory = freeMemory / 1024;
            availableMemory = availableMemory / 1024;

            long usedMemory = totalMemory - availableMemory;
            int memoryUsagePercent = (int) ((usedMemory * 100) / totalMemory);

            memoryProgress.setProgress(memoryUsagePercent);
            tvMemoryUsage.setText(usedMemory + "MB/" + totalMemory + "MB");
            tvMemoryPercent.setText(memoryUsagePercent + "%");
            tvMemoryAvailable.setText(availableMemory + "MB");

        } catch (Exception e) {
            try {
                Runtime runtime = Runtime.getRuntime();
                long totalMemory = runtime.totalMemory() / (1024 * 1024);
                long freeMemory = runtime.freeMemory() / (1024 * 1024);
                long usedMemory = totalMemory - freeMemory;
                int memoryUsagePercent = (int) ((usedMemory * 100) / totalMemory);

                memoryProgress.setProgress(memoryUsagePercent);
                tvMemoryUsage.setText(usedMemory + "MB/" + totalMemory + "MB");
                tvMemoryPercent.setText(memoryUsagePercent + "%");
                tvMemoryAvailable.setText(freeMemory + "MB");
            } catch (Exception ex) {
                tvMemoryUsage.setText("N/A");
                memoryProgress.setProgress(0);
            }
        }
    }

    private long parseMemInfoLine(String line) {
        try {
            String[] parts = line.split("\\s+");
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private void updateBatteryInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/sys/class/power_supply/battery/capacity"));
            String capacityLine = reader.readLine();
            reader.close();

            BufferedReader statusReader = new BufferedReader(new FileReader("/sys/class/power_supply/battery/status"));
            String statusLine = statusReader.readLine();
            statusReader.close();

            int batteryLevel = 0;
            String batteryStatus = "Unknown";

            if (capacityLine != null) {
                batteryLevel = Integer.parseInt(capacityLine.trim());
            }

            if (statusLine != null) {
                batteryStatus = statusLine.trim();
                switch (batteryStatus.toLowerCase()) {
                    case "charging":
                        batteryStatus = "Charging";
                        break;
                    case "discharging":
                        batteryStatus = "Discharging";
                        break;
                    case "full":
                        batteryStatus = "Full";
                        break;
                    case "not charging":
                        batteryStatus = "Not Charging";
                        break;
                }
            }

            batteryProgress.setProgress(batteryLevel);
            tvBatteryLevel.setText(batteryLevel + "%");
            tvBatteryStatus.setText(batteryStatus);

        } catch (Exception e) {
            try {
                batteryProgress.setProgress(50);
                tvBatteryLevel.setText("50%");
                tvBatteryStatus.setText("Unknown");
            } catch (Exception ex) {
                tvBatteryLevel.setText("N/A");
                batteryProgress.setProgress(0);
            }
        }
    }

    private void updateTemperatureInfo() {
        try {
            double temperature = readCpuTemperature();
            tvTemperature.setText(String.format("%.1f°C", temperature));
            
            if (temperature < 40) {
                tvTemperatureStatus.setText("Cool");
                tvTemperature.setTextColor(getResources().getColor(R.color.colorBalance));
            } else if (temperature < 60) {
                tvTemperatureStatus.setText("Normal");
                tvTemperature.setTextColor(getResources().getColor(R.color.colorPerformance));
            } else {
                tvTemperatureStatus.setText("Hot");
                tvTemperature.setTextColor(getResources().getColor(R.color.colorGaming));
            }

        } catch (Exception e) {
            tvTemperature.setText("N/A");
            tvTemperatureStatus.setText("Unknown");
        }
    }

    private double readCpuTemperature() {
        try {
            String[] tempFiles = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/hwmon/hwmon0/temp1_input",
                "/sys/devices/virtual/thermal/thermal_zone0/temp",
                "/sys/class/power_supply/battery/temp"
            };
            
            for (String tempFile : tempFiles) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(tempFile));
                    String line = reader.readLine();
                    reader.close();
                    
                    if (line != null) {
                        double temp = Double.parseDouble(line.trim());
                        if (temp > 1000) {
                            temp = temp / 1000.0;
                        }
                        if (temp > 0 && temp < 100) {
                            return temp;
                        }
                    }
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 35.0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }
}
