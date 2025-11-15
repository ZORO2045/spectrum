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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.chainfire.libsuperuser.Shell;

public class NetworkFragment extends Fragment {

    private RecyclerView appsRecyclerView;
    private TextView emptyText;
    private EditText searchEditText;
    private ChipGroup filterChipGroup;
    private Chip chipAll, chipUser, chipSystem, chipBlocked, chipAllowed;
    
    private List<AppInfo> appList = new ArrayList<>();
    private List<AppInfo> filteredList = new ArrayList<>();
    private NetworkAdapter adapter;
    
    private String currentFilter = "ALL";
    private String currentQuery = "";
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private AlertDialog currentDialog;
    private AppInfo currentDialogApp;

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
        emptyText = view.findViewById(R.id.emptyText);
        searchEditText = view.findViewById(R.id.searchEditText);
        filterChipGroup = view.findViewById(R.id.filterChipGroup);
        chipAll = view.findViewById(R.id.chipAll);
        chipUser = view.findViewById(R.id.chipUser);
        chipSystem = view.findViewById(R.id.chipSystem);
        chipBlocked = view.findViewById(R.id.chipBlocked);
        chipAllowed = view.findViewById(R.id.chipAllowed);

        appsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NetworkAdapter(filteredList);
        appsRecyclerView.setAdapter(adapter);
        
        appsRecyclerView.setHasFixedSize(true);
        appsRecyclerView.setItemViewCacheSize(50);
    }

    private void setupSearchView() {
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().toLowerCase();
                filterApps();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupFilterChips() {
        setActiveChip(chipAll);
        
        chipAll.setOnClickListener(v -> {
            setActiveChip(chipAll);
            currentFilter = "ALL";
            filterApps();
        });

        chipUser.setOnClickListener(v -> {
            setActiveChip(chipUser);
            currentFilter = "USER";
            filterApps();
        });

        chipSystem.setOnClickListener(v -> {
            setActiveChip(chipSystem);
            currentFilter = "SYSTEM";
            filterApps();
        });

        chipBlocked.setOnClickListener(v -> {
            setActiveChip(chipBlocked);
            currentFilter = "BLOCKED";
            filterApps();
        });

        chipAllowed.setOnClickListener(v -> {
            setActiveChip(chipAllowed);
            currentFilter = "ALLOWED";
            filterApps();
        });
    }

    private void setActiveChip(Chip activeChip) {
        Chip[] chips = {chipAll, chipUser, chipSystem, chipBlocked, chipAllowed};
        for (Chip chip : chips) {
            boolean isActive = chip == activeChip;
            chip.setChecked(isActive);
            if (isActive) {
                chip.setChipBackgroundColorResource(R.color.chip_background_checked);
                chip.setTextColor(getResources().getColor(R.color.chip_text_checked));
            } else {
                chip.setChipBackgroundColorResource(R.color.chip_background_color);
                chip.setTextColor(getResources().getColor(R.color.chip_text_color));
            }
        }
    }

    private void filterApps() {
        filteredList.clear();
        
        Set<String> uniquePackages = new HashSet<>();
        
        for (AppInfo app : appList) {
            if (uniquePackages.contains(app.packageName)) {
                continue;
            }
            uniquePackages.add(app.packageName);
            
            boolean matchesSearch = currentQuery.isEmpty() || 
                app.name.toLowerCase().contains(currentQuery) ||
                app.packageName.toLowerCase().contains(currentQuery);
            
            boolean matchesFilter = false;
            switch (currentFilter) {
                case "ALL": matchesFilter = true; break;
                case "USER": matchesFilter = !app.isSystemApp; break;
                case "SYSTEM": matchesFilter = app.isSystemApp; break;
                case "BLOCKED": matchesFilter = app.wifiBlocked || app.dataBlocked || app.backgroundBlocked || app.vpnBlocked; break;
                case "ALLOWED": matchesFilter = !app.wifiBlocked && !app.dataBlocked && !app.backgroundBlocked && !app.vpnBlocked; break;
            }
            
            if (matchesSearch && matchesFilter) {
                filteredList.add(app);
            }
        }
        
        Collections.sort(filteredList, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
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
        emptyText.setText("Loading apps...");
        emptyText.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            List<AppInfo> tempList = new ArrayList<>();
            Set<String> packageSet = new HashSet<>();
            PackageManager pm = requireContext().getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo packageInfo : packages) {
                if (packageSet.contains(packageInfo.packageName)) {
                    continue;
                }
                packageSet.add(packageInfo.packageName);
                
                AppInfo appInfo = new AppInfo();
                appInfo.packageName = packageInfo.packageName;
                appInfo.name = packageInfo.loadLabel(pm).toString();
                appInfo.icon = packageInfo.loadIcon(pm);
                appInfo.uid = packageInfo.uid;
                appInfo.isSystemApp = (packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                
                appInfo.wifiBlocked = false;
                appInfo.dataBlocked = false;
                appInfo.backgroundBlocked = false;
                appInfo.vpnBlocked = false;

                tempList.add(appInfo);
            }

            Collections.sort(tempList, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));

            mainHandler.post(() -> {
                appList.clear();
                appList.addAll(tempList);
                filterApps();
            });

            checkRestrictionsInBackground(tempList);
        });
    }

    private void checkRestrictionsInBackground(List<AppInfo> tempList) {
        new Thread(() -> {
            for (AppInfo app : tempList) {
                app.wifiBlocked = isWifiBlocked(app.uid);
                app.dataBlocked = isDataBlocked(app.uid);
                app.backgroundBlocked = isBackgroundBlocked(app.packageName);
                app.vpnBlocked = isVpnBlocked(app.uid);
            }

            mainHandler.post(() -> {
                appList.clear();
                appList.addAll(tempList);
                filterApps();
                
                if (currentDialog != null && currentDialogApp != null) {
                    updateCurrentDialog();
                }
            });
        }).start();
    }

    private void updateCurrentDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            showAdvancedOptions(currentDialogApp);
        }
    }

    private boolean isWifiBlocked(int uid) {
        List<String> result = Shell.SU.run("iptables -L OUTPUT -n | grep " + uid);
        return result != null && !result.isEmpty();
    }

    private boolean isDataBlocked(int uid) {
        List<String> result = Shell.SU.run("ip6tables -L OUTPUT -n | grep " + uid);
        return result != null && !result.isEmpty();
    }

    private boolean isBackgroundBlocked(String packageName) {
        List<String> result = Shell.SU.run("cmd appops get " + packageName + " RUN_IN_BACKGROUND");
        return result != null && result.toString().contains("ignore");
    }

    private boolean isVpnBlocked(int uid) {
        List<String> result = Shell.SU.run("ip rule show | grep " + uid);
        return result != null && !result.isEmpty();
    }

    private void setWifiRestriction(AppInfo app, boolean block) {
        List<String> commands = new ArrayList<>();
        if (block) {
            commands.add("iptables -I OUTPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("iptables -I INPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
        } else {
            commands.add("iptables -D OUTPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("iptables -D INPUT -m owner --uid-owner " + app.uid + " -j DROP");
        }
        Shell.SU.run(commands);
        app.wifiBlocked = block;
    }

    private void setDataRestriction(AppInfo app, boolean block) {
        List<String> commands = new ArrayList<>();
        if (block) {
            commands.add("ip6tables -I OUTPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -I INPUT 1 -m owner --uid-owner " + app.uid + " -j DROP");
        } else {
            commands.add("ip6tables -D OUTPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -D INPUT -m owner --uid-owner " + app.uid + " -j DROP");
        }
        Shell.SU.run(commands);
        app.dataBlocked = block;
    }

    private void setBackgroundRestriction(AppInfo app, boolean block) {
        List<String> commands = new ArrayList<>();
        if (block) {
            commands.add("cmd appops set " + app.packageName + " RUN_IN_BACKGROUND ignore");
            commands.add("cmd appops set " + app.packageName + " BACKGROUND_START deny");
        } else {
            commands.add("cmd appops set " + app.packageName + " RUN_IN_BACKGROUND allow");
            commands.add("cmd appops set " + app.packageName + " BACKGROUND_START allow");
        }
        Shell.SU.run(commands);
        app.backgroundBlocked = block;
    }

    private void setVpnRestriction(AppInfo app, boolean block) {
        List<String> commands = new ArrayList<>();
        if (block) {
            commands.add("ip rule add from all uidrange " + app.uid + "-" + app.uid + " lookup main");
        } else {
            commands.add("ip rule del from all uidrange " + app.uid + "-" + app.uid + " lookup main");
        }
        Shell.SU.run(commands);
        app.vpnBlocked = block;
    }

    private void blockAllInternet(AppInfo app, boolean block) {
        setWifiRestriction(app, block);
        setDataRestriction(app, block);
        setVpnRestriction(app, block);
        setBackgroundRestriction(app, block);
        
        app.wifiBlocked = block;
        app.dataBlocked = block;
        app.vpnBlocked = block;
        app.backgroundBlocked = block;
        
        if (block) {
            Toast.makeText(requireContext(), "All internet access blocked for " + app.name, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "All internet access allowed for " + app.name, Toast.LENGTH_SHORT).show();
        }
        
        mainHandler.post(() -> {
            filterApps();
        });
    }

    private void showAdvancedOptions(AppInfo app) {
        currentDialogApp = app;
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_network_options, null);
        
        ImageView appIcon = dialogView.findViewById(R.id.dialogAppIcon);
        TextView appName = dialogView.findViewById(R.id.dialogAppName);
        Switch mainSwitch = dialogView.findViewById(R.id.mainSwitch);
        Switch wifiSwitch = dialogView.findViewById(R.id.wifiSwitch);
        Switch dataSwitch = dialogView.findViewById(R.id.dataSwitch);
        Switch backgroundSwitch = dialogView.findViewById(R.id.backgroundSwitch);
        Switch vpnSwitch = dialogView.findViewById(R.id.vpnSwitch);
        
        appIcon.setImageDrawable(app.icon);
        appName.setText(app.name);
        
        setupSwitchesWithoutListeners(mainSwitch, wifiSwitch, dataSwitch, backgroundSwitch, vpnSwitch, app);
        
        mainSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setSwitchesListenersEnabled(false, wifiSwitch, dataSwitch, backgroundSwitch, vpnSwitch);
            
            blockAllInternet(app, !isChecked);
            
            wifiSwitch.setChecked(isChecked);
            dataSwitch.setChecked(isChecked);
            backgroundSwitch.setChecked(isChecked);
            vpnSwitch.setChecked(isChecked);
            
            setSwitchesListenersEnabled(true, wifiSwitch, dataSwitch, backgroundSwitch, vpnSwitch);
        });
        
        setupIndividualSwitches(wifiSwitch, dataSwitch, backgroundSwitch, vpnSwitch, mainSwitch, app);
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.SpectrumDialogTheme)
            .setView(dialogView)
            .setPositiveButton("Save Changes", (d, which) -> {
                currentDialog = null;
                currentDialogApp = null;
            })
            .setOnDismissListener(d -> {
                currentDialog = null;
                currentDialogApp = null;
            })
            .show();

        currentDialog = dialog;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorAccent));
    }

    private void setupSwitchesWithoutListeners(Switch mainSwitch, Switch wifiSwitch, Switch dataSwitch, 
                                             Switch backgroundSwitch, Switch vpnSwitch, AppInfo app) {
        mainSwitch.setOnCheckedChangeListener(null);
        wifiSwitch.setOnCheckedChangeListener(null);
        dataSwitch.setOnCheckedChangeListener(null);
        backgroundSwitch.setOnCheckedChangeListener(null);
        vpnSwitch.setOnCheckedChangeListener(null);
        
        boolean allAllowed = !app.wifiBlocked && !app.dataBlocked && !app.backgroundBlocked && !app.vpnBlocked;
        mainSwitch.setChecked(allAllowed);
        wifiSwitch.setChecked(!app.wifiBlocked);
        dataSwitch.setChecked(!app.dataBlocked);
        backgroundSwitch.setChecked(!app.backgroundBlocked);
        vpnSwitch.setChecked(!app.vpnBlocked);
    }

    private void setSwitchesListenersEnabled(boolean enabled, Switch... switches) {
        for (Switch switchView : switches) {
            switchView.setOnCheckedChangeListener(null);
        }
    }

    private void setupIndividualSwitches(Switch wifiSwitch, Switch dataSwitch, Switch backgroundSwitch, 
                                       Switch vpnSwitch, Switch mainSwitch, AppInfo app) {
        wifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setWifiRestriction(app, !isChecked);
            app.wifiBlocked = !isChecked;
            updateMainSwitchState(mainSwitch, app);
        });
        
        dataSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setDataRestriction(app, !isChecked);
            app.dataBlocked = !isChecked;
            updateMainSwitchState(mainSwitch, app);
        });
        
        backgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setBackgroundRestriction(app, !isChecked);
            app.backgroundBlocked = !isChecked;
            updateMainSwitchState(mainSwitch, app);
        });
        
        vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setVpnRestriction(app, !isChecked);
            app.vpnBlocked = !isChecked;
            updateMainSwitchState(mainSwitch, app);
        });
    }

    private void updateMainSwitchState(Switch mainSwitch, AppInfo app) {
        boolean allAllowed = !app.wifiBlocked && !app.dataBlocked && !app.backgroundBlocked && !app.vpnBlocked;
        mainSwitch.setOnCheckedChangeListener(null);
        mainSwitch.setChecked(allAllowed);
        mainSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            blockAllInternet(app, !isChecked);
        });
    }

    private class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {

        private List<AppInfo> apps;

        public NetworkAdapter(List<AppInfo> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_network_app, parent, false);
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
                holder.appType.setBackgroundResource(R.drawable.bg_system_chip);
            } else {
                holder.appType.setText("USER");
                holder.appType.setBackgroundResource(R.drawable.bg_user_chip);
            }

            int blockedCount = 0;
            if (app.wifiBlocked) blockedCount++;
            if (app.dataBlocked) blockedCount++;
            if (app.backgroundBlocked) blockedCount++;
            if (app.vpnBlocked) blockedCount++;
            
            if (blockedCount > 0) {
                holder.status.setText(blockedCount + " BLOCKED");
                holder.status.setBackgroundResource(R.drawable.bg_blocked_chip);
            } else {
                holder.status.setText("ALL ALLOWED");
                holder.status.setBackgroundResource(R.drawable.bg_allowed_chip);
            }

            holder.itemView.setOnClickListener(v -> {
                showAdvancedOptions(app);
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView appIcon;
            TextView appName, packageName, appType, status;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                packageName = itemView.findViewById(R.id.packageName);
                appType = itemView.findViewById(R.id.appType);
                status = itemView.findViewById(R.id.status);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAllApps();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
    }

    private static class AppInfo {
        String name;
        String packageName;
        int uid;
        boolean isSystemApp;
        android.graphics.drawable.Drawable icon;
        boolean wifiBlocked;
        boolean dataBlocked;
        boolean backgroundBlocked;
        boolean vpnBlocked;
    }
}
