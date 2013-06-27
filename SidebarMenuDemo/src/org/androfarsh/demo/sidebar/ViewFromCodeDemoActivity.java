package org.androfarsh.demo.sidebar;

import org.androfarsh.widget.SidebarLayout;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ViewFromCodeDemoActivity extends BaseDemoActivity {
	public static final int TITLE = R.string.view_from_code_demo_name;
	
	private static final int[] WALLPAPER_DRAWABLE = new int[] { R.drawable.wp0,
			R.drawable.wp1, R.drawable.wp2 };
	private static final int[] WALLPAPER_TITLES = new int[] { R.string.wp0,
			R.string.wp1, R.string.wp2 };

	private SidebarLayout mRoot;
	private ListView mSidebar;
	private ImageView mContent;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		enableHome();
		
		mRoot = new SidebarLayout(this, R.layout.sidebar, R.layout.content);
		mSidebar = (ListView) mRoot.findViewById(R.id.sidebar);
		mContent = (ImageView) mRoot.findViewById(R.id.content);
		
		mSidebar.setAdapter(new SidebarAdapter());
		mSidebar.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				mContent.setImageResource(WALLPAPER_DRAWABLE[position]);
				mRoot.closeSidebar();
			}
		});
		
		setContentView(mRoot);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void enableHome() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
			ActionBar actionBar = getActionBar();
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			mRoot.toggleSidebar();
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}

	
	class SidebarAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return Math.min(WALLPAPER_DRAWABLE.length, WALLPAPER_TITLES.length);
		}

		@Override
		public Object getItem(int position) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				final TAG tag = new TAG();
				convertView = getLayoutInflater().inflate(
						R.layout.sidebar_item, parent, false);
				convertView.setTag(tag);

				tag.icon = (ImageView) convertView
						.findViewById(android.R.id.icon);
				tag.title = (TextView) convertView
						.findViewById(android.R.id.title);
			}
			final TAG tag = (TAG) convertView.getTag();
			tag.icon.setImageResource(WALLPAPER_DRAWABLE[position]);
			tag.title.setText(WALLPAPER_TITLES[position]);
			return convertView;
		}

	}

	static class TAG {
		ImageView icon;
		TextView title;
	}

	@Override
	protected SidebarLayout getSidebar() {
		return mRoot;
	}
}
