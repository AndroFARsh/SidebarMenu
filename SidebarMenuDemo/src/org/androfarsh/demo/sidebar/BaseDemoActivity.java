package org.androfarsh.demo.sidebar;

import java.lang.reflect.Field;

import org.androfarsh.widget.SidebarLayout;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

abstract class BaseDemoActivity extends BaseActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle();
	}
	
	private void setTitle() {
		try {
			final Field title = getClass().getField("TITLE");
			setTitle(getString(R.string.demo_title_pattern, getString(R.string.app_name), getString(title.getInt(getClass()))));
		} catch (IllegalArgumentException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (NoSuchFieldException e) {
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.demo_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null){
			boolean sidebarOverContent = getSidebar().getSidebarHierarchy() == SidebarLayout.OVER_CONTENT;
			if (sidebarOverContent){
				menu.findItem(R.id.sidebar_mode).setVisible(false).setChecked(getSidebar().getSidebarMode() == SidebarLayout.SLIDE);
				menu.findItem(R.id.content_mode).setVisible(true).setChecked(getSidebar().getContentMode() == SidebarLayout.SLIDE);
			}else{
				menu.findItem(R.id.sidebar_mode).setVisible(true).setChecked(getSidebar().getSidebarMode() == SidebarLayout.SLIDE);
				menu.findItem(R.id.content_mode).setVisible(false).setChecked(getSidebar().getContentMode() == SidebarLayout.SLIDE);
			}
			
			menu.findItem(R.id.sidebar_hierarchy).setChecked(sidebarOverContent);
			menu.findItem(R.id.debug_mode).setChecked(getSidebar().isDebugMode());
		}
		return super.onMenuOpened(featureId, menu);
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.debug_mode:
			item.setChecked(!item.isChecked());
			getSidebar().setDebugMode(item.isChecked());
			break;
		case R.id.sidebar_mode:
			item.setChecked(!item.isChecked());
			getSidebar().setSidebarMode(item.isChecked() ? SidebarLayout.SLIDE : SidebarLayout.FIXED);
			break;
		case R.id.content_mode:
			item.setChecked(!item.isChecked());
			getSidebar().setContentMode(item.isChecked() ? SidebarLayout.SLIDE : SidebarLayout.FIXED);
			break;	
		case R.id.sidebar_hierarchy:
			item.setChecked(!item.isChecked());
			getSidebar().setSidebarHierarchy(item.isChecked() ? SidebarLayout.OVER_CONTENT : SidebarLayout.UNDER_CONTENT);
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	@Override
	public void onBackPressed() {
		final SidebarLayout sidebar = getSidebar();
		if (sidebar != null && sidebar.isOpened()){
			sidebar.closeSidebar();
			return;
		}
		super.onBackPressed();
	};
	
	protected abstract SidebarLayout getSidebar();
}
