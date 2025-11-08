package org.frap129.spectrum;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class AppsFragment extends Fragment {

    private RecyclerView appsRecyclerView;
    private EditText searchEditText;
    private Chip filterAll, filterSystem, filterUser, filterDisabled;
    private Button disableSelectedBtn, enableSelectedBtn;

    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> filteredApps = new ArrayList<>();
    private AppsAdapter appsAdapter;
    private PackageManager packageManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupRecyclerView();
        loadApps();
        setupSearchAndFilters();
        setupActionButtons();
    }

    private void initViews(View view) {
        appsRecyclerView = view.findViewById(R.id.appsRecyclerView);
        searchEditText = view.findViewById(R.id.searchEditText);
        filterAll = view.findViewById(R.id.filterAll);
        filterSystem = view.findViewById(R.id.filterSystem);
        filterUser = view.findViewById(R.id.filterUser);
        filterDisabled = view.findViewById(R.id.filterDisabled);
        disableSelectedBtn = view.findViewById(R.id.disableSelectedBtn);
        enableSelectedBtn = view.findViewById(R.id.enableSelectedBtn);

        packageManager = requireContext().getPackageManager();
    }

    private void setupRecyclerView() {
        appsAdapter = new AppsAdapter();
        appsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        appsRecyclerView.setAdapter(appsAdapter);
    }

    private void loadApps() {
        allApps.clear();
        List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : packages) {
            AppInfo app = new AppInfo();
            app.packageName = appInfo.packageName;
            app.name = appInfo.loadLabel(packageManager).toString();
            app.icon = appInfo.loadIcon(packageManager);
            app.isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            app.isEnabled = appInfo.enabled;
            app.isSelected = false;

            allApps.add(app);
        }

        filteredApps.addAll(allApps);
        appsAdapter.notifyDataSetChanged();
    }

    private void setupSearchAndFilters() {
        // Search functionality
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Filter chips
        filterAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) filterApps();
        });

        filterSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) filterApps();
        });

        filterUser.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) filterApps();
        });

        filterDisabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) filterApps();
        });
    }

    private void setupActionButtons() {
        disableSelectedBtn.setOnClickListener(v -> disableSelectedApps());
        enableSelectedBtn.setOnClickListener(v -> enableSelectedApps());
    }

    private void filterApps() {
        filteredApps.clear();
        String query = searchEditText.getText().toString().toLowerCase();

        for (AppInfo app : allApps) {
            boolean matchesSearch = app.name.toLowerCase().contains(query) || 
                                  app.packageName.toLowerCase().contains(query);
            boolean matchesFilter = false;

            if (filterAll.isChecked()) {
                matchesFilter = true;
            } else if (filterSystem.isChecked() && app.isSystemApp) {
                matchesFilter = true;
            } else if (filterUser.isChecked() && !app.isSystemApp) {
                matchesFilter = true;
            } else if (filterDisabled.isChecked() && !app.isEnabled) {
                matchesFilter = true;
            }

            if (matchesSearch && matchesFilter) {
                filteredApps.add(app);
            }
        }

        appsAdapter.notifyDataSetChanged();
    }

    private void disableSelectedApps() {
        int disabledCount = 0;
        for (AppInfo app : filteredApps) {
            if (app.isSelected && app.isEnabled) {
                disableApp(app.packageName);
                app.isEnabled = false;
                disabledCount++;
            }
        }
        
        if (disabledCount > 0) {
            Toast.makeText(requireContext(), "Disabled " + disabledCount + " apps", Toast.LENGTH_SHORT).show();
            appsAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(requireContext(), "No apps selected or already disabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableSelectedApps() {
        int enabledCount = 0;
        for (AppInfo app : filteredApps) {
            if (app.isSelected && !app.isEnabled) {
                enableApp(app.packageName);
                app.isEnabled = true;
                enabledCount++;
            }
        }
        
        if (enabledCount > 0) {
            Toast.makeText(requireContext(), "Enabled " + enabledCount + " apps", Toast.LENGTH_SHORT).show();
            appsAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(requireContext(), "No apps selected or already enabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void disableApp(String packageName) {
        Shell.SU.run("pm disable-user --user 0 " + packageName);
    }

    private void enableApp(String packageName) {
        Shell.SU.run("pm enable " + packageName);
    }

    // App Info Model Class
    private static class AppInfo {
        String name;
        String packageName;
        android.graphics.drawable.Drawable icon;
        boolean isSystemApp;
        boolean isEnabled;
        boolean isSelected;
    }

    // RecyclerView Adapter
    private class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppViewHolder> {

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            return new AppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            AppInfo app = filteredApps.get(position);
            holder.bind(app);
        }

        @Override
        public int getItemCount() {
            return filteredApps.size();
        }

        class AppViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            ImageView appIcon;
            TextView appName, appPackage, appStatus;
            Button actionBtn;

            AppViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.appCheckBox);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                appPackage = itemView.findViewById(R.id.appPackage);
                appStatus = itemView.findViewById(R.id.appStatus);
                actionBtn = itemView.findViewById(R.id.appActionBtn);
            }

            void bind(AppInfo app) {
                appIcon.setImageDrawable(app.icon);
                appName.setText(app.name);
                appPackage.setText(app.packageName);
                
                if (app.isEnabled) {
                    appStatus.setText("ENABLED");
                    appStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_light));
                    actionBtn.setText("DISABLE");
                    actionBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(requireContext().getColor(android.R.color.holo_red_light)));
                } else {
                    appStatus.setText("DISABLED");
                    appStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_light));
                    actionBtn.setText("ENABLE");
                    actionBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(requireContext().getColor(android.R.color.holo_green_light)));
                }

                checkBox.setChecked(app.isSelected);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    app.isSelected = isChecked;
                });

                actionBtn.setOnClickListener(v -> {
                    if (app.isEnabled) {
                        disableApp(app.packageName);
                        app.isEnabled = false;
                        Toast.makeText(requireContext(), "Disabled: " + app.name, Toast.LENGTH_SHORT).show();
                    } else {
                        enableApp(app.packageName);
                        app.isEnabled = true;
                        Toast.makeText(requireContext(), "Enabled: " + app.name, Toast.LENGTH_SHORT).show();
                    }
                    notifyItemChanged(getAdapterPosition());
                });

                // Whole item click toggles selection
                itemView.setOnClickListener(v -> {
                    app.isSelected = !app.isSelected;
                    checkBox.setChecked(app.isSelected);
                });
            }
        }
    }
}
