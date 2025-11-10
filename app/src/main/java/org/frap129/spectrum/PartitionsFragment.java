package org.frap129.spectrum;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PartitionsFragment extends Fragment {

    private LinearLayout partitionsContainer, memoryContainer;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_partitions, container, false);
        initViews(view);
        setupSwipeRefresh();
        loadData();
        return view;
    }

    private void initViews(View view) {
        partitionsContainer = view.findViewById(R.id.partitionsContainer);
        memoryContainer = view.findViewById(R.id.memoryContainer);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadData();
            }
        });

        swipeRefreshLayout.setColorSchemeColors(
            getResources().getColor(android.R.color.holo_blue_bright),
            getResources().getColor(android.R.color.holo_green_light),
            getResources().getColor(android.R.color.holo_orange_light),
            getResources().getColor(android.R.color.holo_red_light)
        );
    }

    private void loadData() {
        updatePartitionsInfo();
        updateMemoryInfo();
        
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
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
        
        partitions.add(getPartitionInfo("/system", "System"));
        partitions.add(getPartitionInfo("/data", "Data"));
        partitions.add(getPartitionInfo("/cache", "Cache"));
        partitions.add(getPartitionInfo("/vendor", "Vendor"));
        
        File dataDir = new File("/data");
        if (dataDir.exists()) {
            partitions.add(getPartitionInfo("/data", "Internal Storage"));
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
                        partitions.add(getPartitionInfo(path, name));
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
                            partitions.add(getPartitionInfo(path, name));
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

    private PartitionInfo getPartitionInfo(String path, String name) {
        File partition = new File(path);
        long totalSpace = 0;
        long freeSpace = 0;
        long usedSpace = 0;
        String realAccess = getRealAccess(path);
        String realFilesystem = getRealFilesystem(path);
        
        if (partition.exists() && partition.isDirectory()) {
            try {
                totalSpace = partition.getTotalSpace();
                freeSpace = partition.getFreeSpace();
                usedSpace = totalSpace - freeSpace;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        return new PartitionInfo(name, path, realFilesystem, realAccess, usedSpace, freeSpace, totalSpace);
    }

    private String getRealAccess(String path) {
        try {
            Process process = Runtime.getRuntime().exec("mount");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains(path)) {
                    if (line.contains("rw")) {
                        reader.close();
                        return "r/w";
                    } else if (line.contains("ro")) {
                        reader.close();
                        return "r";
                    }
                }
            }
            reader.close();
            
            return getSimpleAccess(path);
        } catch (Exception e) {
            return getSimpleAccess(path);
        }
    }

    private String getSimpleAccess(String path) {
        try {
            File dir = new File(path);
            boolean canRead = dir.canRead();
            boolean canWrite = dir.canWrite();
            
            if (canRead && canWrite) return "r/w";
            else if (canRead) return "r";
            else return "no access";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getRealFilesystem(String path) {
        try {
            Process process = Runtime.getRuntime().exec("mount");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains(path)) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        String fsType = parts[4];
                        if (fsType.contains("(")) {
                            fsType = fsType.substring(0, fsType.indexOf("("));
                        }
                        reader.close();
                        return fsType;
                    }
                }
            }
            reader.close();
            
            return getSimpleFilesystem(path);
        } catch (Exception e) {
            return getSimpleFilesystem(path);
        }
    }

    private String getSimpleFilesystem(String path) {
        if (path.contains("sdcard") || path.contains("storage") || path.contains("emulated")) {
            return "fuse";
        } else if (path.equals("/system") || path.equals("/vendor")) {
            return "ext4";
        } else if (path.equals("/data") || path.equals("/cache")) {
            return "ext4";
        } else {
            return "unknown";
        }
    }

    private View createPartitionView(PartitionInfo partition) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_partition, partitionsContainer, false);
        TextView tvName = view.findViewById(R.id.tvPartitionName);
        TextView tvType = view.findViewById(R.id.tvPartitionType);
        TextView tvAccess = view.findViewById(R.id.tvPartitionAccess);
        TextView tvUsed = view.findViewById(R.id.tvUsedSpace);
        TextView tvFree = view.findViewById(R.id.tvFreeSpace);
        TextView tvTotal = view.findViewById(R.id.tvTotalSpace);
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressBar);
        
        tvName.setText(partition.getName());
        tvType.setText(partition.getType());
        tvAccess.setText(partition.getAccess());
        
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

    private static class PartitionInfo {
        private String name;
        private String path;
        private String type;
        private String access;
        private long usedSpace;
        private long freeSpace;
        private long totalSpace;

        public PartitionInfo(String name, String path, String type, String access, long usedSpace, long freeSpace, long totalSpace) {
            this.name = name;
            this.path = path;
            this.type = type;
            this.access = access;
            this.usedSpace = usedSpace;
            this.freeSpace = freeSpace;
            this.totalSpace = totalSpace;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
        public String getType() { return type; }
        public String getAccess() { return access; }
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
