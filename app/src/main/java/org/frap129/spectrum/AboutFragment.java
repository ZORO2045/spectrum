package org.frap129.spectrum;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AboutFragment extends Fragment {

    private Button githubButton, websiteButton;
    private TextView versionText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupClickListeners();
        loadVersionInfo();
    }

    private void initViews(View view) {
        githubButton = view.findViewById(R.id.githubButton);
        websiteButton = view.findViewById(R.id.websiteButton);
        versionText = view.findViewById(R.id.versionText);
    }

    private void setupClickListeners() {
        githubButton.setOnClickListener(v -> openGitHub());
        websiteButton.setOnClickListener(v -> openWebsite());
    }

    private void loadVersionInfo() {
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;
            versionText.setText("Version " + versionName);
        } catch (Exception e) {
            versionText.setText("Version 2.0");
        }
    }

    private void openGitHub() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/frap129/spectrum"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Cannot open GitHub", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWebsite() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/frap129"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Cannot open website", Toast.LENGTH_SHORT).show();
        }
    }
                }
