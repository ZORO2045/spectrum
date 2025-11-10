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
            partitions.add(getPartitionInfo("/data", "Internal Storage", "ext4", "r/w"));
        }
        
        findExternalStorage(partitions);
        
        return partitions;
    }

    private void findExternalStorage(List<PartitionInfo> partitions) {
        String[] possiblePaths = {
            "/storage/sdcard1",
            "/storage/extSdCard",
            "/storage/external_sd",
            "/storage/external",
            "/sdcard/external_sd",
            "/mnt/sdcard/external_sd",
            "/mnt/external_sd",
            "/mnt/sdcard",
            "/sdcard",
            "/storage/emulated/0"
        };
        
        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory() && dir.canRead()) {
                long totalSpace = dir.getTotalSpace();
                long freeSpace = dir.getFreeSpace();
                
                if (totalSpace > 1024 * 1024) {
                    String name = getStorageName(path);
                    if (!isPartitionAlreadyAdded(partitions, name)) {
                        partitions.add(getPartitionInfo(path, name, "fuse", "r/w"));
                    }
                }
            }
        }
        
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.listFiles() != null) {
            for (File file : storageDir.listFiles()) {
                if (file.isDirectory() && !file.getName().equals("emulated") && 
                    !file.getName().equals("self") && !file.getName().equals("0")) {
                    
                    String path = file.getAbsolutePath();
                    long totalSpace = file.getTotalSpace();
                    
                    if (totalSpace > 1024 * 1024) {
                        String name = "SD Card - " + file.getName();
                        if (!isPartitionAlreadyAdded(partitions, name)) {
                            partitions.add(getPartitionInfo(path, name, "fuse", "r/w"));
                        }
                    }
                }
            }
        }
    }

    private String getStorageName(String path) {
        switch (path) {
            case "/storage/sdcard1":
            case "/storage/extSdCard":
            case "/storage/external_sd":
                return "External SD Card";
            case "/storage/emulated/0":
            case "/sdcard":
                return "Internal Storage";
            default:
                if (path.contains("sdcard") || path.contains("external")) {
                    return "SD Card";
                } else {
                    return "Storage - " + new File(path).getName();
                }
        }
    }

    private boolean isPartitionAlreadyAdded(List<PartitionInfo> partitions, String name) {
        for (PartitionInfo partition : partitions) {
            if (partition.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private PartitionInfo getPartitionInfo(String path, String name, String type, String access) {
        File partition = new File(path);
        long totalSpace = 0;
        long freeSpace = 0;
        long usedSpace = 0;
        
        if (partition.exists() && partition.isDirectory()) {
            try {
                totalSpace = partition.getTotalSpace();
                freeSpace = partition.getFreeSpace();
                usedSpace = totalSpace - freeSpace;
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        
        String usedText = formatStorageSize(partition.getUsedSpace(), "used");
        String freeText = formatStorageSize(partition.getFreeSpace(), "free");
        String totalText = formatStorageSize(partition.getTotalSpace(), "total");
        
        tvUsed.setText(usedText);
        tvFree.setText(freeText);
        tvTotal.setText(totalText);
        
        int progress = 0;
        if (partition.getTotalSpace() > 0) {
            progress = (int) ((partition.getUsedSpace() * 100) / partition.getTotalSpace());
        }
        progressBar.setProgress(progress);
        
        return view;
    }

    private void updateMemoryInfo() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            memoryContainer.removeAllViews();
            MemoryInfo ramInfo = getMemoryInfo();
            View ramView = createMemoryView("Memory (RAM)", ramInfo);
            memoryContainer.addView(ramView);
            
            MemoryInfo swapInfo = getSwapInfo();
            if (swapInfo.getTotal() > 0) {
                View swapView = createMemoryView("Swap Memory", swapInfo);
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
            
            long totalBytes = totalMemory * 1024;
            long availableBytes = availableMemory * 1024;
            long usedBytes = totalBytes - availableBytes;
            
            return new MemoryInfo(usedBytes, availableBytes, totalBytes);
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
            
            long totalBytes = totalSwap * 1024;
            long freeBytes = freeSwap * 1024;
            long usedBytes = totalBytes - freeBytes;
            
            return new MemoryInfo(usedBytes, freeBytes, totalBytes);
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

    private View createMemoryView(String title, MemoryInfo memoryInfo) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_memory, memoryContainer, false);
        TextView tvTitle = view.findViewById(R.id.tvMemoryTitle);
        TextView tvUsed = view.findViewById(R.id.tvMemoryUsed);
        TextView tvFree = view.findViewById(R.id.tvMemoryFree);
        TextView tvTotal = view.findViewById(R.id.tvMemoryTotal);
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressMemory);
        
        tvTitle.setText(title);
        
        tvUsed.setText(formatStorageSize((long) memoryInfo.getUsed(), "used"));
        tvFree.setText(formatStorageSize((long) memoryInfo.getFree(), "free"));
        tvTotal.setText(formatStorageSize((long) memoryInfo.getTotal(), "total"));
        
        int progress = 0;
        if (memoryInfo.getTotal() > 0) {
            progress = (int) ((memoryInfo.getUsed() * 100) / memoryInfo.getTotal());
        }
        progressBar.setProgress(progress);
        
        return view;
    }

    private String formatStorageSize(long bytes, String type) {
        double megaBytes = bytes / (1024.0 * 1024.0);
        double gigaBytes = bytes / (1024.0 * 1024.0 * 1024.0);
        
        if (megaBytes < 800) {
            if (type.equals("used")) {
                return String.format("%.1f MB used", megaBytes);
            } else if (type.equals("free")) {
                return String.format("%.1f MB free", megaBytes);
            } else {
                return String.format("%.1f MB total", megaBytes);
            }
        } else {
            if (type.equals("used")) {
                return String.format("%.1f GB used", gigaBytes);
            } else if (type.equals("free")) {
                return String.format("%.1f GB free", gigaBytes);
            } else {
                return String.format("%.1f GB total", gigaBytes);
            }
        }
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
