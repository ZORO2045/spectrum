package org.frap129.spectrum;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class GpuFragment extends Fragment {

    private Spinner gpuGovernorSpinner;
    private TextView currentGpuGovernorText, gpuMinFreqText, gpuMaxFreqText, currentGpuFreqText;
    private TextView gpuVendorText, gpuModelText, gpuTempText;
    private SeekBar gpuFreqSeekBar;
    private Button gpuPerformanceBtn, gpuPowerSaveBtn;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable gpuMonitorRunnable;

    private long gpuMinFreq = 0;
    private long gpuMaxFreq = 0;
    private long currentGpuFreq = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gpu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupGpuControls();
        startGpuMonitoring();
    }

    private void initViews(View view) {
        gpuGovernorSpinner = view.findViewById(R.id.gpuGovernorSpinner);
        currentGpuGovernorText = view.findViewById(R.id.currentGpuGovernorText);
        gpuMinFreqText = view.findViewById(R.id.gpuMinFreqText);
        gpuMaxFreqText = view.findViewById(R.id.gpuMaxFreqText);
        currentGpuFreqText = view.findViewById(R.id.currentGpuFreqText);
        gpuVendorText = view.findViewById(R.id.gpuVendorText);
        gpuModelText = view.findViewById(R.id.gpuModelText);
        gpuTempText = view.findViewById(R.id.gpuTempText);
        gpuFreqSeekBar = view.findViewById(R.id.gpuFreqSeekBar);
        gpuPerformanceBtn = view.findViewById(R.id.gpuPerformanceBtn);
        gpuPowerSaveBtn = view.findViewById(R.id.gpuPowerSaveBtn);
    }

    private void setupGpuControls() {
        setupGpuGovernorSpinner();
        setupGpuFrequencyControls();
        setupGpuPerformanceControls();
        loadGpuInfo();
    }

    private void setupGpuGovernorSpinner() {
        List<String> governors = getAvailableGpuGovernors();
        if (governors.isEmpty()) {
            governors.add("Not available");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_spinner_item, governors);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gpuGovernorSpinner.setAdapter(adapter);

        gpuGovernorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedGovernor = governors.get(position);
                if (!selectedGovernor.equals("Not available")) {
                    setGpuGovernor(selectedGovernor);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Set current governor
        String currentGov = getCurrentGpuGovernor();
        int position = governors.indexOf(currentGov);
        if (position >= 0) {
            gpuGovernorSpinner.setSelection(position);
        }
    }

    private void setupGpuFrequencyControls() {
        // Get available GPU frequencies
        List<Long> frequencies = getAvailableGpuFrequencies();
        if (!frequencies.isEmpty()) {
            gpuMinFreq = frequencies.get(frequencies.size() - 1); // Min is last
            gpuMaxFreq = frequencies.get(0); // Max is first
            
            gpuMinFreqText.setText(formatFrequency(gpuMinFreq));
            gpuMaxFreqText.setText(formatFrequency(gpuMaxFreq));
            
            gpuFreqSeekBar.setMax(frequencies.size() - 1);
            
            // Set current frequency
            currentGpuFreq = getCurrentGpuFrequency();
            int progress = getFrequencyProgress(currentGpuFreq, frequencies);
            gpuFreqSeekBar.setProgress(progress);
            
            gpuFreqSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        long selectedFreq = frequencies.get(progress);
                        setGpuMaxFrequency(selectedFreq);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void setupGpuPerformanceControls() {
        gpuPerformanceBtn.setOnClickListener(v -> setGpuPerformanceMode());
        gpuPowerSaveBtn.setOnClickListener(v -> setGpuPowerSaveMode());
    }

    private void loadGpuInfo() {
        // GPU Vendor and Model
        String vendor = getGpuVendor();
        String model = getGpuModel();
        
        gpuVendorText.setText(vendor);
        gpuModelText.setText(model);
    }

    private void startGpuMonitoring() {
        gpuMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                updateGpuInfo();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(gpuMonitorRunnable);
    }

    private void updateGpuInfo() {
        // Update current frequency
        currentGpuFreq = getCurrentGpuFrequency();
        currentGpuFreqText.setText("Current: " + formatFrequency(currentGpuFreq));

        // Update current governor
        String currentGov = getCurrentGpuGovernor();
        currentGpuGovernorText.setText("Current: " + currentGov);

        // Update temperature
        String temp = getGpuTemperature();
        gpuTempText.setText(temp);
    }

    // GPU Control Methods
    private List<String> getAvailableGpuGovernors() {
        List<String> governors = new ArrayList<>();
        try {
            // Try common GPU governor paths
            String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/devfreq/available_governors",
                "/sys/class/kgsl/kgsl-3d0/available_governors",
                "/sys/devices/platform/*.gpu/devfreq/available_governors"
            };
            
            for (String path : paths) {
                File govFile = new File(path);
                if (govFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(govFile));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null) {
                        String[] govs = line.split(" ");
                        for (String gov : govs) {
                            if (!gov.trim().isEmpty()) {
                                governors.add(gov.trim());
                            }
                        }
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        if (governors.isEmpty()) {
            governors.add("msm-adreno-tz");
            governors.add("performance");
            governors.add("powersave");
        }
        
        return governors;
    }

    private String getCurrentGpuGovernor() {
        try {
            // Try common GPU governor paths
            String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
                "/sys/class/kgsl/kgsl-3d0/governor",
                "/sys/devices/platform/*.gpu/devfreq/governor"
            };
            
            for (String path : paths) {
                File govFile = new File(path);
                if (govFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(govFile));
                    String gov = reader.readLine();
                    reader.close();
                    return gov != null ? gov : "Unknown";
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    private void setGpuGovernor(String governor) {
        // Try common GPU governor paths
        String[] paths = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/kgsl/kgsl-3d0/governor",
            "/sys/devices/platform/*.gpu/devfreq/governor"
        };
        
        for (String path : paths) {
            File govFile = new File(path);
            if (govFile.exists()) {
                Shell.SU.run("echo " + governor + " > " + path);
                break;
            }
        }
        Toast.makeText(requireContext(), "GPU Governor set to: " + governor, Toast.LENGTH_SHORT).show();
    }

    private List<Long> getAvailableGpuFrequencies() {
        List<Long> frequencies = new ArrayList<>();
        try {
            // Try common GPU frequency paths
            String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
                "/sys/class/kgsl/kgsl-3d0/available_frequencies",
                "/sys/devices/platform/*.gpu/devfreq/available_frequencies"
            };
            
            for (String path : paths) {
                File freqFile = new File(path);
                if (freqFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(freqFile));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null) {
                        String[] freqs = line.split(" ");
                        for (String freq : freqs) {
                            if (!freq.trim().isEmpty()) {
                                frequencies.add(Long.parseLong(freq.trim()));
                            }
                        }
                        // Sort descending
                        frequencies.sort((a, b) -> Long.compare(b, a));
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return frequencies;
    }

    private long getCurrentGpuFrequency() {
        try {
            // Try common GPU frequency paths
            String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/devices/platform/*.gpu/devfreq/cur_freq"
            };
            
            for (String path : paths) {
                File freqFile = new File(path);
                if (freqFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(freqFile));
                    String freq = reader.readLine();
                    reader.close();
                    return freq != null ? Long.parseLong(freq) : 0;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void setGpuMaxFrequency(long frequency) {
        // Try common GPU max frequency paths
        String[] paths = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/devices/platform/*.gpu/devfreq/max_freq"
        };
        
        for (String path : paths) {
            File maxFreqFile = new File(path);
            if (maxFreqFile.exists()) {
                Shell.SU.run("echo " + frequency + " > " + path);
                break;
            }
        }
    }

    private void setGpuPerformanceMode() {
        setGpuGovernor("performance");
        List<Long> frequencies = getAvailableGpuFrequencies();
        if (!frequencies.isEmpty()) {
            setGpuMaxFrequency(frequencies.get(0)); // Set to max frequency
        }
        Toast.makeText(requireContext(), "GPU Performance mode activated", Toast.LENGTH_SHORT).show();
    }

    private void setGpuPowerSaveMode() {
        setGpuGovernor("powersave");
        List<Long> frequencies = getAvailableGpuFrequencies();
        if (!frequencies.isEmpty()) {
            setGpuMaxFrequency(frequencies.get(frequencies.size() - 1)); // Set to min frequency
        }
        Toast.makeText(requireContext(), "GPU Power save mode activated", Toast.LENGTH_SHORT).show();
    }

    private String getGpuVendor() {
        // Try to detect GPU vendor
        if (new File("/sys/class/kgsl/kgsl-3d0").exists()) {
            return "Qualcomm Adreno";
        } else if (new File("/sys/devices/platform/mali.0").exists()) {
            return "ARM Mali";
        } else if (new File("/sys/devices/platform/pvrsrvkm.0").exists()) {
            return "PowerVR";
        }
        return "Unknown";
    }

    private String getGpuModel() {
        // This is simplified - in real app you'd parse /proc/gpuinfo or similar
        return "GPU";
    }

    private String getGpuTemperature() {
        try {
            // Try common GPU temperature paths
            String[] paths = {
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/class/kgsl/kgsl-3d0/temp",
                "/sys/devices/virtual/thermal/thermal_zone1/temp"
            };
            
            for (String path : paths) {
                File tempFile = new File(path);
                if (tempFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(tempFile));
                    String temp = reader.readLine();
                    reader.close();
                    if (temp != null) {
                        int tempValue = Integer.parseInt(temp.trim());
                        if (tempValue > 1000) {
                            tempValue = tempValue / 1000;
                        }
                        return tempValue + "°C";
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "N/A";
    }

    private int getFrequencyProgress(long frequency, List<Long> frequencies) {
        for (int i = 0; i < frequencies.size(); i++) {
            if (frequencies.get(i).equals(frequency)) {
                return i;
            }
        }
        return 0;
    }

    private String formatFrequency(long frequency) {
        if (frequency >= 1000000) {
            return (frequency / 1000000) + " MHz";
        } else if (frequency >= 1000) {
            return (frequency / 1000) + " KHz";
        } else {
            return frequency + " Hz";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && gpuMonitorRunnable != null) {
            handler.removeCallbacks(gpuMonitorRunnable);
        }
    }
}
