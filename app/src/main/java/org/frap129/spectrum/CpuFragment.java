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

public class CpuFragment extends Fragment {

    private Spinner cpuGovernorSpinner;
    private TextView currentGovernorText, minFreqText, maxFreqText, currentFreqText;
    private TextView activeCoresText, cpuArchText, cpuCoresText, cpuTempText;
    private SeekBar cpuFreqSeekBar;
    private Button enableAllCoresBtn, disableAllCoresBtn;
    private Button applyFreqBtn, resetFreqBtn;
    private View boostCard;
    private Button boostToggleBtn;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable cpuMonitorRunnable;

    private long minFreq = 0;
    private long maxFreq = 0;
    private long currentFreq = 0;
    private List<Long> availableFrequencies = new ArrayList<>();
    private boolean isBoostEnabled = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cpu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupCpuControls();
        startCpuMonitoring();
    }

    private void initViews(View view) {
        cpuGovernorSpinner = view.findViewById(R.id.cpuGovernorSpinner);
        currentGovernorText = view.findViewById(R.id.currentGovernorText);
        minFreqText = view.findViewById(R.id.minFreqText);
        maxFreqText = view.findViewById(R.id.maxFreqText);
        currentFreqText = view.findViewById(R.id.currentFreqText);
        activeCoresText = view.findViewById(R.id.activeCoresText);
        cpuArchText = view.findViewById(R.id.cpuArchText);
        cpuCoresText = view.findViewById(R.id.cpuCoresText);
        cpuTempText = view.findViewById(R.id.cpuTempText);
        cpuFreqSeekBar = view.findViewById(R.id.cpuFreqSeekBar);
        enableAllCoresBtn = view.findViewById(R.id.enableAllCoresBtn);
        disableAllCoresBtn = view.findViewById(R.id.disableAllCoresBtn);
        applyFreqBtn = view.findViewById(R.id.applyFreqBtn);
        resetFreqBtn = view.findViewById(R.id.resetFreqBtn);
        boostCard = view.findViewById(R.id.boostCard);
        boostToggleBtn = view.findViewById(R.id.boostToggleBtn);
    }

    private void setupCpuControls() {
        setupGovernorSpinner();
        setupFrequencyControls();
        setupCoreControls();
        checkAndSetupCpuBoost();
        loadCpuInfo();
    }

    private void setupGovernorSpinner() {
        List<String> governors = getAvailableGovernors();
        if (governors.isEmpty()) {
            governors.add("Not available");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_spinner_item, governors);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cpuGovernorSpinner.setAdapter(adapter);

        cpuGovernorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedGovernor = governors.get(position);
                if (!selectedGovernor.equals("Not available")) {
                    setCpuGovernor(selectedGovernor);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String currentGov = getCurrentGovernor();
        int position = governors.indexOf(currentGov);
        if (position >= 0) {
            cpuGovernorSpinner.setSelection(position);
        }
    }

    private void setupFrequencyControls() {
        availableFrequencies = getAvailableFrequencies();
        if (!availableFrequencies.isEmpty()) {
            minFreq = availableFrequencies.get(availableFrequencies.size() - 1);
            maxFreq = availableFrequencies.get(0);
            
            minFreqText.setText(formatFrequency(minFreq));
            maxFreqText.setText(formatFrequency(maxFreq));
            
            cpuFreqSeekBar.setMax(availableFrequencies.size() - 1);
            
            currentFreq = getCurrentFrequency();
            int progress = getFrequencyProgress(currentFreq, availableFrequencies);
            cpuFreqSeekBar.setProgress(progress);
            
            cpuFreqSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        long selectedFreq = availableFrequencies.get(progress);
                        currentFreqText.setText("Selected: " + formatFrequency(selectedFreq));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        applyFreqBtn.setOnClickListener(v -> {
            int progress = cpuFreqSeekBar.getProgress();
            if (progress >= 0 && progress < availableFrequencies.size()) {
                long selectedFreq = availableFrequencies.get(progress);
                setCpuFrequency(selectedFreq);
                Toast.makeText(requireContext(), "Frequency set to: " + formatFrequency(selectedFreq), Toast.LENGTH_SHORT).show();
            }
        });

        resetFreqBtn.setOnClickListener(v -> {
            resetCpuFrequency();
            Toast.makeText(requireContext(), "Frequency reset to default", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupCoreControls() {
        enableAllCoresBtn.setOnClickListener(v -> enableAllCores());
        disableAllCoresBtn.setOnClickListener(v -> disableAllCores());
    }

    private void checkAndSetupCpuBoost() {
        File boostFile = new File("/sys/devices/system/cpu/cpufreq/boost");
        if (boostFile.exists()) {
            boostCard.setVisibility(View.VISIBLE);
            updateBoostState();
            
            boostToggleBtn.setOnClickListener(v -> toggleCpuBoost());
        }
    }

    private void updateBoostState() {
        try {
            File boostFile = new File("/sys/devices/system/cpu/cpufreq/boost");
            BufferedReader reader = new BufferedReader(new FileReader(boostFile));
            String current = reader.readLine();
            reader.close();
            
            isBoostEnabled = "1".equals(current);
            boostToggleBtn.setText(isBoostEnabled ? "Disable Boost" : "Enable Boost");
            boostToggleBtn.setBackgroundTintList(getResources().getColorStateList(
                isBoostEnabled ? R.color.colorAccent : android.R.color.darker_gray));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void toggleCpuBoost() {
        String newValue = isBoostEnabled ? "0" : "1";
        Shell.SU.run("echo " + newValue + " > /sys/devices/system/cpu/cpufreq/boost");
        
        isBoostEnabled = !isBoostEnabled;
        updateBoostState();
        
        Toast.makeText(requireContext(), "CPU Boost " + (isBoostEnabled ? "enabled" : "disabled"), 
                      Toast.LENGTH_SHORT).show();
    }

    private void loadCpuInfo() {
        String arch = getCpuArchitecture();
        cpuArchText.setText(arch);

        int totalCores = getTotalCores();
        cpuCoresText.setText(String.valueOf(totalCores));

        updateActiveCores();
    }

    private void startCpuMonitoring() {
        cpuMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                updateCpuInfo();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(cpuMonitorRunnable);
    }

    private void updateCpuInfo() {
        currentFreq = getCurrentFrequency();
        currentFreqText.setText("Current: " + formatFrequency(currentFreq));

        String currentGov = getCurrentGovernor();
        currentGovernorText.setText("Current: " + currentGov);

        updateActiveCores();

        String temp = getCpuTemperature();
        cpuTempText.setText(temp);
    }

    private List<String> getAvailableGovernors() {
        List<String> governors = new ArrayList<>();
        try {
            File govFile = new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
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
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return governors;
    }

    private String getCurrentGovernor() {
        try {
            File govFile = new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if (govFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(govFile));
                String gov = reader.readLine();
                reader.close();
                return gov != null ? gov : "Unknown";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    private void setCpuGovernor(String governor) {
        Shell.SU.run("echo " + governor + " > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
        Toast.makeText(requireContext(), "Governor set to: " + governor, Toast.LENGTH_SHORT).show();
    }

    private List<Long> getAvailableFrequencies() {
        List<Long> frequencies = new ArrayList<>();
        try {
            File freqFile = new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies");
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
                    frequencies.sort((a, b) -> Long.compare(b, a));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return frequencies;
    }

    private long getCurrentFrequency() {
        try {
            File freqFile = new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            if (freqFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(freqFile));
                String freq = reader.readLine();
                reader.close();
                return freq != null ? Long.parseLong(freq) : 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void setCpuFrequency(long frequency) {
        List<String> commands = new ArrayList<>();
        commands.add("echo " + frequency + " > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
        commands.add("echo " + frequency + " > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq");
        
        int totalCores = getTotalCores();
        for (int i = 0; i < totalCores; i++) {
            commands.add("echo " + frequency + " > /sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_max_freq");
        }
        
        Shell.SU.run(commands);
    }

    private void resetCpuFrequency() {
        if (!availableFrequencies.isEmpty()) {
            long defaultMaxFreq = availableFrequencies.get(0);
            long defaultMinFreq = availableFrequencies.get(availableFrequencies.size() - 1);
            
            List<String> commands = new ArrayList<>();
            commands.add("echo " + defaultMaxFreq + " > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
            commands.add("echo " + defaultMinFreq + " > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq");
            
            Shell.SU.run(commands);
            
            int progress = getFrequencyProgress(defaultMaxFreq, availableFrequencies);
            cpuFreqSeekBar.setProgress(progress);
        }
    }

    private int getFrequencyProgress(long frequency, List<Long> frequencies) {
        for (int i = 0; i < frequencies.size(); i++) {
            if (frequencies.get(i).equals(frequency)) {
                return i;
            }
        }
        return 0;
    }

    private String getCpuArchitecture() {
        return System.getProperty("os.arch", "Unknown");
    }

    private int getTotalCores() {
        File cpuDir = new File("/sys/devices/system/cpu/");
        File[] cpuFiles = cpuDir.listFiles((dir, name) -> name.matches("cpu[0-9]+"));
        return cpuFiles != null ? cpuFiles.length : 0;
    }

    private void updateActiveCores() {
        int activeCores = 0;
        int totalCores = getTotalCores();
        
        for (int i = 0; i < totalCores; i++) {
            File onlineFile = new File("/sys/devices/system/cpu/cpu" + i + "/online");
            if (onlineFile.exists()) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(onlineFile));
                    String online = reader.readLine();
                    reader.close();
                    if ("1".equals(online)) {
                        activeCores++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                activeCores++;
            }
        }
        
        activeCoresText.setText("Active Cores: " + activeCores + "/" + totalCores);
    }

    private void enableAllCores() {
        int totalCores = getTotalCores();
        for (int i = 0; i < totalCores; i++) {
            Shell.SU.run("echo 1 > /sys/devices/system/cpu/cpu" + i + "/online");
        }
        updateActiveCores();
        Toast.makeText(requireContext(), "All cores enabled", Toast.LENGTH_SHORT).show();
    }

    private void disableAllCores() {
        int totalCores = getTotalCores();
        for (int i = 1; i < totalCores; i++) {
            Shell.SU.run("echo 0 > /sys/devices/system/cpu/cpu" + i + "/online");
        }
        updateActiveCores();
        Toast.makeText(requireContext(), "Disabled secondary cores", Toast.LENGTH_SHORT).show();
    }

    private String getCpuTemperature() {
        String[] tempPaths = {
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        };
        
        for (String path : tempPaths) {
            try {
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "N/A";
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
        if (handler != null && cpuMonitorRunnable != null) {
            handler.removeCallbacks(cpuMonitorRunnable);
        }
    }
}
