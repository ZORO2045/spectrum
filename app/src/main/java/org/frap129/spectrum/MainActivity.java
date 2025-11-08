package org.frap129.spectrum;

import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        
        // Setup ViewPager and Tabs
        setupViewPager();
        
        // Check for Spectrum Support
        if (!Utils.checkSupport(this)) {
            Utils.showNoSupportDialog(this);
            return;
        }

        // Ensure root access
        if (!Utils.checkSU()) {
            Utils.showNoRootDialog(this);
            return;
        }
    }

    private void setupViewPager() {
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Profiles");
                    break;
                case 1:
                    tab.setText("Monitor");
                    break;
                case 2:
                    tab.setText("CPU");
                    break;
                case 3:
                    tab.setText("Apps Manager");
                    break;
                case 4:
                    tab.setText("About");
                    break;
            }
        }).attach();
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(FragmentActivity fa) {
            super(fa);
        }
        
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new ProfilesFragment(); 
                case 1:
                    return new DashboardFragment();
                case 2:
                    return new CpuFragment();
                case 3:
                    return new AppsFragment();    
                case 4:
                    return new AboutFragment(); 
                default:
                    return new DashboardFragment();
            }
        }
        
        @Override
        public int getItemCount() {
            return 4;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.nav, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.custom_profile) {
            Intent intent = new Intent(this, ProfileLoaderActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
            }
