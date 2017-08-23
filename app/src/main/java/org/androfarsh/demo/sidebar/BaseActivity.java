package org.androfarsh.demo.sidebar;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

abstract class BaseActivity extends Activity {
	protected static final String TAG = BaseActivity.class.getSimpleName();
	private static final Uri GITHUB_URL = Uri.parse("https://github.com/AndroFARsh/SidebarMenu");

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.git:
			Intent intent = new Intent(Intent.ACTION_VIEW, GITHUB_URL);
			startActivity(intent);
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}
}
