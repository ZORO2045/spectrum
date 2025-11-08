package org.frap129.spectrum;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;

public class KillCameraFragment extends Fragment {

    private Button btnKillCamera;
    private TextView tvStatus;
    private MaterialCardView statusCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_kill_camera, container, false);
        
        initViews(view);
        setupClickListeners();
        checkCameraStatus();
        
        return view;
    }

    private void initViews(View view) {
        btnKillCamera = view.findViewById(R.id.btnKillCamera);
        tvStatus = view.findViewById(R.id.tvCameraStatus);
        statusCard = view.findViewById(R.id.statusCard);
    }

    private void setupClickListeners() {
        btnKillCamera.setOnClickListener(v -> killCameraProcess());
    }

    private void checkCameraStatus() {
        // Check if camera services are running
        boolean isCameraRunning = Utils.isCameraServiceRunning(requireContext());
        
        if (isCameraRunning) {
            tvStatus.setText("Camera services are running");
            statusCard.setCardBackgroundColor(getResources().getColor(R.color.colorPerformance));
        } else {
            tvStatus.setText("Camera services are stopped");
            statusCard.setCardBackgroundColor(getResources().getColor(R.color.colorBalance));
        }
    }

    private void killCameraProcess() {
        if (Utils.killCameraServices(requireContext())) {
            tvStatus.setText("Camera services killed successfully");
            statusCard.setCardBackgroundColor(getResources().getColor(R.color.colorBalance));
            btnKillCamera.setEnabled(false);
            
            // Re-enable button after 5 seconds
            new android.os.Handler().postDelayed(() -> {
                btnKillCamera.setEnabled(true);
                checkCameraStatus();
            }, 5000);
        } else {
            tvStatus.setText("Failed to kill camera services");
            statusCard.setCardBackgroundColor(getResources().getColor(R.color.colorGaming));
        }
    }
}
