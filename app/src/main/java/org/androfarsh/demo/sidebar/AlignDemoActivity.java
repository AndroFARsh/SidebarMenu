package org.androfarsh.demo.sidebar;

import org.androfarsh.widget.SidebarLayout;
import org.androfarsh.widget.SidebarLayout.SidebarListener;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewSwitcher;

public class AlignDemoActivity extends BaseDemoActivity {
	public static final int TITLE = R.string.align_demo_name;

	private ViewSwitcher mSidebar;
	private SidebarLayout mRoot;
	private SidebarListener mSidebarListener = new SidebarListener() {
		
		@Override
		public void onSidebarOpened() {
			Toast.makeText(AlignDemoActivity.this, R.string.sidebar_opened, Toast.LENGTH_SHORT).show();
		}
		
		@Override
		public void onSidebarClosed() {
			Toast.makeText(AlignDemoActivity.this, R.string.sidebar_closed, Toast.LENGTH_SHORT).show();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.align_demo_screen);

		mRoot = (SidebarLayout) findViewById(R.id.root);
		mSidebar = (ViewSwitcher) findViewById(R.id.sidebar);

		final Spinner intepolator = (Spinner) findViewById(R.id.interpolator);
		intepolator.setAdapter(new InterpolatorAdapter(this));
		intepolator.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				final Interpolator interpolator = ((InterpolatorAdapter) parent
						.getAdapter()).getItem(position);
				mRoot.setSlideAnimationInterpolator(interpolator);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		final Spinner align = (Spinner) findViewById(R.id.align);
		align.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				switch (position) {
				case 0:
					mRoot.setAlign(SidebarLayout.LEFT);
					mSidebar.setDisplayedChild(0);
					break;
				case 1:
					mRoot.setAlign(SidebarLayout.TOP);
					mSidebar.setDisplayedChild(1);
					break;
				case 2:
					mRoot.setAlign(SidebarLayout.RIGHT);
					mSidebar.setDisplayedChild(0);
					break;
				case 3:
					mRoot.setAlign(SidebarLayout.BOTTOM);
					mSidebar.setDisplayedChild(1);
					break;
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		
		final TextView sidebarSizeTitle = (TextView)findViewById(R.id.sidebar_size_title);
		final SeekBar sidebarSize = (SeekBar)findViewById(R.id.sidebar_size);
		sidebarSize.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				updateSidebarSize(sidebarSizeTitle, seekBar);
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				sidebarSizeTitle.setText(getString(R.string.sidebar_size_pattern, progress));
			}
		});
		updateSidebarSize(sidebarSizeTitle, sidebarSize);
		
		final TextView sidebarOffsetTitle = (TextView)findViewById(R.id.sidebar_offset_title);
		final SeekBar sidebarOffset = (SeekBar)findViewById(R.id.sidebar_offset);
		sidebarOffset.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				updateSidebarOffset(sidebarOffsetTitle, seekBar);
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				sidebarOffsetTitle.setText(getString(R.string.sidebar_offset_pattern, progress));
			}
		});
		updateSidebarOffset(sidebarOffsetTitle, sidebarOffset);
		
		
		final CheckBox sidebarListener = (CheckBox)findViewById(R.id.sidebar_listener);
		sidebarListener.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mRoot.setListener(isChecked ? mSidebarListener : null);
			}
		});
		
		final CheckBox sidebarOnFreespace = (CheckBox)findViewById(R.id.sidebar_close_on_freespace);
		sidebarOnFreespace.setChecked(mRoot.isCloseOnFreespaceTap());
		sidebarOnFreespace.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mRoot.setCloseOnFreeSpaceTap(isChecked);
			}
		});
		
		final CheckBox sidebarDragToOpen = (CheckBox)findViewById(R.id.sidebar_allow_drag);
		sidebarDragToOpen.setChecked(mRoot.isAllowDrag());
		sidebarDragToOpen.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mRoot.setAllowDrag(isChecked);
			}
		});
	}

	private void updateSidebarSize(final TextView sidebarTitle,
			SeekBar seekBar) {
		mRoot.setSidebarSizeFraction(seekBar.getProgress() * 0.01f);
		sidebarTitle.setText(getString(R.string.sidebar_size_pattern, seekBar.getProgress()));
	}

	private void updateSidebarOffset(final TextView sidebarTitle,
			SeekBar seekBar) {
		mRoot.setSidebarOffsetFraction(seekBar.getProgress() * 0.01f);
		sidebarTitle.setText(getString(R.string.sidebar_offset_pattern, seekBar.getProgress()));
	}
	
	@Override
	protected SidebarLayout getSidebar() {
		return mRoot;
	}
	
	public void onToggleSidebar(View view){
		mRoot.toggleSidebar();
	}

	private static class InterpolatorAdapter extends BaseAdapter {
		private static Interpolator[] INTERPOLATORS = new Interpolator[] {
				new LinearInterpolator(),
				new AccelerateDecelerateInterpolator(),
				new AccelerateInterpolator(), new AnticipateInterpolator(),
				new AnticipateOvershootInterpolator(),
				new BounceInterpolator(), new DecelerateInterpolator(),
				new OvershootInterpolator() };

		private LayoutInflater mLayoutInflater;

		InterpolatorAdapter(Context context) {
			mLayoutInflater = LayoutInflater.from(context);
		}

		@Override
		public int getCount() {
			return INTERPOLATORS.length;
		}

		@Override
		public Interpolator getItem(int position) {
			return INTERPOLATORS[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getDropDownView(int position, View convertView,
				ViewGroup parent) {
			if (convertView == null) {
				convertView = mLayoutInflater.inflate(
						android.R.layout.simple_spinner_dropdown_item, parent,
						false);
			}
			((TextView) convertView).setText(getItem(position).getClass()
					.getSimpleName());
			return convertView;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = mLayoutInflater.inflate(
						android.R.layout.simple_spinner_item, parent, false);
			}
			((TextView) convertView).setText(getItem(position).getClass()
					.getSimpleName());
			return convertView;
		}
	}
}