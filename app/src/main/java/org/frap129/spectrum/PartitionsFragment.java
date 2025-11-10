package org.frap129.spectrum;

import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
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
    private boolean isFirstLoad = true;
    private Handler handler = new Handler();

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
            loadDataWithAnimation();
        });
        swipeRefreshLayout.setColorSchemeColors(0xFFB399FF);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(0xFF1A1A1A);
        swipeRefreshLayout.setProgressViewEndTarget(true, 200);
        swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
    }

    private void loadData() {
        if (isFirstLoad) {
            loadDataWithInitialAnimation();
            isFirstLoad = false;
        } else {
            updatePartitionsInfo();
            updateMemoryInfo();
        }
    }

    private void loadDataWithInitialAnimation() {
        if (getActivity() == null) return;
        
        getActivity().runOnUiThread(() -> {
            partitionsContainer.removeAllViews();
            memoryContainer.removeAllViews();
            
            List<PartitionInfo> partitions = getPartitionsInfo();
            for (PartitionInfo partition : partitions) {
                View partitionView = createPartitionViewWithAnimation(partition);
                partitionsContainer.addView(partitionView);
            }

            MemoryInfo ramInfo = getMemoryInfo();
            View ramView = createMemoryViewWithAnimation("Memory (RAM)", ramInfo);
            memoryContainer.addView(ramView);
            
            MemoryInfo swapInfo = getSwapInfo();
            if (swapInfo.getTotal() > 0) {
                View swapView = createMemoryViewWithAnimation("Swap Memory", swapInfo);
                memoryContainer.addView(swapView);
            }
        });
    }

    private void loadDataWithAnimation() {
        if (getActivity() == null) return;
        
        getActivity().runOnUiThread(() -> {
            partitionsContainer.removeAllViews();
            memoryContainer.removeAllViews();
            
            handler.postDelayed(() -> {
                List<PartitionInfo> partitions = getPartitionsInfo();
                for (int i = 0; i < partitions.size(); i++) {
                    PartitionInfo partition = partitions.get(i);
                    View partitionView = createPartitionViewWithRefreshAnimation(partition);
                    partitionsContainer.addView(partitionView);
                    
                    if (i < partitions.size() - 1) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                MemoryInfo ramInfo = getMemoryInfo();
                View ramView = createMemoryViewWithRefreshAnimation("Memory (RAM)", ramInfo);
                memoryContainer.addView(ramView);
                
                MemoryInfo swapInfo = getSwapInfo();
                if (swapInfo.getTotal() > 0) {
                    View swapView = createMemoryViewWithRefreshAnimation("Swap Memory", swapInfo);
                    memoryContainer.addView(swapView);
                }
                
                swipeRefreshLayout.setRefreshing(false);
            }, 500);
        });
    }

    private View createPartitionViewWithAnimation(PartitionInfo partition) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_partition, partitionsContainer, false);
        setupPartitionView(view, partition);
        
        view.setAlpha(0f);
        view.setTranslationY(50f);
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .start();
        
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressBar);
        animateProgressBar(progressBar, partition, 800);
        
        return view;
    }

    private View createPartitionViewWithRefreshAnimation(PartitionInfo partition) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_partition, partitionsContainer, false);
        setupPartitionView(view, partition);
        
        view.setAlpha(0f);
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .start();
        
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressBar);
        animateProgressBar(progressBar, partition, 600);
        
        return view;
    }

    private View createMemoryViewWithAnimation(String title, MemoryInfo memoryInfo) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_memory, memoryContainer, false);
        setupMemoryView(view, title, memoryInfo);
        
        view.setAlpha(0f);
        view.setTranslationY(50f);
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(200)
            .start();
        
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressMemory);
        animateProgressBar(progressBar, memoryInfo, 800);
        
        return view;
    }

    private View createMemoryViewWithRefreshAnimation(String title, MemoryInfo memoryInfo) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_memory, memoryContainer, false);
        setupMemoryView(view, title, memoryInfo);
        
        view.setAlpha(0f);
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .start();
        
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressMemory);
        animateProgressBar(progressBar, memoryInfo, 600);
        
        return view;
    }

    private void setupPartitionView(View view, PartitionInfo partition) {
        TextView tvName = view.findViewById(R.id.tvPartitionName);
        TextView tvAccess = view.findViewById(R.id.tvPartitionAccess);
        TextView tvUsed = view.findViewById(R.id.tvUsedSpace);
        TextView tvFree = view.findViewById(R.id.tvFreeSpace);
        TextView tvTotal = view.findViewById(R.id.tvTotalSpace);
        
        tvName.setText(partition.getName());
        tvAccess.setText(partition.getAccess());
        tvUsed.setText(formatStorageSize(partition.getUsedSpace(), "used"));
        tvFree.setText(formatStorageSize(partition.getFreeSpace(), "free"));
        tvTotal.setText(formatStorageSize(partition.getTotalSpace(), "total"));
    }

    private void setupMemoryView(View view, String title, MemoryInfo memoryInfo) {
        TextView tvTitle = view.findViewById(R.id.tvMemoryTitle);
        TextView tvUsed = view.findViewById(R.id.tvMemoryUsed);
        TextView tvFree = view.findViewById(R.id.tvMemoryFree);
        TextView tvTotal = view.findViewById(R.id.tvMemoryTotal);
        
        tvTitle.setText(title);
        tvUsed.setText(formatStorageSize((long) memoryInfo.getUsed(), "used"));
        tvFree.setText(formatStorageSize((long) memoryInfo.getFree(), "free"));
        tvTotal.setText(formatStorageSize((long) memoryInfo.getTotal(), "total"));
    }

    private void animateProgressBar(LinearProgressIndicator progressBar, PartitionInfo partition, int duration) {
        final int targetProgress;
        if (partition.getTotalSpace() > 0) {
            targetProgress = (int) ((partition.getUsedSpace() * 100) / partition.getTotalSpace());
        } else {
            targetProgress = 0;
        }
        
        progressBar.setProgress(0);
        progressBar.animate()
            .setDuration(duration)
            .setStartDelay(300)
            .start();
        
        handler.postDelayed(() -> {
            progressBar.setProgress(targetProgress);
        }, 300);
    }

    private void animateProgressBar(LinearProgressIndicator progressBar, MemoryInfo memoryInfo, int duration) {
        final int targetProgress;
        if (memoryInfo.getTotal() > 0) {
            targetProgress = (int) ((memoryInfo.getUsed() * 100) / memoryInfo.getTotal());
        } else {
            targetProgress = 0;
        }
        
        progressBar.setProgress(0);
        progressBar.animate()
            .setDuration(duration)
            .setStartDelay(300)
            .start();
        
        handler.postDelayed(() -> {
            progressBar.setProgress(targetProgress);
        }, 300);
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

    private View createPartitionView(PartitionInfo partition) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_partition, partitionsContainer, false);
        TextView tvName = view.findViewById(R.id.tvPartitionName);
        TextView tvAccess = view.findViewById(R.id.tvPartitionAccess);
        TextView tvUsed = view.findViewById(R.id.tvUsedSpace);
        TextView tvFree = view.findViewById(R.id.tvFreeSpace);
        TextView tvTotal = view.findViewById(R.id.tvTotalSpace);
        LinearProgressIndicator progressBar = view.findViewById(R.id.progressBar);
        
        tvName.setText(partition.getName());
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

    private List<PartitionInfo> getPartitionsInfo() {
        List<PartitionInfo> partitions = new ArrayList<>();
        partitions.add(getPartitionInfo("/system", "System"));
        partitions.add(getPartitionInfo("/data", "Data"));
        partitions.add(getPartitionInfo("/cache", "Cache"));
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
        if (partition.exists() && partition.isDirectory()) {
            try {
                totalSpace = partition.getTotalSpace();
                freeSpace = partition.getFreeSpace();
                usedSpace = totalSpace - freeSpace;
            } catch (Exception ignored) {}
        }
        return new PartitionInfo(name, path, realAccess, usedSpace, freeSpace, totalSpace);
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
        if (path.equals("/system")) return "r";
        else return "r/w";
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
        private String access;
        private long usedSpace;
        private long freeSpace;
        private long totalSpace;
        
        public PartitionInfo(String name, String path, String access, long usedSpace, long freeSpace, long totalSpace) {
            this.name = name;
            this.path = path;
            this.access = access;
            this.usedSpace = usedSpace;
            this.freeSpace = freeSpace;
            this.totalSpace = totalSpace;
        }
        
        public String getName() { return name; }
        public String getPath() { return path; }
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
