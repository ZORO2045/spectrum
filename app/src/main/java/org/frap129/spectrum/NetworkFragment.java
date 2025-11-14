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
                    appInfo.uid = packageInfo.uid;
                    
                    appInfo.wifiRestricted = false;
                    appInfo.dataRestricted = false;
                    appInfo.vpnRestricted = false;
                    appInfo.backgroundRestricted = false;

                    appList.add(appInfo);
                }
            }

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

    private void setWifiRestriction(AppInfo app, boolean restricted) {
        List<String> commands = new ArrayList<>();
        if (restricted) {
            commands.add("ndc firewall set_uid_rule mobile " + app.uid + " deny");
            commands.add("ndc firewall set_uid_rule wifi " + app.uid + " deny");
            commands.add("cmd appops set " + app.packageName + " WIFI_SCAN deny");
            Toast.makeText(requireContext(), "WiFi blocked for " + app.name, Toast.LENGTH_SHORT).show();
        } else {
            commands.add("ndc firewall set_uid_rule mobile " + app.uid + " allow");
            commands.add("ndc firewall set_uid_rule wifi " + app.uid + " allow");
            commands.add("cmd appops set " + app.packageName + " WIFI_SCAN allow");
            Toast.makeText(requireContext(), "WiFi allowed for " + app.name, Toast.LENGTH_SHORT).show();
        }
        Shell.SU.run(commands);
    }

    private void setDataRestriction(AppInfo app, boolean restricted) {
        List<String> commands = new ArrayList<>();
        if (restricted) {
            commands.add("ndc firewall set_uid_rule mobile " + app.uid + " deny");
            commands.add("cmd appops set " + app.packageName + " CHANGE_NETWORK_STATE deny");
            Toast.makeText(requireContext(), "Mobile data blocked for " + app.name, Toast.LENGTH_SHORT).show();
        } else {
            commands.add("ndc firewall set_uid_rule mobile " + app.uid + " allow");
            commands.add("cmd appops set " + app.packageName + " CHANGE_NETWORK_STATE allow");
            Toast.makeText(requireContext(), "Mobile data allowed for " + app.name, Toast.LENGTH_SHORT).show();
        }
        Shell.SU.run(commands);
    }

    private void setVpnRestriction(AppInfo app, boolean restricted) {
        List<String> commands = new ArrayList<>();
        if (restricted) {
            commands.add("ip rule add uidrange " + app.uid + "-" + app.uid + " lookup main");
            commands.add("cmd appops set " + app.packageName + " ACTIVITY_RECOGNITION deny");
            Toast.makeText(requireContext(), "VPN restricted for " + app.name, Toast.LENGTH_SHORT).show();
        } else {
            commands.add("ip rule del uidrange " + app.uid + "-" + app.uid + " lookup main");
            commands.add("cmd appops set " + app.packageName + " ACTIVITY_RECOGNITION allow");
            Toast.makeText(requireContext(), "VPN allowed for " + app.name, Toast.LENGTH_SHORT).show();
        }
        Shell.SU.run(commands);
    }

    private void setBackgroundRestriction(AppInfo app, boolean restricted) {
        List<String> commands = new ArrayList<>();
        if (restricted) {
            commands.add("cmd appops set " + app.packageName + " RUN_IN_BACKGROUND ignore");
            commands.add("cmd appops set " + app.packageName + " BACKGROUND_START deny");
            commands.add("cmd appops set " + app.packageName + " START_FOREGROUND deny");
            Toast.makeText(requireContext(), "Background data blocked for " + app.name, Toast.LENGTH_SHORT).show();
        } else {
            commands.add("cmd appops set " + app.packageName + " RUN_IN_BACKGROUND allow");
            commands.add("cmd appops set " + app.packageName + " BACKGROUND_START allow");
            commands.add("cmd appops set " + app.packageName + " START_FOREGROUND allow");
            Toast.makeText(requireContext(), "Background data allowed for " + app.name, Toast.LENGTH_SHORT).show();
        }
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

            holder.wifiSwitch.setChecked(!app.wifiRestricted);
            holder.dataSwitch.setChecked(!app.dataRestricted);
            holder.vpnSwitch.setChecked(!app.vpnRestricted);
            holder.backgroundSwitch.setChecked(!app.backgroundRestricted);

            holder.wifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.wifiRestricted = !isChecked;
                setWifiRestriction(app, !isChecked);
            });

            holder.dataSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.dataRestricted = !isChecked;
                setDataRestriction(app, !isChecked);
            });

            holder.vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.vpnRestricted = !isChecked;
                setVpnRestriction(app, !isChecked);
            });

            holder.backgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.backgroundRestricted = !isChecked;
                setBackgroundRestriction(app, !isChecked);
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
            .setMessage("Package: " + app.packageName + "\n" +
                       "UID: " + app.uid + "\n\n" +
                       "WiFi: " + (app.wifiRestricted ? "❌ Blocked" : "✅ Allowed") + "\n" +
                       "Mobile Data: " + (app.dataRestricted ? "❌ Blocked" : "✅ Allowed") + "\n" +
                       "VPN: " + (app.vpnRestricted ? "❌ Blocked" : "✅ Allowed") + "\n" +
                       "Background Data: " + (app.backgroundRestricted ? "❌ Blocked" : "✅ Allowed"))
            .setPositiveButton("OK", null)
            .show();
    }

    private static class AppInfo {
        String name;
        String packageName;
        int uid;
        android.graphics.drawable.Drawable icon;
        boolean wifiRestricted;
        boolean dataRestricted;
        boolean vpnRestricted;
        boolean backgroundRestricted;
    }
}
