package common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import eu.trackify.net.R;
import com.ogaclejapan.smarttablayout.SmartTabLayout;

import java.util.LinkedList;

import common.UserDistributorShipmentsFragment.ShipmentsType;

public class UserDistributorTabCtrl extends LinearLayout {

    LinkedList<TabItem> Tabs = new LinkedList<TabItem>();

    SmartTabLayout viewPagerTab;
    ViewPager viewPager;

    public UserDistributorTabCtrl(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.ctrl_distributor_tabs, this);

        if (!this.isInEditMode()) {

            App.Object.userDistributorMyShipmentsFragment = new Draggable_UserDistributorShipmentsFragment();
            Tabs.add(new TabItem(context.getString(R.string.tab_deliveries), App.Object.userDistributorMyShipmentsFragment, R.drawable.ic_tab_deliveries));

            App.Object.userDistributorReconcileShipmentsFragment = new UserDistributorShipmentsFragment(ShipmentsType.ReconcileShipments);
            Tabs.add(new TabItem(context.getString(R.string.tab_cod), App.Object.userDistributorReconcileShipmentsFragment, R.drawable.ic_tab_cod));

            // Hide Returns tab (keep fragment reference for backward compatibility)
            App.Object.userDistributorReturnShipmentsFragment = new UserDistributorShipmentsFragment(ShipmentsType.Returns);
            // Not adding Returns to Tabs list - effectively hiding it

            MyPagerAdapter adapter = new MyPagerAdapter(App.Object.getSupportFragmentManager());
            viewPager = (ViewPager) findViewById(R.id.viewpager);
            viewPager.setOffscreenPageLimit(Tabs.size()); // **** IMPORTANT ****
            viewPager.setAdapter(adapter);

            viewPagerTab = (SmartTabLayout) findViewById(R.id.viewpagertab);
            viewPagerTab.setViewPager(viewPager);
            setupTabIcons();
            viewPagerTab.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageSelected(int arg0) {

                }

                @Override
                public void onPageScrolled(int arg0, float arg1, int arg2) {

                }

                @Override
                public void onPageScrollStateChanged(int arg0) {

                }
            });

            SelectTab(App.Object.userDistributorMyShipmentsFragment);
        }
    }

    public boolean isVisible(Fragment fragment) {
        return Tabs.get(viewPager.getCurrentItem()).FragmentInstance.equals(fragment);
    }

    public void SelectTab(Fragment fragment) {
        int indx = -1;
        for (int i = 0; i < Tabs.size(); i++) {
            if (Tabs.get(i).FragmentInstance.equals(fragment)) {
                indx = i;
                break;
            }
        }
        viewPager.setCurrentItem(indx, true);
    }

    public void ChangeTabTitle(String title, Fragment fragment) {
        for (int i = 0; i < Tabs.size(); i++) {
            if (Tabs.get(i).FragmentInstance.equals(fragment)) {
                ((TextView) viewPagerTab.getTabAt(i)).setText(title);
                break;
            }
        }
    }
    
    private void setupTabIcons() {
        for (int i = 0; i < Tabs.size(); i++) {
            TextView tab = (TextView) viewPagerTab.getTabAt(i);
            if (tab != null && Tabs.get(i).IconResId != 0) {
                tab.setCompoundDrawablesWithIntrinsicBounds(Tabs.get(i).IconResId, 0, 0, 0);
                tab.setCompoundDrawablePadding(8);
            }
        }
    }

    public class MyPagerAdapter extends FragmentPagerAdapter {

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return Tabs.get(position).Title;
        }

        @Override
        public int getCount() {
            return Tabs.size();
        }

        @Override
        public Fragment getItem(int position) {
            return Tabs.get(position).FragmentInstance;
        }
    }

    private class TabItem {
        public String Title;
        public Fragment FragmentInstance;
        public int IconResId;

        public TabItem(String title, Fragment fragment) {
            Title = title;
            this.FragmentInstance = fragment;
            this.IconResId = 0;
        }
        
        public TabItem(String title, Fragment fragment, int iconResId) {
            Title = title;
            this.FragmentInstance = fragment;
            this.IconResId = iconResId;
        }
    }
}
