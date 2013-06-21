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

public class MainActivity extends BaseActivity {
	private static final int[] WALLPAPER_DRAWABLE = new int[]{R.drawable.wp0, R.drawable.wp1, R.drawable.wp2};
	private static final int[] WALLPAPER_TITLES = new int[]{R.string.wp0, R.string.wp1, R.string.wp2};
	
	private ListView mSidebar;
	private ImageView mContent;
	private SidebarLayout mRoot;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		enableHome();
		
		mRoot = (SidebarLayout) findViewById(R.id.root);
		mSidebar = (ListView) findViewById(R.id.sidebar);
		mContent = (ImageView) findViewById(R.id.content);
		
		mSidebar.setAdapter(new SidebarAdapter());
		mSidebar.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				mContent.setImageResource(WALLPAPER_DRAWABLE[position]);
				mRoot.closeSidebar();
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void enableHome() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB){
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
			if (convertView == null){
				final TAG tag = new TAG();
				convertView = getLayoutInflater().inflate(R.layout.sidebar_item, parent, false);
				convertView.setTag(tag);

				tag.icon = (ImageView) convertView.findViewById(android.R.id.icon);
				tag.title = (TextView) convertView.findViewById(android.R.id.title);
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
}
