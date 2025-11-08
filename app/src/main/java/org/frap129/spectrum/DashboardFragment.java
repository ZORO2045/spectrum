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
import java.io.IOException;

public class DashboardFragment extends Fragment {

    private LinearProgressIndicator cpuProgress, memoryProgress, batteryProgress;
    private TextView tvCpuUsage, tvCpuCores, tvMemoryUsage, tvMemoryPercent, tvMemoryAvailable;
    private TextView tvBatteryLevel, tvBatteryStatus, tvTemperature, tvTemperatureStatus;
    private MaterialButton btnRefresh;
    
    private Handler handler = new Handler();
    private Runnable updateRunnable;

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
                handler.postDelayed(this, 2000); // Update every 2 seconds
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
            // Get CPU cores count
            int cores = Runtime.getRuntime().availableProcessors();
            tvCpuCores.setText(String.valueOf(cores));

            // Get CPU usage (simplified calculation)
            double cpuUsage = calculateCpuUsage();
            int cpuUsagePercent = (int) cpuUsage;
            
            cpuProgress.setProgress(cpuUsagePercent);
            tvCpuUsage.setText(cpuUsagePercent + "%");

        } catch (Exception e) {
            tvCpuUsage.setText("N/A");
            cpuProgress.setProgress(0);
        }
    }

    private double calculateCpuUsage() {
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
                
                // Simple calculation (in real app you'd compare with previous values)
                double usage = 100.0 - (idle * 100.0 / total);
                return Math.min(100, Math.max(0, usage));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 25.0; // Default value for demo
    }

    private void updateMemoryInfo() {
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

        } catch (Exception e) {
            tvMemoryUsage.setText("N/A");
            memoryProgress.setProgress(0);
        }
    }

    private void updateBatteryInfo() {
        try {
            // Simulate battery data (in real app, use BatteryManager)
            int batteryLevel = 75; // This would come from system
            String batteryStatus = "Charging";
            
            batteryProgress.setProgress(batteryLevel);
            tvBatteryLevel.setText(batteryLevel + "%");
            tvBatteryStatus.setText(batteryStatus);

        } catch (Exception e) {
            tvBatteryLevel.setText("N/A");
            batteryProgress.setProgress(0);
        }
    }

    private void updateTemperatureInfo() {
        try {
            // Read CPU temperature
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
            // Try to read from common temperature files
            String[] tempFiles = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/hwmon/hwmon0/temp1_input",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
            };
            
            for (String tempFile : tempFiles) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(tempFile));
                    String line = reader.readLine();
                    reader.close();
                    
                    if (line != null) {
                        double temp = Double.parseDouble(line.trim()) / 1000.0;
                        if (temp > 0 && temp < 100) {
                            return temp;
                        }
                    }
                } catch (Exception e) {
                    // Continue to next file
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 45.0; // Default temperature
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }
    }
