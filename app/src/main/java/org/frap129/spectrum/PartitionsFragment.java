package org.frap129.spectrum;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class PartitionsFragment extends Fragment {

    private LinearLayout partitionsContainer, memoryContainer;
    private Handler handler = new Handler();
    private Runnable updateRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_partitions, container, false);
        initViews(view);
        startMonitoring();
        return view;
    }

    private void initViews(View view) {
        partitionsContainer = view.findViewById(R.id.partitionsContainer);
        memoryContainer = view.findViewById(R.id.memoryContainer);
    }

    private void startMonitoring() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updatePartitionsInfo();
                updateMemoryInfo();
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(updateRunnable);
    }

    private void updatePartitionsInfo() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            partitionsContainer.removeAllViews();
            List<PartitionInfo> partitions = getPartitionsInfo();
            for (PartitionInfo partition : partitions) {
                View partitionView = createPartitionView(partition);
                partitionsContainer.addView(partitionView);
            }
        });
    }

    private List<PartitionInfo> getPartitionsInfo() {
        List<PartitionInfo> partitions = new ArrayList<>();
        partitions.add(getPartitionInfo("/system", "System", "ext4", "r"));
        partitions.add(getPartitionInfo("/data", "Data", "ext4", "r/w"));
        partitions.add(getPartitionInfo("/cache", "Cache", "ext4", "r/w"));
        partitions.add(getPartitionInfo("/vendor", "Vendor", "ext4", "r"));
        File dataDir = new File("/data");
        if (dataDir.exists()) {
            partitions.add(getPartitionInfo("/data", "Internal Storage", "fuse", "r/w"));
        }
        File sdcardDir = new File("/sdcard");
        if (sdcardDir.exists()) {
            partitions.add(getPartitionInfo("/sdcard", "SD Card", "fuse", "r/w"));
        }
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.listFiles() != null) {
            for (File file : storageDir.listFiles()) {
                if (file.isDirectory() && !file.getName().equals("emulated")) {
                    partitions.add(getPartitionInfo(file.getAbsolutePath(), "External " + file.getName(), "fuse", "r/w"));
                }
            }
        }
        return partitions;
    }

    private PartitionInfo getPartitionInfo(String path, String name, String type, String access) {
        File partition = new File(path);
        long totalSpace = 0;
        long freeSpace = 0;
        long usedSpace = 0;
        if (partition.exists()) {
            totalSpace = partition.getTotalSpace();
            freeSpace = partition.getFreeSpace();
            usedSpace = totalSpace - freeSpace;
        }
        return new PartitionInfo(name, path, type, access, "4.00 KiB", usedSpace, freeSpace, totalSpace);
    }

    private View createPartitionView(PartitionInfo partition) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_partition, partitionsContainer, false);
        TextView tvName = view.findViewById(R.id.tvPartitionName);
        TextView tvType = view.findViewById(R.id.tvPartitionType);
        TextView tvAccess = view.findViewById(R.id.tvPartitionAccess);
        TextView tvBlockSize = view.findViewById(R.id.tvBlockSize);
        TextView tvUsed = view.findViewById(R.id.tvUsedSpace);
        TextView tvFree = view.findViewById(R.id.tvFreeSpace);
        TextView tvTotal = view.findViewById(R.id.tvTotalSpace);
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressBar);
        
        tvName.setText(partition.getName());
        tvType.setText(partition.getType());
        tvAccess.setText(partition.getAccess());
        tvBlockSize.setText(partition.getBlockSize());
        
        double usedGB = partition.getUsedSpace() / (1024.0 * 1024.0 * 1024.0);
        double freeGB = partition.getFreeSpace() / (1024.0 * 1024.0 * 1024.0);
        double totalGB = partition.getTotalSpace() / (1024.0 * 1024.0 * 1024.0);
        
        tvUsed.setText(String.format("%.2f GiB used", usedGB));
        tvFree.setText(String.format("%.2f GiB free", freeGB));
        tvTotal.setText(String.format("%.2f GiB total", totalGB));
        
        int progress = 0;
        if (totalGB > 0) progress = (int) ((usedGB / totalGB) * 100);
        progressBar.setProgress(progress);
        
        return view;
    }

    private void updateMemoryInfo() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            memoryContainer.removeAllViews();
            MemoryInfo ramInfo = getMemoryInfo();
            View ramView = createMemoryView("Memory (RAM)", ramInfo, true);
            memoryContainer.addView(ramView);
            
            MemoryInfo swapInfo = getSwapInfo();
            if (swapInfo.getTotal() > 0) {
                View swapView = createMemoryView("Swap Memory", swapInfo, false);
                memoryContainer.addView(swapView);
            }
        });
    }

    private MemoryInfo getMemoryInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            long totalMemory = 0;
            long freeMemory = 0;
            long availableMemory = 0;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) totalMemory = parseMemInfoLine(line);
                else if (line.startsWith("MemFree:")) freeMemory = parseMemInfoLine(line);
                else if (line.startsWith("MemAvailable:")) availableMemory = parseMemInfoLine(line);
            }
            reader.close();
            
            // Convert from KB to GB
            double totalGB = totalMemory / (1024.0 * 1024.0);
            double availableGB = availableMemory / (1024.0 * 1024.0);
            double usedGB = totalGB - availableGB;
            
            return new MemoryInfo(usedGB, availableGB, totalGB);
        } catch (Exception e) {
            e.printStackTrace();
            return new MemoryInfo(0, 0, 0);
        }
    }

    private MemoryInfo getSwapInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            long totalSwap = 0;
            long freeSwap = 0;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) totalSwap = parseMemInfoLine(line);
                else if (line.startsWith("SwapFree:")) freeSwap = parseMemInfoLine(line);
            }
            reader.close();
            
            // Convert from KB to GB
            double totalGB = totalSwap / (1024.0 * 1024.0);
            double freeGB = freeSwap / (1024.0 * 1024.0);
            double usedGB = totalGB - freeGB;
            
            return new MemoryInfo(usedGB, freeGB, totalGB);
        } catch (Exception e) {
            e.printStackTrace();
            return new MemoryInfo(0, 0, 0);
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

    private View createMemoryView(String title, MemoryInfo memoryInfo, boolean isRam) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_memory, memoryContainer, false);
        TextView tvTitle = view.findViewById(R.id.tvMemoryTitle);
        TextView tvUsed = view.findViewById(R.id.tvMemoryUsed);
        TextView tvFree = view.findViewById(R.id.tvMemoryFree);
        TextView tvTotal = view.findViewById(R.id.tvMemoryTotal);
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressMemory);
        
        tvTitle.setText(title);
        tvUsed.setText(String.format("%.1f GiB used", memoryInfo.getUsed()));
        tvFree.setText(String.format("%.1f GiB free", memoryInfo.getFree()));
        tvTotal.setText(String.format("%.1f GiB total", memoryInfo.getTotal()));
        
        int progress = 0;
        if (memoryInfo.getTotal() > 0) {
            progress = (int) ((memoryInfo.getUsed() / memoryInfo.getTotal()) * 100);
        }
        progressBar.setProgress(progress);
        
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private static class PartitionInfo {
        private String name;
        private String path;
        private String type;
        private String access;
        private String blockSize;
        private long usedSpace;
        private long freeSpace;
        private long totalSpace;

        public PartitionInfo(String name, String path, String type, String access, String blockSize, long usedSpace, long freeSpace, long totalSpace) {
            this.name = name;
            this.path = path;
            this.type = type;
            this.access = access;
            this.blockSize = blockSize;
            this.usedSpace = usedSpace;
            this.freeSpace = freeSpace;
            this.totalSpace = totalSpace;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
        public String getType() { return type; }
        public String getAccess() { return access; }
        public String getBlockSize() { return blockSize; }
        public long getUsedSpace() { return usedSpace; }
        public long getFreeSpace() { return freeSpace; }
        public long getTotalSpace() { return totalSpace; }
    }

    private static class MemoryInfo {
        private double used;
        private double free;
        private double total;

        public MemoryInfo(double used, double free, double total) {
            this.used = used;
            this.free = free;
            this.total = total;
        }

        public double getUsed() { return used; }
        public double getFree() { return free; }
        public double getTotal() { return total; }
    }
                                                }
