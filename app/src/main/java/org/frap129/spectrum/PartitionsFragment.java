package org.frap129.spectrum;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
        partitionsContainer = view.findViewById(R.id.partitionsContainer);
        memoryContainer = view.findViewById(R.id.memoryContainer);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setBackgroundColor(0x00000000);
        requestPermissions();
        setupSwipeRefresh();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (partitionsContainer.getChildCount() == 0) loadData();
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadData();
            swipeRefreshLayout.setRefreshing(false);
        });
        swipeRefreshLayout.setColorSchemeColors(0xFFB399FF);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(0xFF1A1A1A);
    }

    private void loadData() {
        updatePartitionsInfo();
        updateMemoryInfo();
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
        findExternalStorage(partitions);
        return partitions;
    }

    private void findExternalStorage(List<PartitionInfo> partitions) {
        File[] externalDirs = getContext().getExternalFilesDirs(null);
        for (File dir : externalDirs) {
            if (dir != null) {
                String path = dir.getAbsolutePath();
                if (!path.contains("emulated")) {
                    File root = new File(path.split("/Android")[0]);
                    if (root.exists() && root.getTotalSpace() > 0) {
                        partitions.add(getPartitionInfo(root.getAbsolutePath(), "External Storage"));
                    }
                }
            }
        }
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
            } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
        return getSimpleAccess(path);
    }

    private String getSimpleAccess(String path) {
        if (path.equals("/system") || path.equals("/vendor")) return "r";
        else return "r/w";
    }

    private String getRealFilesystem(String path) {
        try {
            File realPath = new File(path).getCanonicalFile();
            String resolvedPath = realPath.getAbsolutePath();
            BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            String foundFs = "unknown";
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String mountPoint = parts[1];
                    String fsType = parts[2];
                    if (resolvedPath.equals(mountPoint) || resolvedPath.startsWith(mountPoint + "/") || mountPoint.startsWith(resolvedPath + "/")) {
                        foundFs = fsType;
                        break;
                    }
                }
            }
            reader.close();
            return foundFs;
        } catch (Exception e) {
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
        tvUsed.setText(formatStorageSize(partition.getUsedSpace(), "used"));
        tvFree.setText(formatStorageSize(partition.getFreeSpace(), "free"));
        tvTotal.setText(formatStorageSize(partition.getTotalSpace(), "total"));
        int progress = 0;
        if (partition.getTotalSpace() > 0) progress = (int) ((partition.getUsedSpace() * 100) / partition.getTotalSpace());
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
            long availableMemory = 0;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) totalMemory = parseMemInfoLine(line);
                else if (line.startsWith("MemAvailable:")) availableMemory = parseMemInfoLine(line);
            }
            reader.close();
            long totalBytes = totalMemory * 1024;
            long availableBytes = availableMemory * 1024;
            long usedBytes = totalBytes - availableBytes;
            return new MemoryInfo(usedBytes, availableBytes, totalBytes);
        } catch (Exception e) {
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
        if (memoryInfo.getTotal() > 0) progress = (int) ((memoryInfo.getUsed() * 100) / memoryInfo.getTotal());
        progressBar.setProgress(progress);
        return view;
    }

    private String formatStorageSize(long bytes, String type) {
        double megaBytes = bytes / (1024.0 * 1024.0);
        double gigaBytes = bytes / (1024.0 * 1024.0 * 1024.0);
        if (megaBytes < 800) {
            if (type.equals("used")) return String.format("%.1f MB used", megaBytes);
            else if (type.equals("free")) return String.format("%.1f MB free", megaBytes);
            else return String.format("%.1f MB total", megaBytes);
        } else {
            if (type.equals("used")) return String.format("%.1f GB used", gigaBytes);
            else if (type.equals("free")) return String.format("%.1f GB free", gigaBytes);
            else return String.format("%.1f GB total", gigaBytes);
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
