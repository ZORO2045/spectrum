package org.frap129.spectrum;

import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.util.Log;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        setSupportActionBar(findViewById(R.id.toolbar));
        
        setupViewPager();
        
        try {
            if (!Utils.checkSupport(this)) {
                Utils.showNoSupportDialog(this);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking Spectrum support", e);
            showCompatibilityWarning();
        }

        try {
            if (!Utils.checkSU()) {
                Utils.showNoRootDialog(this);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking root access", e);
            showRootWarning();
        }
    }

    private void setupViewPager() {
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        
        viewPager.setOffscreenPageLimit(5);
        
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Profiles");
                    break;
                case 1:
                    tab.setText("Disk Info");
                    break;
                case 2:
                    tab.setText("CPU");
                    break;
                case 3:
                    tab.setText("Apps Manager");
                    break;
                case 4:
                    tab.setText("Kill Camera");
                    break;   
                case 5:
                    tab.setText("About");
                    break;
            }
        }).attach();
        
        new Handler().postDelayed(() -> {
            try {
                viewPager.setCurrentItem(0, false);
            } catch (Exception e) {
                Log.e(TAG, "Error setting initial page", e);
            }
        }, 50);
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(FragmentActivity fa) {
            super(fa);
        }
        
        @Override
        public Fragment createFragment(int position) {
            try {
                switch (position) {
                    case 0:
                        return new ProfilesFragment(); 
                    case 1:
                        return new PartitionsFragment();
                    case 2:
                        return new CpuFragment();
                    case 3:
                        return new AppsFragment();
                    case 4:
                        return new KillCameraFragment(); 
                    case 5:
                        return new AboutFragment(); 
                    default:
                        return new DashboardFragment();
                }
            } catch (Exception e) {
                Log.e("ViewPagerAdapter", "Error creating fragment for position: " + position, e);
                return new DashboardFragment();
            }
        }
        
        @Override
        public int getItemCount() {
            return 6;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            getMenuInflater().inflate(R.menu.nav, menu);
            
            for(int i = 0; i < menu.size(); i++) {
                MenuItem menuItem = menu.getItem(i);
                SpannableString spannable = new SpannableString(menuItem.getTitle());
                spannable.setSpan(new ForegroundColorSpan(Color.WHITE), 0, spannable.length(), 0);
                menuItem.setTitle(spannable);
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error creating options menu", e);
            return false;
        }
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (menu != null) {
            if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
                try {
                    Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            if (item.getItemId() == R.id.custom_profile) {
                Intent intent = new Intent(this, ProfileLoaderActivity.class);
                startActivity(intent);
                return true;
            }
            return super.onOptionsItemSelected(item);
        } catch (Exception e) {
            Log.e(TAG, "Error handling menu item selection", e);
            return false;
        }
    }

    private void showCompatibilityWarning() {
        try {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Compatibility Notice")
                .setMessage("This device may not be fully compatible with Spectrum. Some features might not work as expected.")
                .setPositiveButton("Continue", null)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing compatibility warning", e);
        }
    }

    private void showRootWarning() {
        try {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Root Access")
                .setMessage("Root access is required for full functionality. Continuing with limited features.")
                .setPositiveButton("Continue", null)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing root warning", e);
        }
    }
                    }
