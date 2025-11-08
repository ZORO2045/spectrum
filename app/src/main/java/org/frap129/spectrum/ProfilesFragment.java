package org.frap129.spectrum;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.cardview.widget.CardView;
import android.widget.TextView;
import android.content.Intent;
import eu.chainfire.libsuperuser.Shell;
import java.util.List;
import java.util.Objects;
import static org.frap129.spectrum.Utils.KPM;
import static org.frap129.spectrum.Utils.getCustomDesc;
import static org.frap129.spectrum.Utils.kernelProp;
import static org.frap129.spectrum.Utils.kpmPropPath;
import static org.frap129.spectrum.Utils.listToString;
import static org.frap129.spectrum.Utils.profileProp;
import static org.frap129.spectrum.Utils.setProfile;

public class ProfilesFragment extends Fragment {

    private CardView oldCard;
    private List<String> suResult = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profiles, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Define CardViews
        final CardView card0 = view.findViewById(R.id.card0);
        final CardView card1 = view.findViewById(R.id.card1);
        final CardView card2 = view.findViewById(R.id.card2);
        final CardView card3 = view.findViewById(R.id.card3);

        // Get profile descriptions
        getDesc(view);
        
        // Highlight current profile
        initSelected(view);

        // Set click listeners
        card0.setOnClickListener(v -> cardClick(card0, 0));
        card1.setOnClickListener(v -> cardClick(card1, 1));
        card2.setOnClickListener(v -> cardClick(card2, 2));
        card3.setOnClickListener(v -> cardClick(card3, 3));
    }

    private void cardClick(CardView card, int profile) {
        if (oldCard != card) {
            if (oldCard != null) {
                // Reset old card color
                oldCard.setCardBackgroundColor(getResources().getColor(R.color.cardBackground));
            }
            
            // Set new card color based on profile
            int colorRes = getProfileColor(profile);
            card.setCardBackgroundColor(getResources().getColor(colorRes));
            
            setProfile(profile);
            oldCard = card;
        }
    }

    private int getProfileColor(int profile) {
        switch (profile) {
            case 0: return R.color.colorBalance;
            case 1: return R.color.colorPerformance;
            case 2: return R.color.colorBattery;
            case 3: return R.color.colorGaming;
            default: return R.color.cardBackground;
        }
    }

    private void initSelected(View view) {
        if (KPM) {
            suResult = Shell.SU.run(String.format("cat %s", Utils.kpmPath));
        } else {
            suResult = Shell.SU.run(String.format("getprop %s", profileProp));
        }

        if (suResult != null) {
            String result = listToString(suResult);
            CardView selectedCard = null;
            
            if (result.contains("0")) {
                selectedCard = view.findViewById(R.id.card0);
            } else if (result.contains("1")) {
                selectedCard = view.findViewById(R.id.card1);
            } else if (result.contains("2")) {
                selectedCard = view.findViewById(R.id.card2);
            } else if (result.contains("3")) {
                selectedCard = view.findViewById(R.id.card3);
            }
            
            if (selectedCard != null) {
                int profile = getProfileFromCard(selectedCard);
                int colorRes = getProfileColor(profile);
                selectedCard.setCardBackgroundColor(getResources().getColor(colorRes));
                oldCard = selectedCard;
            }
        }
    }

    private int getProfileFromCard(CardView card) {
        int id = card.getId();
        if (id == R.id.card0) return 0;
        if (id == R.id.card1) return 1;
        if (id == R.id.card2) return 2;
        if (id == R.id.card3) return 3;
        return 0;
    }

    private void getDesc(View view) {
        TextView desc0 = view.findViewById(R.id.desc0);
        TextView desc1 = view.findViewById(R.id.desc1);
        TextView desc2 = view.findViewById(R.id.desc2);
        TextView desc3 = view.findViewById(R.id.desc3);
        
        String kernel;
        if (KPM) {
            suResult = Shell.SU.run(String.format("cat %s", kpmPropPath));
        } else {
            suResult = Shell.SU.run(String.format("getprop %s", kernelProp));
        }
        
        kernel = listToString(suResult);
        if (kernel.isEmpty()) return;

        if (Utils.supportsCustomDesc()) {
            if (!Objects.equals(getCustomDesc("balance"), "fail")) 
                desc0.setText(getCustomDesc("balance"));
            if (!Objects.equals(getCustomDesc("performance"), "fail")) 
                desc1.setText(getCustomDesc("performance"));
            if (!Objects.equals(getCustomDesc("battery"), "fail")) 
                desc2.setText(getCustomDesc("battery"));
            if (!Objects.equals(getCustomDesc("gaming"), "fail")) 
                desc3.setText(getCustomDesc("gaming"));
        }
    }
}
