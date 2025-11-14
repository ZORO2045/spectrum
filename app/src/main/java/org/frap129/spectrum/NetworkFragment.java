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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
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
        loadAllApps();
    }

    private void initViews(View view) {
        appsRecyclerView = view.findViewById(R.id.appsRecyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyText = view.findViewById(R.id.emptyText);

        appsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NetworkAdapter(appList);
        appsRecyclerView.setAdapter(adapter);
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

            Collections.sort(appList, (o1, o2) -> {
                if (o1.isSystemApp != o2.isSystemApp) {
                    return o1.isSystemApp ? 1 : -1;
                }
                return o1.name.compareToIgnoreCase(o2.name);
            });

            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                if (appList.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                }
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
            
            Toast.makeText(requireContext(), "Internet blocked for " + app.name, Toast.LENGTH_SHORT).show();
        } else {
            commands.add("iptables -D OUTPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("iptables -D INPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -D OUTPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip6tables -D INPUT -m owner --uid-owner " + app.uid + " -j DROP");
            commands.add("ip rule del from all uidrange " + app.uid + "-" + app.uid + " lookup main");
            commands.add("cmd appops set " + app.packageName + " RUN_IN_BACKGROUND allow");
            commands.add("cmd appops set " + app.packageName + " BACKGROUND_START allow");
            
            Toast.makeText(requireContext(), "Internet allowed for " + app.name, Toast.LENGTH_SHORT).show();
        }
        
        Shell.SU.run(commands);
    }

    private void createFirewallChains() {
        List<String> commands = new ArrayList<>();
        commands.add("iptables -N SPECTRUM_FW 2>/dev/null || iptables -F SPECTRUM_FW");
        commands.add("ip6tables -N SPECTRUM_FW 2>/dev/null || ip6tables -F SPECTRUM_FW");
        Shell.SU.run(commands);
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
            
            if (app.isSystemApp) {
                holder.appName.setTextColor(0xFFB399FF);
                holder.systemIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.appName.setTextColor(0xFFFFFFFF);
                holder.systemIndicator.setVisibility(View.GONE);
            }

            holder.blockSwitch.setChecked(!app.internetBlocked);

            holder.blockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.internetBlocked = !isChecked;
                blockInternetCompletely(app, !isChecked);
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
            TextView appName, systemIndicator;
            Switch blockSwitch;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                systemIndicator = itemView.findViewById(R.id.systemIndicator);
                blockSwitch = itemView.findViewById(R.id.blockSwitch);
            }
        }
    }

    private void showAppDetails(AppInfo app) {
        String status = app.internetBlocked ? "COMPLETELY BLOCKED" : "ALLOWED";
        String type = app.isSystemApp ? "System App" : "User App";
        
        new AlertDialog.Builder(requireContext(), R.style.SpectrumDialogTheme)
            .setTitle(app.name)
            .setMessage(
                "Package: " + app.packageName + "\n" +
                "UID: " + app.uid + "\n" +
                "Type: " + type + "\n\n" +
                "Internet Access: " + status
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Refresh", (dialog, which) -> loadAllApps())
            .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        createFirewallChains();
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
