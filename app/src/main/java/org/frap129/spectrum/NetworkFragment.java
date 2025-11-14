package org.frap129.spectrum;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class NetworkFragment extends Fragment {

    private RecyclerView appsRecyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private SearchView searchView;
    private ChipGroup filterChipGroup;
    private Chip chipAll, chipUser, chipSystem, chipBlocked, chipAllowed;
    
    private List<AppInfo> appList = new ArrayList<>();
    private List<AppInfo> filteredList = new ArrayList<>();
    private NetworkAdapter adapter;
    
    private String currentFilter = "ALL";
    private String currentQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_network, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupSearchView();
        setupFilterChips();
        loadAllApps();
    }

    private void initViews(View view) {
        appsRecyclerView = view.findViewById(R.id.appsRecyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyText = view.findViewById(R.id.emptyText);
        searchView = view.findViewById(R.id.searchView);
        filterChipGroup = view.findViewById(R.id.filterChipGroup);
        chipAll = view.findViewById(R.id.chipAll);
        chipUser = view.findViewById(R.id.chipUser);
        chipSystem = view.findViewById(R.id.chipSystem);
        chipBlocked = view.findViewById(R.id.chipBlocked);
        chipAllowed = view.findViewById(R.id.chipAllowed);

        appsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NetworkAdapter(filteredList);
        appsRecyclerView.setAdapter(adapter);
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText.toLowerCase();
                filterApps();
                return true;
            }
        });
    }

    private void setupFilterChips() {
        chipAll.setChecked(true);
        
        chipAll.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) { currentFilter = "ALL"; filterApps(); }
        });
        chipUser.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) { currentFilter = "USER"; filterApps(); }
        });
        chipSystem.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) { currentFilter = "SYSTEM"; filterApps(); }
        });
        chipBlocked.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) { currentFilter = "BLOCKED"; filterApps(); }
        });
        chipAllowed.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) { currentFilter = "ALLOWED"; filterApps(); }
        });
    }

    private void filterApps() {
        filteredList.clear();
        
        for (AppInfo app : appList) {
            boolean matchesSearch = currentQuery.isEmpty() || 
                app.name.toLowerCase().contains(currentQuery) ||
                app.packageName.toLowerCase().contains(currentQuery);
            
            boolean matchesFilter = false;
            switch (currentFilter) {
                case "ALL": matchesFilter = true; break;
                case "USER": matchesFilter = !app.isSystemApp; break;
                case "SYSTEM": matchesFilter = app.isSystemApp; break;
                case "BLOCKED": matchesFilter = app.internetBlocked; break;
                case "ALLOWED": matchesFilter = !app.internetBlocked; break;
            }
            
            if (matchesSearch && matchesFilter) {
                filteredList.add(app);
            }
        }
        
        updateEmptyState();
        adapter.notifyDataSetChanged();
    }

    private void updateEmptyState() {
        if (filteredList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            String message = "No apps found";
            if (!currentQuery.isEmpty()) {
                message += " for '" + currentQuery + "'";
            }
            if (!currentFilter.equals("ALL")) {
                message += " in " + currentFilter.toLowerCase() + " apps";
            }
            emptyText.setText(message);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private void loadAllApps() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        new Thread(() -> {
            appList.clear();
            PackageManager pm = requireContext().getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo packageInfo : packages) {
                AppInfo appInfo = new AppInfo();
                appInfo.packageName = packageInfo.packageName;
                appInfo.name = packageInfo.loadLabel(pm).toString();
                appInfo.icon = packageInfo.loadIcon(pm);
                appInfo.uid = packageInfo.uid;
                appInfo.isSystemApp = (packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                
                appInfo.internetBlocked = isInternetBlocked(appInfo.uid);

                appList.add(appInfo);
            }

            Collections.sort(appList, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));

            new Handler(Looper.getMainLooper()).post(() -> {
                filterApps();
                progressBar.setVisibility(View.GONE);
            });
        }).start();
    }

    private boolean isInternetBlocked(int uid) {
        List<String> result = Shell.SU.run("iptables -L OUTPUT -n | grep " + uid);
        return result != null && !result.isEmpty();
    }

    private void blockInternetCompletely(AppInfo app, boolean block) {
        List<String> commands = new ArrayList<>();
        
        if (block) {
            commands.add("iptables -I OUTPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("iptables -I INPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -I OUTPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -I INPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip rule add from all uidrange " + app.uid + "-" + app.uid + " lookup main");
            commands.add("cmd appops set " + app.packageName + " RUN_IN_BACKGROUND ignore");
            commands.add("cmd appops set " + app.packageName + " BACKGROUND_START deny");
        } else {
            commands.add("iptables -D OUTPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("iptables -D INPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -D OUTPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -D INPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip rule del from all uidrange " + app.uid + "-" + app.uid + " lookup main");
            commands.add("cmd appops set " + app.packageName + " RUN_IN_BACKGROUND allow");
            commands.add("cmd appops set " + app.packageName + " BACKGROUND_START allow");
        }
        
        Shell.SU.run(commands);
        app.internetBlocked = block;
        filterApps();
    }

    private class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {

        private List<AppInfo> apps;

        public NetworkAdapter(List<AppInfo> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = apps.get(position);
            holder.appName.setText(app.name);
            holder.appIcon.setImageDrawable(app.icon);
            holder.packageName.setText(app.packageName);
            
            if (app.isSystemApp) {
                holder.appType.setText("SYSTEM");
                holder.appType.setTextColor(0xFFB399FF);
                holder.appType.setBackgroundResource(R.drawable.bg_system_chip);
            } else {
                holder.appType.setText("USER");
                holder.appType.setTextColor(0xFF4CAF50);
                holder.appType.setBackgroundResource(R.drawable.bg_user_chip);
            }

            if (app.internetBlocked) {
                holder.status.setText("BLOCKED");
                holder.status.setTextColor(0xFFFF5722);
                holder.status.setBackgroundResource(R.drawable.bg_blocked_chip);
                holder.blockSwitch.setChecked(false);
            } else {
                holder.status.setText("ALLOWED");
                holder.status.setTextColor(0xFF4CAF50);
                holder.status.setBackgroundResource(R.drawable.bg_allowed_chip);
                holder.blockSwitch.setChecked(true);
            }

            holder.blockSwitch.setOnCheckedChangeListener(null);
            holder.blockSwitch.setChecked(!app.internetBlocked);
            holder.blockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                blockInternetCompletely(app, !isChecked);
            });

            holder.itemView.setOnClickListener(v -> {
                showAppDetails(app);
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView appIcon;
            TextView appName, packageName, appType, status;
            Switch blockSwitch;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                packageName = itemView.findViewById(R.id.packageName);
                appType = itemView.findViewById(R.id.appType);
                status = itemView.findViewById(R.id.status);
                blockSwitch = itemView.findViewById(R.id.blockSwitch);
            }
        }
    }

    private void showAppDetails(AppInfo app) {
        String status = app.internetBlocked ? "BLOCKED ❌" : "ALLOWED ✅";
        String type = app.isSystemApp ? "System App" : "User App";
        
        new AlertDialog.Builder(requireContext(), R.style.SpectrumDialogTheme)
            .setTitle(app.name)
            .setMessage(
                "Package: " + app.packageName + "\n" +
                "UID: " + app.uid + "\n" +
                "Type: " + type + "\n" +
                "Status: " + status + "\n\n" +
                "Internet access is " + (app.internetBlocked ? "completely blocked" : "fully allowed") + " for this app."
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Refresh", (dialog, which) -> loadAllApps())
            .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAllApps();
    }

    private static class AppInfo {
        String name;
        String packageName;
        int uid;
        boolean isSystemApp;
        android.graphics.drawable.Drawable icon;
        boolean internetBlocked;
    }
                                       }
