package org.frap129.spectrum;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkPolicyManager;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class NetworkFragment extends Fragment {

    private RecyclerView appsRecyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private List<AppInfo> appList = new ArrayList<>();
    private NetworkAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_network, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadApps();
    }

    private void initViews(View view) {
        appsRecyclerView = view.findViewById(R.id.appsRecyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyText = view.findViewById(R.id.emptyText);

        appsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NetworkAdapter(appList);
        appsRecyclerView.setAdapter(adapter);
    }

    private void loadApps() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        new Thread(() -> {
            appList.clear();
            PackageManager pm = requireContext().getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo packageInfo : packages) {
                if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    AppInfo appInfo = new AppInfo();
                    appInfo.packageName = packageInfo.packageName;
                    appInfo.name = packageInfo.loadLabel(pm).toString();
                    appInfo.icon = packageInfo.loadIcon(pm);
                    
                    // Get current network restrictions
                    appInfo.wifiRestricted = isWifiRestricted(appInfo.packageName);
                    appInfo.dataRestricted = isDataRestricted(appInfo.packageName);
                    appInfo.vpnRestricted = isVpnRestricted(appInfo.packageName);
                    appInfo.backgroundRestricted = isBackgroundRestricted(appInfo.packageName);

                    appList.add(appInfo);
                }
            }

            // Sort alphabetically
            Collections.sort(appList, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));

            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                if (appList.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private boolean isWifiRestricted(String packageName) {
        List<String> result = Shell.SU.run("iptables -L | grep " + packageName);
        return result != null && !result.isEmpty();
    }

    private boolean isDataRestricted(String packageName) {
        List<String> result = Shell.SU.run("iptables -L | grep " + packageName);
        return result != null && !result.isEmpty();
    }

    private boolean isVpnRestricted(String packageName) {
        // Check if app is blocked from using VPN
        List<String> result = Shell.SU.run("ip rule show | grep " + packageName);
        return result != null && !result.isEmpty();
    }

    private boolean isBackgroundRestricted(String packageName) {
        try {
            NetworkStatsManager statsManager = (NetworkStatsManager) requireContext().getSystemService(Context.NETWORK_STATS_SERVICE);
            // Implementation for background data restriction check
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void setWifiRestriction(String packageName, boolean restricted) {
        if (restricted) {
            Shell.SU.run(
                "iptables -A OUTPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP",
                "iptables -A INPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP"
            );
        } else {
            Shell.SU.run(
                "iptables -D OUTPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP",
                "iptables -D INPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP"
            );
        }
    }

    private void setDataRestriction(String packageName, boolean restricted) {
        if (restricted) {
            Shell.SU.run(
                "iptables -A OUTPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP",
                "iptables -A INPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP"
            );
        } else {
            Shell.SU.run(
                "iptables -D OUTPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP",
                "iptables -D INPUT -m owner --uid-owner $(pm list packages -U " + packageName + " | cut -d: -f3) -j DROP"
            );
        }
    }

    private void setVpnRestriction(String packageName, boolean restricted) {
        if (restricted) {
            Shell.SU.run(
                "ip rule add uidrange $(pm list packages -U " + packageName + " | cut -d: -f3) lookup main"
            );
        } else {
            Shell.SU.run(
                "ip rule del uidrange $(pm list packages -U " + packageName + " | cut -d: -f3) lookup main"
            );
        }
    }

    private void setBackgroundRestriction(String packageName, boolean restricted) {
        if (restricted) {
            Shell.SU.run(
                "cmd appops set " + packageName + " RUN_IN_BACKGROUND ignore"
            );
        } else {
            Shell.SU.run(
                "cmd appops set " + packageName + " RUN_IN_BACKGROUND allow"
            );
        }
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

            holder.wifiSwitch.setChecked(!app.wifiRestricted);
            holder.dataSwitch.setChecked(!app.dataRestricted);
            holder.vpnSwitch.setChecked(!app.vpnRestricted);
            holder.backgroundSwitch.setChecked(!app.backgroundRestricted);

            holder.wifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setWifiRestriction(app.packageName, !isChecked);
                app.wifiRestricted = !isChecked;
            });

            holder.dataSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setDataRestriction(app.packageName, !isChecked);
                app.dataRestricted = !isChecked;
            });

            holder.vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setVpnRestriction(app.packageName, !isChecked);
                app.vpnRestricted = !isChecked;
            });

            holder.backgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setBackgroundRestriction(app.packageName, !isChecked);
                app.backgroundRestricted = !isChecked;
            });

            holder.itemView.setOnLongClickListener(v -> {
                showAppDetails(app);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView appIcon;
            TextView appName;
            Switch wifiSwitch, dataSwitch, vpnSwitch, backgroundSwitch;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                wifiSwitch = itemView.findViewById(R.id.wifiSwitch);
                dataSwitch = itemView.findViewById(R.id.dataSwitch);
                vpnSwitch = itemView.findViewById(R.id.vpnSwitch);
                backgroundSwitch = itemView.findViewById(R.id.backgroundSwitch);
            }
        }
    }

    private void showAppDetails(AppInfo app) {
        new AlertDialog.Builder(requireContext(), R.style.SpectrumDialogTheme)
            .setTitle(app.name)
            .setMessage("Package: " + app.packageName + "\n\n" +
                       "WiFi: " + (app.wifiRestricted ? "Blocked" : "Allowed") + "\n" +
                       "Mobile Data: " + (app.dataRestricted ? "Blocked" : "Allowed") + "\n" +
                       "VPN: " + (app.vpnRestricted ? "Blocked" : "Allowed") + "\n" +
                       "Background Data: " + (app.backgroundRestricted ? "Blocked" : "Allowed"))
            .setPositiveButton("OK", null)
            .show();
    }

    private static class AppInfo {
        String name;
        String packageName;
        android.graphics.drawable.Drawable icon;
        boolean wifiRestricted;
        boolean dataRestricted;
        boolean vpnRestricted;
        boolean backgroundRestricted;
    }
}
