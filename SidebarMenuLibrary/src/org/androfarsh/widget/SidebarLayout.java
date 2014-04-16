/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authored by Anton Kuhlevskyi <anton.kuhleskiy@gmail.com>
 */
package org.androfarsh.widget;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

public class SidebarLayout extends ViewGroup {
	private static final String RES_TYPE_LAYOUT = "layout";
	private static final String RES_TYPE_ID = "id";
	private static final int SNAP_VELOCITY = 1000;
	private static final int OFFSET = 50;

	public static final int FIXED = 0;
	public static final int SLIDE = 1;

	public static final int OVER_CONTENT = 1;
	public static final int UNDER_CONTENT = 0;

	public static final int LEFT = 1;
	public static final int RIGHT = 2;
	public static final int TOP = 4;
	public static final int BOTTOM = 8;

	private static final int VERTICAL_MASK = TOP | BOTTOM;
	private static final int RIGHT_BOTTOM_MASK = BOTTOM | RIGHT;
	private static final float TOGLE_KOEF = 0.3f;
	private static final float SIDEBAR_SIZE = 0.8f;
	private static final int UNKNOWN = -1;
	private static final int DURATION = 300;

	private final Rect mDragRect = new Rect();

	private boolean mOpened;

	private boolean mToggling;

	private int mDuration = DURATION;
	private Interpolator mInterpolator = new LinearInterpolator();

	private ViewHolder mContent;
	private ViewHolder mSidebar;

	private int mSidebarHeight;
	private int mSidebarWidth;
	private int mOffset;
	
	private int mToggle;
	private float mToggleFactor;

	private OpenListener mOpenListener;
	private CloseListener mCloseListener;
	private SidebarListener mSidebarListener;

	private boolean mSliding;
	private int mPrevX;
	private int mPrevY;
	private int mDelta;
	private VelocityTracker mVelocityTracker;
	private int mAlign = LEFT;
	private SizeResolver mOffsetResolver = new SizeResolver(0,
			TypedValue.TYPE_DIMENSION);
	private SizeResolver mSizeResolver = new SizeResolver(SIDEBAR_SIZE,
			TypedValue.TYPE_FRACTION);
	private int mDragOffsetContent = OFFSET;
	private int mDragOffsetSidebar;
	private boolean mDebugMode;
	private float mMaximumFlingVelocity = 2 * SNAP_VELOCITY;

	private int mSidebarMode = FIXED;
	private int mContentMode = SLIDE;
	private int mSidebarHierarchy = UNDER_CONTENT;

	private boolean mInitialized;
	private boolean mAttachToWindow;
	private boolean mAllowDrag = true;
	private Rect mContentRect = new Rect();
	private Rect mSidebarRect = new Rect();
	private boolean mCloseOnFreeSpaceTap;

	static class ViewHolder {
		public ViewHolder(Context context) {
			view = new FrameLayout(context);
			view.setBackgroundColor(Color.TRANSPARENT);
		}

		final FrameLayout view;
		int id = View.NO_ID;
		BitmapDrawable viewDrawable;
		
		@SuppressWarnings("deprecation")
		void createDrawingCache() {
			BitmapDrawable drawable = null;
			view.buildDrawingCache();
			final Bitmap bitmap = view.getDrawingCache();
			if (bitmap != null) {
				drawable = new BitmapDrawable(Bitmap.createBitmap(bitmap));
				bitmap.recycle();
			}
			view.destroyDrawingCache();
			
			if (viewDrawable != null && viewDrawable.getBitmap() != null){
				viewDrawable.getBitmap().recycle();
			}
			viewDrawable = drawable;
		}
		
		void recycleDrawingCache() {
			if (viewDrawable != null && viewDrawable.getBitmap() != null){
				viewDrawable.getBitmap().recycle();
			}
			viewDrawable = null;
		}
	}

	static class SizeResolver {
		private final float size;
		private final int type;

		SizeResolver(float size, int type) {
			this.size = size;
			this.type = type;
		}

		int resolveSize(int measuredSize) {
			switch (type) {
			case TypedValue.TYPE_FRACTION:
				return (int) (measuredSize * size);
			default:
				return (int) size;
			}
		}
		
		int resolveSpec(int measuredSpec) {
			switch (type) {
			case TypedValue.TYPE_FRACTION:
				return MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(measuredSpec)*size), MeasureSpec.getMode(measuredSpec));
			case TypedValue.TYPE_INT_DEC:
				return MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(measuredSpec), MeasureSpec.AT_MOST);
			default:
				return MeasureSpec.makeMeasureSpec((int) size, MeasureSpec.getMode(measuredSpec));
			}
		}
		
		LayoutParams resolveLayout(int align){
			switch (type) {
			case TypedValue.TYPE_INT_DEC:
				boolean v = (align & VERTICAL_MASK) > 0;
				return new LayoutParams(!v ? LayoutParams.MATCH_PARENT : (int)size, v ? LayoutParams.MATCH_PARENT : (int)size);
			default:
				return null;
			}
		}
	}

	public SidebarLayout(Context context) {
		this(context, UNKNOWN, UNKNOWN);	
	}
	
	public SidebarLayout(Context context, int sidebarLayoutRes,
			int contentLayoutRes) {
		super(context);

		mSidebar = resolveReference(sidebarLayoutRes, mSizeResolver.resolveLayout(mAlign));
		mContent = resolveReference(contentLayoutRes, null);

		init();
	}

	public SidebarLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SidebarLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.SidebarLayout, defStyle, 0);

		mToggleFactor = a.getFraction(
				R.styleable.SidebarLayout_toggle_factor, 1, 1, TOGLE_KOEF);

		mSizeResolver = resolveFractalOrDimentionValue(a
				.peekValue(R.styleable.SidebarLayout_sidebar_size), SIDEBAR_SIZE, TypedValue.TYPE_FRACTION);

		mOffsetResolver = resolveFractalOrDimentionValue(a
				.peekValue(R.styleable.SidebarLayout_sidebar_offset), 0, TypedValue.TYPE_DIMENSION);
		
		mDragOffsetContent = a.getDimensionPixelOffset(R.styleable.SidebarLayout_drag_content_offset, OFFSET);
		
		mDragOffsetSidebar = a.getDimensionPixelOffset(R.styleable.SidebarLayout_drag_sidebar_offset, 0);
		
		mDuration = a.getInt(R.styleable.SidebarLayout_android_duration,
				DURATION);

		mAlign = a.getInt(R.styleable.SidebarLayout_sidebar_align, LEFT);

		mDebugMode = a.getBoolean(R.styleable.SidebarLayout_debug_mode, false);
		
		mAllowDrag = a.getBoolean(R.styleable.SidebarLayout_allow_drag, true);
		
		mAttachToWindow = a.getBoolean(R.styleable.SidebarLayout_attach_to_window, false);
		
		final int interpolatorId = a.getResourceId(
				R.styleable.SidebarLayout_android_interpolator, UNKNOWN);
		if (interpolatorId != UNKNOWN) {
			mInterpolator = AnimationUtils.loadInterpolator(context,
					interpolatorId);
		}

		mSidebar = resolveReference(a.getResourceId(
				R.styleable.SidebarLayout_sidebar, UNKNOWN), mSizeResolver.resolveLayout(mAlign));

		mContent = resolveReference(a.getResourceId(
				R.styleable.SidebarLayout_content, UNKNOWN), null);
		
		setCloseOnFreeSpaceTap(a.getBoolean(
				R.styleable.SidebarLayout_close_on_sidebar_freespace_tap, false));
		
		setSidebarHierarchy(a.getInt(R.styleable.SidebarLayout_sidebar_hierarchy, UNDER_CONTENT));
		
		setSidebarMode(a.getInt(R.styleable.SidebarLayout_sidebar_mode, FIXED));

		setContentMode(a.getInt(R.styleable.SidebarLayout_content_mode, SLIDE));
		
		a.recycle();
	}

	public boolean isCloseOnFreespaceTap(){
		return mCloseOnFreeSpaceTap;
	}
	
	public void setCloseOnFreeSpaceTap(boolean enabled){
		mCloseOnFreeSpaceTap = enabled;
		if (enabled){
			mSidebar.view.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					if (isOpened()){
						closeSidebar();
					}
				}
			});
		} else {
			mSidebar.view.setOnClickListener(null);
		}
	}
	
	private void attachSidebarToWindow(Activity activity) {
		// get the window background
		TypedArray a = activity.getTheme().obtainStyledAttributes(
				new int[] { android.R.attr.windowBackground });
		int background = a.getResourceId(0, 0);
		a.recycle();

		ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
		ViewGroup decorChild = (ViewGroup) decor.getChildAt(0);
			
		if (this == decorChild){
			return;
		}
			
		if (getParent() != null){
			((ViewGroup)getParent()).removeView(this);
		}
			
		// save ActionBar themes that have transparent assets
		decorChild.setBackgroundResource(background);
		decor.removeView(decorChild);
		decor.addView(this);
			
		View content = getContent();
		setContent(decorChild);
			
		activity.setContentView(content);
	}
	
	private ViewHolder resolveReference(final int ref, LayoutParams lp) {
		final ViewHolder viewHolder = new ViewHolder(getContext());
		super.addView(viewHolder.view, UNKNOWN, lp != null ? lp : generateDefaultLayoutParams());
		
		try {
			if (RES_TYPE_LAYOUT.equals(getResources().getResourceTypeName(ref))) {
				final View view =  View.inflate(getContext(), ref, null);
				viewHolder.view.addView(view);
				viewHolder.id = view.getId();
				if (viewHolder.id == View.NO_ID) {
					viewHolder.id = viewHolder.view.hashCode();
					viewHolder.view.setId(viewHolder.id);
				}
			} else if (RES_TYPE_ID.equals(getResources().getResourceTypeName(ref))) {
				viewHolder.id = ref;
			}
		} catch (NotFoundException e){}
		return viewHolder;
	}
 	
	private SizeResolver resolveFractalOrDimentionValue(final TypedValue value, float defVal, int defType) {
		if (value == null) {
			return new SizeResolver(defVal, defType);
		}

		switch (value.type) {
		case TypedValue.TYPE_FRACTION:
			return new SizeResolver(TypedValue.complexToFraction(value.data, 1,
					1), value.type);

		case TypedValue.TYPE_DIMENSION:
			return new SizeResolver(TypedValue.complexToDimension(value.data,
					getResources().getDisplayMetrics()),
					value.type);
		case TypedValue.TYPE_INT_DEC:
			return new SizeResolver(value.data, value.type);	
		default:
			return new SizeResolver(defVal, defType);
		}
	}

	@Override
	public void onFinishInflate() {
		super.onFinishInflate();
		
		init();
	}

	private void init() {
		if (mInitialized) {
			return;
		}

		if (mSidebar == null) {
			throw new NullPointerException("no sidebar view");
		}

		if (mContent == null) {
			throw new NullPointerException("no content view");
		}
			
		mOpenListener = new OpenListener();
		mCloseListener = new CloseListener();
		
		resolveChildViewAttach();
		mInitialized = true;
	}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		
		if (mAttachToWindow && (getContext() instanceof Activity)){
			attachSidebarToWindow((Activity)getContext());
		}
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);
	}

	@Override
	public void onLayout(boolean changed, int l, int t, int r, int b) {
		resolveLayout(l, t, r, b);
	}

	private void resolveLayout(int l, int t, int r, int b) {
		/* the title bar assign top padding, drop it */
		int width = r - l;
		int height = b - t;

		mContentRect.set(0, 0, width, height);
		mSidebarRect.set(0, 0, mSidebarWidth, mSidebarHeight);
		switch (mAlign) {
		case BOTTOM:
			mContentRect.bottom -= mOffset;
			resolveContentLayout(mContentRect, -mSidebarHeight + mOffset);
			if (getSidebarMode() == FIXED) {
				mSidebarRect.offsetTo(0, height - mSidebarHeight);
			} else {
				mSidebarRect.offsetTo(0, mContentRect.bottom);
			}

			break;
		case TOP:
			mContentRect.top += mOffset;
			resolveContentLayout(mContentRect, mSidebarHeight - mOffset);
			if (getSidebarMode() == FIXED) {
				mSidebarRect.offsetTo(0, 0);
			} else {
				mSidebarRect.offsetTo(0, mContentRect.top - mSidebarHeight);
			}
			break;
		case RIGHT:
			mContentRect.right -= mOffset;
			resolveContentLayout(mContentRect, -mSidebarWidth + mOffset);
			if (getSidebarMode() == FIXED) {
				mSidebarRect.offsetTo(width - mSidebarWidth, 0);
			} else {
				mSidebarRect.offsetTo(mContentRect.right, 0);
			}
			
			break;
		case LEFT:
		default:
			mContentRect.left += mOffset;
			resolveContentLayout(mContentRect, mSidebarWidth - mOffset);
			if (getSidebarMode() == FIXED) {
				mSidebarRect.offsetTo(0, 0);
			} else if (getContentMode() != FIXED) {
				mSidebarRect.offsetTo(mContentRect.left - mSidebarWidth, 0);
			} else {
				resolveSidebarLayout(mSidebarRect, mSidebarWidth - mOffset);
			}
			break;
		}

		if (mSidebar.view.getVisibility() != View.GONE) {
			mSidebar.view.layout(mSidebarRect.left, mSidebarRect.top,
					mSidebarRect.right, mSidebarRect.bottom);
		}
		if (mContent.view.getVisibility() != View.GONE) {
			mContent.view.layout(mContentRect.left, mContentRect.top,
					mContentRect.right, mContentRect.bottom);
		}

		updateDragRect(mContentRect, mSidebarRect);
	}

	private void updateDragRect(Rect contentRect, Rect sidebarRect) {
		if (getContentMode() == SLIDE) {
			if ((mAlign & VERTICAL_MASK) > 0) {
				mDragRect.set(contentRect.left,
						(mAlign == TOP ? contentRect.top - mDragOffsetSidebar
								: contentRect.bottom - mDragOffsetContent),
						contentRect.right, (mAlign == TOP ? contentRect.top
								+ mDragOffsetContent : contentRect.bottom
								+ mDragOffsetSidebar));
			} else {
				mDragRect.set((mAlign == LEFT ? contentRect.left
						- mDragOffsetSidebar : contentRect.right - mDragOffsetContent),
						contentRect.top, (mAlign == LEFT ? contentRect.left
								+ mDragOffsetContent : contentRect.right
								+ mDragOffsetSidebar), contentRect.bottom);
			}
		} else {
			if ((mAlign & VERTICAL_MASK) > 0) {
				mDragRect
						.set(sidebarRect.left,
								(mAlign == TOP ? sidebarRect.bottom - mDragOffsetSidebar
										: sidebarRect.top - mDragOffsetContent),
										sidebarRect.right, (mAlign == TOP ? sidebarRect.bottom
										+ mDragOffsetContent : sidebarRect.top
										+ mDragOffsetSidebar));
			} else {
				mDragRect.set((mAlign == LEFT ? sidebarRect.right - mDragOffsetSidebar
						: sidebarRect.left - mDragOffsetContent), sidebarRect.top,
						(mAlign == LEFT ? sidebarRect.right + mDragOffsetContent
								: sidebarRect.left + mDragOffsetSidebar), sidebarRect.bottom);
			}
		}
	}

	private void resolveSidebarLayout(Rect result,
			int sidebarSize) {
		if (getSidebarMode() != FIXED) {
			int offcet = -sidebarSize;
			if (mSliding) {
				offcet = -(sidebarSize - Math.abs(mDelta));
			} else if (mOpened) {
				offcet = 0;
			}

			if ((mAlign & VERTICAL_MASK) > 0) {
				result.offset(0, offcet);
			} else {
				result.offset(offcet, 0);
			}
		}
	}
	
	private void resolveContentLayout(Rect result,
			int sidebarSize) {
		if (getContentMode() != FIXED) {
			int offcet = 0;
			if (mSliding) {
				offcet = mDelta;
			} else if (mOpened) {
				offcet = sidebarSize;
			}

			if ((mAlign & VERTICAL_MASK) > 0) {
				result.offset(0, offcet);
			} else {
				result.offset(offcet, 0);
			}
		}
	}

	@Override
	public void onMeasure(int w, int h) {
		super.onMeasure(w, h);
		
		final boolean v = (mAlign & VERTICAL_MASK) > 0;
		mOffset = mOffsetResolver.resolveSize(v ? getMeasuredHeight() : getMeasuredWidth());
		
		super.measureChildren(w, h);
		
		mSidebarWidth = mSidebar.view.getMeasuredWidth();
		mSidebarHeight = mSidebar.view.getMeasuredHeight();
	
		mToggle = (int) (mToggleFactor * getSidebarSize());
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		if (mDebugMode) {
			final Paint paint = new Paint();
			paint.setColor(0x6600cc00);
			canvas.drawRect(mDragRect, paint);
		}
	}
	
	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		if (child == mContent.view && mOpened && !mSliding && !mToggling) {
			return drawChildDrawable(mContent, mContentRect, canvas);
		} else {
			return super.drawChild(canvas, child, drawingTime);
		}
	}

	private boolean drawChildDrawable(ViewHolder holder, Rect rect, Canvas canvas) {
		final int saveCount = canvas.getSaveCount();
		canvas.save();

		if ((holder.viewDrawable == null) || holder.viewDrawable.getBitmap().isRecycled()) {
			holder.createDrawingCache();
		}
		
		canvas.clipRect(rect);
		
		holder.viewDrawable.setBounds(rect);
		holder.viewDrawable.draw(canvas);

		canvas.restoreToCount(saveCount);
		postInvalidate();
		return true;
	}

	@Override
	protected void measureChild(View child, int parentWSpec, int parentHSpec) {
		if (child == mSidebar.view) {
			if ((mAlign & VERTICAL_MASK) > 0) {
				parentHSpec = mSizeResolver.resolveSpec(parentHSpec);
			} else {
				parentWSpec = mSizeResolver.resolveSpec(parentWSpec);
			}
		} else if (child == mContent.view){
			if ((mAlign & VERTICAL_MASK) > 0) {
				parentHSpec = MeasureSpec.makeMeasureSpec(
						MeasureSpec.getSize(parentHSpec)-mOffset,
						MeasureSpec.getMode(parentHSpec));
			}else{
				parentWSpec = MeasureSpec.makeMeasureSpec(
						MeasureSpec.getSize(parentWSpec)-mOffset,
						MeasureSpec.getMode(parentWSpec));
			}
			
		}
		super.measureChild(child, parentWSpec, parentHSpec);
	}	
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (!mAllowDrag){
			return super.onInterceptTouchEvent(ev);
		}
		
		final int action = ev.getAction();
		final int x = (int) ev.getX();
		final int y = (int) ev.getY();

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}

		mVelocityTracker.addMovement(ev);

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			if (!mSliding && mDragRect.contains(x, y)) {
				mSliding = true;
				
				mPrevX = x;
				mPrevY = y;

				if (!mOpened) {
					mDelta = 0;
				} else {
					mDelta = ((mAlign & RIGHT_BOTTOM_MASK) > 0 ? -1 : 1)
							* getSidebarSize();
				}

				return true;
			}
			break;
		case MotionEvent.ACTION_MOVE:
			if (mSliding) {
				int newDelta;
				if ((mAlign & VERTICAL_MASK) > 0) {
					newDelta = validteDelta(mDelta + (y - mPrevY));
				} else {
					newDelta = validteDelta(mDelta + (x - mPrevX));
				}

				mPrevX = x;
				mPrevY = y;

				if (mDelta != newDelta) {
					mDelta = newDelta;
					requestLayout();
					invalidate();
				}
				return true;
			}
			break;
		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			if (mSliding) {
				mSliding = false;

				mVelocityTracker.computeCurrentVelocity(SNAP_VELOCITY,
						mMaximumFlingVelocity);

				final float velocity = ((mAlign & VERTICAL_MASK) > 0) ? mVelocityTracker
						.getYVelocity() : mVelocityTracker.getXVelocity();
				toggleSidebar(mDelta, velocity, false);

				mVelocityTracker.recycle();
				mVelocityTracker = null;
				return true;
			}
			break;
		}
		return mSliding;
	}

	private int validteDelta(int newDelta) {
		int sidebarSize = getSidebarSizeWithOutOffset();
		switch (mAlign) {
		case BOTTOM:
		case RIGHT:
			if (newDelta > 0) {
				return 0;
			} else if (newDelta < -sidebarSize) {
				return -sidebarSize;
			}
			break;
		case TOP:
		case LEFT:
		default:
			if (newDelta < 0) {
				return 0;
			} else if (newDelta > sidebarSize) {
				return sidebarSize;
			}
			break;
		}
		return newDelta;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (!mAllowDrag){
			return super.onInterceptTouchEvent(ev);
		}
		
		final int x = (int) ev.getX();
		final int y = (int) ev.getY();

		if (!mSliding && mDragRect.contains(x, y)) {
			mSliding = true;
			mPrevX = x;
			mPrevY = y;

			if (!mOpened) {
				mDelta = 0;
			} else {
				mDelta = ((mAlign & RIGHT_BOTTOM_MASK) > 0 ? -1 : 1) * getSidebarSize();
			}
		}
		return mSliding;
	}

	public void setListener(SidebarListener l) {
		mSidebarListener = l;
	}

	/* to see if the Sidebar is visible */
	public boolean isOpened() {
		return mOpened;
	}

	private void toggleSidebar(int from, float velocity, boolean toggled) {
		if (mToggling) {
			return;
		}
		
		final int sidebarSize = getSidebarSizeWithOutOffset();

		final boolean rbAlign = (mAlign & RIGHT_BOTTOM_MASK) > 0;

		final AnimatorListener listener;

		if (!toggled) {
			final boolean needOpen;
			if (Math.abs(velocity) > 1000) {
				needOpen = rbAlign ? (velocity < 0) : (velocity > 0);
			} else {
				needOpen = Math.abs(from) > mToggle;
			}
			listener = needOpen ? mOpenListener : mCloseListener;
			if (needOpen) {
				from = (rbAlign ? sidebarSize + from : from - sidebarSize);
			}
			mOpened = needOpen;
		} else if (mOpened) {
			mOpened = false;
			from = (rbAlign ? -1 : 1) * sidebarSize;
			listener = mCloseListener;
		} else {
			mOpened = true;
			from = (rbAlign ? 1 : -1) * sidebarSize;
			listener = mOpenListener;
		}

		if (from == 0) {
			listener.onAnimationEnd(null);
		} else {
			mToggling = true;
			requestLayout();

			final int duration = (int) (this.mDuration * (Math.abs(from) / (float) sidebarSize));
			final String translation = (mAlign & VERTICAL_MASK) > 0 ? "translationY"
					: "translationX";
			final List<Animator> animators = new ArrayList<Animator>();
			if (getSidebarMode() == SLIDE) {
				ObjectAnimator animator = ObjectAnimator.ofFloat(mSidebar.view,
						translation, from, 0);
				animator.setDuration(duration);
				animator.setInterpolator(mInterpolator);
				animator.addListener(listener);
				animators.add(animator);
			}

			if (getContentMode() == SLIDE) {
				ObjectAnimator animator = ObjectAnimator.ofFloat(mContent.view,
						translation, from, 0);
				animator.setDuration(duration);
				animator.setInterpolator(mInterpolator);
				animator.addListener(listener);
				animators.add(animator);
			}

			final AnimatorSet animSet = new AnimatorSet();
			animSet.addListener(listener);
			animSet.setDuration(duration);
			animSet.setInterpolator(mInterpolator);
			animSet.playTogether(animators);
			animSet.start();
		}
	}

	private int getSidebarSize() {
		return (mAlign & VERTICAL_MASK) > 0 ? mSidebarHeight
				: mSidebarWidth;
	}
	
	private int getSidebarSizeWithOutOffset() {
		return getSidebarSize() - mOffset;
	}

	public void toggleSidebar() {
		toggleSidebar(-1, 0, true);
	}

	public void openSidebar() {
		if (!mOpened) {
			toggleSidebar();
		}
	}

	public void closeSidebar() {
		if (mOpened) {
			toggleSidebar();
		}
	}

	@Override
	public void addView(View child, int index, LayoutParams params) {
		final int id = child.getId();
		if (id != View.NO_ID) {
			if ((mSidebar != null) && (mSidebar.id == id)) {
				mSidebar.view.removeAllViews(); 
				mSidebar.view.addView(child);
				return;
			}

			if ((mContent != null) && (mContent.id == id)) {
				mContent.view.removeAllViews(); 
				mContent.view.addView(child);
				return;
			}
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeView(View view) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeViewAt(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeViewInLayout(View view) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeViews(int start, int count) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeViewsInLayout(int start, int count) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeAllViews() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeAllViewsInLayout() {
		throw new UnsupportedOperationException();
	}

	public int getAlign() {
		return mAlign;
	}

	public void setAlign(int align) {
		if (this.mAlign != align) {
			switch (align) {
			case LEFT:
			case TOP:
			case RIGHT:
			case BOTTOM:
				this.mAlign = align;
				LayoutParams lp = mSizeResolver.resolveLayout(mAlign);
				if (lp != null){
					mSidebar.view.setLayoutParams(lp);
				}
				break;
			default:
				throw new IllegalArgumentException("Unsupported align");
			}

			requestLayout();
			invalidate();
		}
	}

	public void setDuration(int duration) {
		this.mDuration = duration;
	}

	public void setSlideAnimationInterpolator(Interpolator interpolator) {
		this.mInterpolator = interpolator;
	}

	public boolean isDebugMode() {
		return mDebugMode;
	}

	public void setDebugMode(boolean debugMode) {
		if (this.mDebugMode != debugMode) {
			this.mDebugMode = debugMode;
			invalidate();
		}
	}

	public int getSidebarMode() {
		return mSidebarMode;
	}

	public void setSidebarMode(int sidebarMode) {
		if (mSidebarHierarchy == OVER_CONTENT && sidebarMode == FIXED) {
			return;
		}

		if (this.mSidebarMode != sidebarMode) {
			this.mSidebarMode = sidebarMode;
			requestLayout();
		}
	}

	public int getContentMode() {
		return mContentMode;
	}

	public void setContentMode(int contentMode) {
		if (mSidebarHierarchy == UNDER_CONTENT && contentMode == FIXED) {
			return;
		}

		if (this.mContentMode != contentMode) {
			this.mContentMode = contentMode;
			requestLayout();
		}
	}

	public int getSidebarHierarchy() {
		return mSidebarHierarchy;
	}

	public void setSidebarSizeFraction(float size) {
		mSizeResolver = new SizeResolver(size, TypedValue.TYPE_FRACTION);
		requestLayout();
	}

	public void setSidebarSizeDimention(int size) {
		mSizeResolver = new SizeResolver(size, TypedValue.TYPE_DIMENSION);
		requestLayout();
	}

	public void setSidebarOffsetFraction(float size) {
		mOffsetResolver = new SizeResolver(size, TypedValue.TYPE_FRACTION);
		requestLayout();
	}

	public void setSidebarOffsetDimention(int size) {
		mOffsetResolver = new SizeResolver(size, TypedValue.TYPE_DIMENSION);
		requestLayout();
	}
	
	public void setSidebarHierarchy(int sidebarHierarchy) {
		if (mSidebarHierarchy != sidebarHierarchy) {
			mSidebarHierarchy = sidebarHierarchy;
			switch (mSidebarHierarchy) {
			case OVER_CONTENT:
				setSidebarMode(SLIDE);
				break;
			case UNDER_CONTENT:
				setContentMode(SLIDE);
				break;
			}
			
			if (mSidebar != null && mSidebar.view != null && mSidebar.view.getParent() != null) {
				detachViewFromParent(mSidebar.view);
				attachViewToParent(mSidebar.view, 
						(mSidebarHierarchy == UNDER_CONTENT) ? 0 : UNKNOWN, 
						mSidebar.view.getLayoutParams());
			}
			requestLayout();
		}
	}
	
	private void attachChildView(ViewHolder holder, View newView) {
		holder.view.removeAllViews();
		if (newView != null){
			if (newView.getId() == NO_ID){
				if (holder.id == NO_ID){
					holder.id = newView.hashCode();
				}
				newView.setId(holder.id);				
			} else {
				holder.id = newView.getId();
			}
			holder.view.addView(newView);
		}
	}
	
	public View getContent(){
		return mContent.view.getChildAt(0);
	}
	
	public void setContent(View view){
		attachChildView(mContent,view);
	}
	
	public View getSidebar(){
		return mSidebar.view.getChildAt(0);
	}
	
	public void setSidebar(View view){
		attachChildView(mSidebar,view);
	}

	class OpenListener implements AnimatorListener {

		@Override
		public void onAnimationRepeat(Animator animation) {
		}

		@Override
		public void onAnimationStart(Animator animation) {
		}

		@Override
		public void onAnimationEnd(Animator animation) {
			mToggling = false;
			mOpened = true;
			mDelta = 0;

			requestLayout();
			invalidate();
			if (mSidebarListener != null) {
				mSidebarListener.onSidebarOpened();
			}
		}

		@Override
		public void onAnimationCancel(Animator animation) {
			onAnimationEnd(animation);
		}
	}

	private void resolveChildViewAttach(){
		if (mContent != null &&
			mContent.view != null &&
		 	mContent.view.getParent() != this &&
		 	mSidebar != null &&
		 	mSidebar.view != null &&
		 	mSidebar.view.getParent() != this){
			throw new RuntimeException();
		}
		
		detachViewFromParent(mContent.view);
		detachViewFromParent(mSidebar.view);
		
		switch (mSidebarHierarchy) {
		case OVER_CONTENT:
			attachViewToParent(mContent.view, 0, generateDefaultLayoutParams());
			attachViewToParent(mSidebar.view, 1, generateDefaultLayoutParams());
			break;
		case UNDER_CONTENT:
			attachViewToParent(mSidebar.view, 0, generateDefaultLayoutParams());
			attachViewToParent(mContent.view, 1, generateDefaultLayoutParams());
			break;
		default:
			throw new UnsupportedOperationException();
		}
		
	}
	
	public boolean isAllowDrag() {
		return mAllowDrag;
	}

	public void setAllowDrag(boolean mAllowDrag) {
		this.mAllowDrag = mAllowDrag;
	}

	public int getDragOffsetContent() {
		return mDragOffsetContent;
	}

	public void setDragOffsetContent(int offsetContent) {
		mDragOffsetContent = offsetContent;
		requestLayout();
	}

	public int getDragOffsetSidebar() {
		return mDragOffsetSidebar;
	}

	public void setDragOffsetSidebar(int offsetSidebar) {
		mDragOffsetSidebar = offsetSidebar;
		requestLayout();
	}

	class CloseListener implements AnimatorListener {

		@Override
		public void onAnimationRepeat(Animator animation) {
		}

		@Override
		public void onAnimationStart(Animator animation) {
		}

		@Override
		public void onAnimationEnd(Animator animation) {
			mToggling = false;
			mOpened = false;
			mDelta = 0;

			mContent.recycleDrawingCache();
			
			requestLayout();
			invalidate();
			if (mSidebarListener != null) {
				mSidebarListener.onSidebarClosed();
			}
		}

		@Override
		public void onAnimationCancel(Animator animation) {
			onAnimationEnd(animation);
		}
	}

	public interface SidebarListener {
		public void onSidebarOpened();

		public void onSidebarClosed();
	}
}