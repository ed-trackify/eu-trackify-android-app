package common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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

public class UserDistributorShipmentDetailTabCtrl extends LinearLayout {

	LinkedList<TabItem> Tabs = new LinkedList<TabItem>();

	SmartTabLayout viewPagerTab;
	ViewPager viewPager;

	public ShipmentsType LoadFromShipmentType;

	public UserDistributorShipmentDetailTabCtrl(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.ctrl_distributor_tabs_shipmentdetail, this);

		if (!this.isInEditMode()) {

			App.Object.userDistributorShipmentDetail = new UserDistributorShipmentDetail();
			Tabs.add(new TabItem(context.getString(R.string.tab_details), App.Object.userDistributorShipmentDetail, R.drawable.ic_tab_details));

			App.Object.userDistributorNotesFragment = new UserDistributorNotesFragment();
			Tabs.add(new TabItem(context.getString(R.string.tab_notes), App.Object.userDistributorNotesFragment, R.drawable.ic_tab_notes));

			App.Object.userDistributorPicturesFragment = new UserDistributorShipmentPicturesFragment();
			Tabs.add(new TabItem(context.getString(R.string.tab_pictures), App.Object.userDistributorPicturesFragment, R.drawable.ic_tab_pictures));

			App.Object.userDistributorSMSHistoryFragment = new UserDistributorSMSHistoryFragment();
			Tabs.add(new TabItem(context.getString(R.string.tab_sms), App.Object.userDistributorSMSHistoryFragment, R.drawable.ic_tab_sms));

			MyPagerAdapter adapter = new MyPagerAdapter(App.Object.getSupportFragmentManager());
			viewPager = (ViewPager) findViewById(R.id.viewpager2);
			viewPager.setOffscreenPageLimit(Tabs.size()); // **** IMPORTANT ****
			viewPager.setAdapter(adapter);

			viewPagerTab = (SmartTabLayout) findViewById(R.id.viewpagertab2);
			viewPagerTab.setViewPager(viewPager);
			setupTabIcons();
			viewPagerTab.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
				@Override
				public void onPageSelected(int position) {
					// Load SMS History only when the tab is selected
					if (position < Tabs.size() && 
					    Tabs.get(position).FragmentInstance instanceof UserDistributorSMSHistoryFragment) {
						UserDistributorSMSHistoryFragment smsFragment = (UserDistributorSMSHistoryFragment) Tabs.get(position).FragmentInstance;
						smsFragment.setUserVisibleHint(true);
					}
				}

				@Override
				public void onPageScrolled(int arg0, float arg1, int arg2) {

				}

				@Override
				public void onPageScrollStateChanged(int arg0) {

				}
			});

			View btnClose = findViewById(R.id.btnClose);
			btnClose.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Hide();
				}
			});
		}
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
				TextView tab = (TextView) viewPagerTab.getTabAt(i);
				if (tab != null) {
					tab.setText(title);
				}
				break;
			}
		}
	}
	
	public void UpdateTabCount(Fragment fragment, int count) {
		for (int i = 0; i < Tabs.size(); i++) {
			if (Tabs.get(i).FragmentInstance.equals(fragment)) {
				TextView tab = (TextView) viewPagerTab.getTabAt(i);
				if (tab != null) {
					TabItem tabItem = Tabs.get(i);
					String tabTitle = tabItem.Title;
					if (tabItem.Title.equals("Pictures")) {
						tabTitle = "Photos";
					} else if (tabItem.Title.equals("SMS")) {
						tabTitle = "SMS";
					}
					
					if (count > 0) {
						tab.setText(tabTitle + " (" + count + ")");
					} else {
						tab.setText(tabTitle);
					}
				}
				break;
			}
		}
	}

	public void Initialize() {
		SelectTab(App.Object.userDistributorShipmentDetail);
	}
	
	public void RefreshCurrentTab() {
		// Get the current tab position
		int currentPosition = viewPager.getCurrentItem();
		if (currentPosition >= 0 && currentPosition < Tabs.size()) {
			// Get the current fragment
			Fragment currentFragment = Tabs.get(currentPosition).FragmentInstance;
			
			// Refresh based on fragment type
			if (currentFragment == App.Object.userDistributorPicturesFragment) {
				App.Object.userDistributorPicturesFragment.Initialize();
			} else if (currentFragment == App.Object.userDistributorNotesFragment) {
				App.Object.userDistributorNotesFragment.Initialize();
			} else if (currentFragment == App.Object.userDistributorShipmentDetail) {
				App.Object.userDistributorShipmentDetail.Initialize();
			}
		}
	}
	
	public void RefreshAllTabs() {
		// Refresh all tabs to ensure data consistency
		if (App.Object.userDistributorShipmentDetail != null) {
			App.Object.userDistributorShipmentDetail.Initialize();
		}
		if (App.Object.userDistributorNotesFragment != null) {
			App.Object.userDistributorNotesFragment.Initialize();
		}
		if (App.Object.userDistributorPicturesFragment != null) {
			App.Object.userDistributorPicturesFragment.Initialize();
		}
		if (App.Object.userDistributorSMSHistoryFragment != null) {
			// Only refresh SMS if it's been loaded before
			if (App.Object.userDistributorSMSHistoryFragment.isAdded()) {
				App.Object.userDistributorSMSHistoryFragment.Initialize();
			}
		}
	}
	
	private void setupTabIcons() {
		// Simple icon setup for SmartTabLayout
		// We'll keep the tabs simple with just icons on the left
		for (int i = 0; i < Tabs.size(); i++) {
			TextView tab = (TextView) viewPagerTab.getTabAt(i);
			if (tab != null) {
				TabItem tabItem = Tabs.get(i);
				
				// Use shorter names to fit better
				String tabTitle = tabItem.Title;
				if (tabItem.Title.equals("Pictures")) {
					tabTitle = "Photos";
				} else if (tabItem.Title.equals("SMS")) {
					tabTitle = "SMS";
				}
				
				tab.setText(tabTitle);
				
				// Set icon with smaller size
				if (tabItem.IconResId != 0) {
					tab.setCompoundDrawablesWithIntrinsicBounds(tabItem.IconResId, 0, 0, 0);
					tab.setCompoundDrawablePadding(4);
				}
				
				// Adjust text size and padding
				tab.setTextSize(10);
				tab.setPadding(8, 4, 8, 4);
			}
		}
	}

	public boolean Show(ShipmentsType type) {
		LoadFromShipmentType = type;
		boolean isLoaded = viewPager != null;
		setVisibility(View.VISIBLE);
		return isLoaded;
	}

	public void Hide() {
		setVisibility(View.GONE);
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
