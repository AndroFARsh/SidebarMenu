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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ViewSwitcher;


public class SidebarLayout extends ViewGroup {
	private static final String RES_TYPE_LAYOUT = "layout";
	private static final int SNAP_VELOCITY = 1000;
	private static final int OFFSET = 50;

	public static final int FIXED = 0;
	public static final int SLIDE = 1;

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

	private boolean mUseContentCache = false;
	private boolean mOpened;

	private int mDuration = DURATION;
	private Interpolator mInterpolator = new LinearInterpolator();

	private ViewGroup mContentWraper;
	private ViewSwitcher mContentSwitcher;
	private ImageView mContentImage;

	private ViewHolder mContent;
	private ViewHolder mDragbar;
	private ViewHolder mSidebar;

	private int mSidebarHeight;
	private int mSidebarWidth;

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
	private SizeResolver mSize = new SizeResolver(SIDEBAR_SIZE,
			TypedValue.TYPE_FRACTION);
	private int mOffsetContent = OFFSET;
	private int mOffsetSidebar = 0;
	private boolean mDebugMode;
	private float mMaximumFlingVelocity = 2 * SNAP_VELOCITY;
	private OnClickListener mClickCloseListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			closeSidebar();
		}
	};
	private int mSidebarMode;
	
	private Drawable mShadow;

	static class ViewHolder {
		private View view;
		private int id = View.NO_ID;
	}

	static class SizeResolver {
		private final float size;
		private final int type;

		SizeResolver(float size, int type) {
			this.size = size;
			this.type = type;
		}

		int resolveSize(int measuredSize) {
			if (type == TypedValue.TYPE_FRACTION) {
				return (int) (measuredSize * size);
			} else {
				return (int) size;
			}
		}
	}

	public SidebarLayout(Context context, int sidebarLayoutRes,
			int contentLayoutRes) {
		super(context);

		mSidebar = resolveReference(sidebarLayoutRes);
		mContent = resolveReference(contentLayoutRes);
	}

	public SidebarLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SidebarLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.SidebarLayout, defStyle, 0);

		mSize = resolveFractalOrDimentionValue(a
				.peekValue(R.styleable.SidebarLayout_sidebar_size));

		mToggleFactor = a.getFraction(
				R.styleable.SidebarLayout_toggle_coefficient, 1, 1, TOGLE_KOEF);

		mDuration = a.getInt(R.styleable.SidebarLayout_android_duration,
				DURATION);

		mShadow = a.getDrawable(R.styleable.SidebarLayout_shadow);
		
		mSidebarMode = a.getInt(R.styleable.SidebarLayout_sidebar_mode, FIXED);

		mAlign = a.getInt(R.styleable.SidebarLayout_sidebar_align, LEFT);

		mUseContentCache = a.getBoolean(
				R.styleable.SidebarLayout_use_content_cache, true);

		mDebugMode = a.getBoolean(R.styleable.SidebarLayout_debug_mode, false);

		final int interpolatorId = a.getResourceId(
				R.styleable.SidebarLayout_android_interpolator, UNKNOWN);
		if (interpolatorId != UNKNOWN) {
			mInterpolator = AnimationUtils.loadInterpolator(context,
					interpolatorId);
		}

		mDragbar = resolveReference(a.getResourceId(
				R.styleable.SidebarLayout_dragbar, UNKNOWN));

		mSidebar = resolveReference(a.getResourceId(
				R.styleable.SidebarLayout_sidebar, UNKNOWN));

		mContent = resolveReference(a.getResourceId(
				R.styleable.SidebarLayout_content, UNKNOWN));

		a.recycle();
	}

	private ViewHolder resolveReference(final int ref) {
		if (ref == UNKNOWN) {
			return null;
		}

		final ViewHolder viewHolder = new ViewHolder();
		if (RES_TYPE_LAYOUT.equals(getResources().getResourceTypeName(ref))) {
			viewHolder.view = View.inflate(getContext(), ref, null);
			viewHolder.id = viewHolder.view.getId();
			if (viewHolder.id == View.NO_ID){
				viewHolder.id = viewHolder.view.hashCode();
				viewHolder.view.setId(viewHolder.id);
			}
		} else {
			viewHolder.id = ref;
		}
		return viewHolder;
	}

	private SizeResolver resolveFractalOrDimentionValue(final TypedValue value) {
		if (value == null) {
			return new SizeResolver(SIDEBAR_SIZE, TypedValue.TYPE_FRACTION);
		}

		switch (value.type) {
		case TypedValue.TYPE_FRACTION:
			return new SizeResolver(TypedValue.complexToFraction(value.data, 1,
					1), TypedValue.TYPE_FRACTION);

		case TypedValue.TYPE_DIMENSION:
			return new SizeResolver(TypedValue.complexToDimension(value.data,
					getResources().getDisplayMetrics()),
					TypedValue.TYPE_DIMENSION);
		default:
			return new SizeResolver(SIDEBAR_SIZE, TypedValue.TYPE_FRACTION);
		}
	}

	@Override
	public void onFinishInflate() {
		super.onFinishInflate();

		if (mSidebar == null) {
			throw new NullPointerException("no sidebar view");
		}

		if (mContent == null) {
			throw new NullPointerException("no content view");
		}

		mContentSwitcher = new ViewSwitcher(getContext());
		if (mDragbar != null) {
			mContentWraper = new RelativeLayout(getContext());
			mContentWraper.addView(mDragbar.view);
			mContentWraper.addView(mContentSwitcher);

			requestDragBarLayout();
		} else {
			mContentWraper = mContentSwitcher;
		}

		mContentImage = new ImageView(getContext());
		mContentImage.setOnClickListener(mClickCloseListener);

		mContentSwitcher.addView(
				mContent.view,
				mContent.view.getLayoutParams() != null ? mContent.view
						.getLayoutParams() : generateDefaultLayoutParams());
		mContentSwitcher.addView(mContentImage, UNKNOWN,
				generateDefaultLayoutParams());

		super.addView(mSidebar.view, UNKNOWN, generateDefaultLayoutParams());
		super.addView(mContentWraper, UNKNOWN, generateDefaultLayoutParams());

		mOpenListener = new OpenListener();
		mCloseListener = new CloseListener();
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);
	}

	@Override
	public void onLayout(boolean changed, int l, int t, int r, int b) {
		/* the title bar assign top padding, drop it */
		t = l = 0;

		final int sidebarLeft, sidebarTop, sidebarRight, sidebarBottom;
		final int contentLeft, contentTop, contentRight, contentBottom;
		switch (mAlign) {
		case BOTTOM:
			if (mSidebarMode == FIXED) {
				sidebarLeft = l;
				sidebarTop = b - mSidebarHeight;
				sidebarRight = r;
				sidebarBottom = b;
			} else {
				if (mSliding) {
					sidebarLeft = l;
					sidebarTop = b + mDelta;
					sidebarRight = r;
					sidebarBottom = b - mDelta;
				} else if (mOpened) {
					sidebarLeft = l;
					sidebarTop = b - mSidebarHeight;
					sidebarRight = r;
					sidebarBottom = b + mSidebarHeight;
				} else {
					sidebarLeft = l;
					sidebarTop = b;
					sidebarRight = r;
					sidebarBottom = b + mSidebarHeight;
				}
			}

			if (mSliding) {
				contentLeft = l;
				contentTop = t + mDelta;
				contentRight = r;
				contentBottom = b + mDelta;
			} else if (mOpened) {
				contentLeft = l;
				contentTop = t - mSidebarHeight;
				contentRight = r;
				contentBottom = b - mSidebarHeight;
			} else {
				contentLeft = l;
				contentTop = t;
				contentRight = r;
				contentBottom = b;
			}
			break;
		case TOP:
			if (mSidebarMode == FIXED) {
				sidebarLeft = l;
				sidebarTop = t;
				sidebarRight = r;
				sidebarBottom = t + mSidebarHeight;
			} else {
				if (mSliding) {
					sidebarLeft = l;
					sidebarTop = t - (mSidebarHeight - mDelta);
					sidebarRight = r;
					sidebarBottom = t + mDelta;
				} else if (mOpened) {
					sidebarLeft = l;
					sidebarTop = t;
					sidebarRight = r;
					sidebarBottom = t + mSidebarHeight;
				} else {
					sidebarLeft = l;
					sidebarTop = t - mSidebarHeight;
					sidebarRight = r;
					sidebarBottom = t;
				}
			}

			if (mSliding) {
				contentLeft = l;
				contentTop = t + mDelta;
				contentRight = r;
				contentBottom = b + mDelta;
			} else if (mOpened) {
				contentLeft = l;
				contentTop = t + mSidebarHeight;
				contentRight = r;
				contentBottom = b + mSidebarHeight;
			} else {
				contentLeft = l;
				contentTop = t;
				contentRight = r;
				contentBottom = b;
			}
			break;
		case RIGHT:
			if (mSidebarMode == FIXED) {
				sidebarLeft = r - mSidebarWidth;
				sidebarTop = t;
				sidebarRight = r;
				sidebarBottom = b;
			} else {
				if (mSliding) {
					sidebarLeft = r + mDelta;
					sidebarTop = t;
					sidebarRight = r + (mSidebarWidth + mDelta);
					sidebarBottom = b;
				} else if (mOpened) {
					sidebarLeft = r - mSidebarWidth;
					sidebarTop = t;
					sidebarRight = r;
					sidebarBottom = b;
				} else {
					sidebarLeft = r;
					sidebarTop = t;
					sidebarRight = r + mSidebarWidth;
					sidebarBottom = b;
				}
			}

			if (mSliding) {
				contentLeft = l + mDelta;
				contentTop = t;
				contentRight = r + mDelta;
				contentBottom = b;
			} else if (mOpened) {
				contentLeft = l - mSidebarWidth;
				contentTop = t;
				contentRight = r - mSidebarWidth;
				contentBottom = b;
			} else {
				contentLeft = l;
				contentTop = t;
				contentRight = r;
				contentBottom = b;
			}
			break;
		case LEFT:
		default:
			if (mSidebarMode == FIXED) {
				sidebarLeft = l;
				sidebarTop = t;
				sidebarRight = l + mSidebarWidth;
				sidebarBottom = b;
			} else {
				if (mSliding) {
					sidebarLeft = (l - mSidebarWidth) + mDelta;
					sidebarTop = t;
					sidebarRight = l + mDelta;
					sidebarBottom = b;
				} else if (mOpened) {
					sidebarLeft = l;
					sidebarTop = t;
					sidebarRight = l + mSidebarWidth;
					sidebarBottom = b;
				} else {
					sidebarLeft = l - mSidebarWidth;
					sidebarTop = t;
					sidebarRight = l;
					sidebarBottom = b;
				}
			}

			if (mSliding) {
				contentLeft = l + mDelta;
				contentTop = t;
				contentRight = r + mDelta;
				contentBottom = b;
			} else if (mOpened) {
				contentLeft = l + mSidebarWidth;
				contentTop = t;
				contentRight = r + mSidebarWidth;
				contentBottom = b;
			} else {
				contentLeft = l;
				contentTop = t;
				contentRight = r;
				contentBottom = b;
			}
			break;
		}

		if (mSidebar.view.getVisibility() != View.GONE) {
			mSidebar.view.layout(sidebarLeft, sidebarTop, sidebarRight,
					sidebarBottom);
		}
		if (mContentWraper.getVisibility() != View.GONE) {
			mContentWraper.layout(contentLeft, contentTop, contentRight,
					contentBottom);
		}

		if (mDragbar != null) {
			final int newLeft;
			final int newTop;
			requestRectangle(mDragRect, mDragbar.view);
			if ((mAlign & VERTICAL_MASK) > 0) {
				newTop = mAlign == TOP ? contentTop : contentBottom
						- mDragRect.height();
				newLeft = 0;
			} else {
				newTop = 0;
				newLeft = mAlign == LEFT ? contentLeft : contentRight
						- mDragRect.width();
			}

			mDragRect.offsetTo(newLeft, newTop);
		} else {
			if ((mAlign & VERTICAL_MASK) > 0) {
				mDragRect.set(contentLeft, (mAlign == TOP ? contentTop
						- mOffsetSidebar : contentBottom - mOffsetContent),
						contentRight,
						(mAlign == TOP ? contentTop + mOffsetContent
								: contentBottom + mOffsetSidebar));
			} else {
				mDragRect.set((mAlign == LEFT ? contentLeft - mOffsetSidebar
						: contentRight - mOffsetContent), contentTop,
						(mAlign == LEFT ? contentLeft + mOffsetContent
								: contentRight + mOffsetSidebar), contentBottom);
			}
		}
	}

	private static Bitmap createDrawingCache(View view) {
		Bitmap bitmapRes = null;
		view.buildDrawingCache();
		final Bitmap bitmap = view.getDrawingCache();
		if (bitmap != null) {
			bitmapRes = Bitmap.createBitmap(bitmap);
			bitmap.recycle();
		}
		view.destroyDrawingCache();
		return bitmapRes;
	}

	private void requestRectangle(Rect outRect, View view) {
		if ((outRect == null) || (view == null)) {
			return;
		}

		outRect.left = view.getLeft();
		outRect.top = view.getTop();
		outRect.right = view.getRight();
		outRect.bottom = view.getBottom();
	}

	@Override
	public void onMeasure(int w, int h) {
		super.onMeasure(w, h);
		super.measureChildren(w, h);
		mSidebarWidth = mSidebar.view.getMeasuredWidth();
		mSidebarHeight = mSidebar.view.getMeasuredHeight();

		mToggle = (int) (mToggleFactor * ((mAlign & VERTICAL_MASK) > 0 ? mSidebarHeight
				: mSidebarWidth));
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
		boolean result = super.drawChild(canvas, child, drawingTime);
		if (mShadow != null && child == mSidebar.view){
			Region region = new Region(getLeft(), getTop(), getRight(), getBottom());
			Rect rect = new Rect(mContent.view.getLeft(), mContent.view.getTop(), mContent.view.getRight(), mContent.view.getBottom());
			region.op(rect, Op.DIFFERENCE);
			region.getBounds(rect);
			
			canvas.save();
			canvas.clipRect(rect);
			mShadow.setBounds(rect);
			mShadow.draw(canvas);
			canvas.restore();
		}
		return result; 
	}

	@Override
	protected void measureChild(View child, int parentWSpec, int parentHSpec) {
		if ((mSidebar != null) && (child == mSidebar.view)) {
			final int widthMeasureSpec;
			final int heightMeasureSpec;
			if ((mAlign & VERTICAL_MASK) > 0) {
				widthMeasureSpec = parentWSpec;
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(
						mSize.resolveSize(getMeasuredHeight()),
						MeasureSpec.getMode(parentHSpec));
			} else {
				heightMeasureSpec = parentHSpec;
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(
						mSize.resolveSize(getMeasuredWidth()),
						MeasureSpec.getMode(parentWSpec));
			}
			super.measureChild(child, widthMeasureSpec, heightMeasureSpec);
		} else {
			super.measureChild(child, parentWSpec, parentHSpec);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
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
							* ((mAlign & VERTICAL_MASK) > 0 ? mSidebarHeight
									: mSidebarWidth);
				}

				requestContentChache();
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
				toggleSidebar(mDelta, velocity, 0, false);

				mVelocityTracker.recycle();
				mVelocityTracker = null;
				return true;
			}
			break;
		}
		return mSliding;
	}

	private int validteDelta(int newDelta) {
		switch (mAlign) {
		case TOP:
			if (newDelta < 0) {
				return 0;
			} else if (newDelta > mSidebarHeight) {
				return mSidebarHeight;
			}
			break;
		case BOTTOM:
			if (newDelta > 0) {
				return 0;
			} else if (newDelta < -mSidebarHeight) {
				return -mSidebarHeight;
			}
			break;
		case RIGHT:
			if (newDelta > 0) {
				return 0;
			} else if (newDelta < -mSidebarWidth) {
				return -mSidebarWidth;
			}
			break;
		case LEFT:
		default:
			if (newDelta < 0) {
				return 0;
			} else if (newDelta > mSidebarWidth) {
				return mSidebarWidth;
			}
			break;
		}
		return newDelta;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		final int x = (int) ev.getX();
		final int y = (int) ev.getY();

		if (!mSliding && (mDragbar == null) && mDragRect.contains(x, y)) {
			mSliding = true;
			mPrevX = x;
			mPrevY = y;

			if (!mOpened) {
				mDelta = 0;
			} else {
				mDelta = ((mAlign & RIGHT_BOTTOM_MASK) > 0 ? -1 : 1)
						* ((mAlign & VERTICAL_MASK) > 0 ? mSidebarHeight
								: mSidebarWidth);
			}

			requestContentChache();
		}
		return mSliding;
	}

	private void requestContentChache() {
		if (!mOpened) {
			mContentImage.setImageBitmap(SidebarLayout
					.createDrawingCache(mContent.view));
		}

		final int contentImageIndex = mContentSwitcher
				.indexOfChild(mContentImage);
		if (mUseContentCache
				&& (mContentSwitcher.getDisplayedChild() != contentImageIndex)) {
			mContentSwitcher.setDisplayedChild(contentImageIndex);
		}
	}

	public void setListener(SidebarListener l) {
		mSidebarListener = l;
	}

	/* to see if the Sidebar is visible */
	public boolean isOpened() {
		return mOpened;
	}

	private void toggleSidebar(int from, float velocity, int startOffset,
			boolean toggled) {
		if ((mContentWraper.getAnimation() != null)) {
			return;
		}

		final int sidebarSize = (mAlign & VERTICAL_MASK) > 0 ? mSidebarHeight
				: mSidebarWidth;

		final boolean rbAlign = (mAlign & RIGHT_BOTTOM_MASK) > 0;

		final Animation.AnimationListener listener;

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
			requestLayout();

			final Animation animation;
			if ((mAlign & VERTICAL_MASK) > 0) {
				animation = new TranslateAnimation(0, 0, from, 0);
			} else {
				animation = new TranslateAnimation(from, 0, 0, 0);
			}

			final int duration = (int) (this.mDuration * (Math.abs(from) / (float) sidebarSize));
			animation.setStartOffset(startOffset);
			animation.setAnimationListener(listener);
			animation.setDuration(duration);
			animation.setInterpolator(mInterpolator);

			mContentWraper.startAnimation(animation);
			if (mSidebarMode == SLIDE) {
				mSidebar.view.startAnimation(animation);
			}
		}
	}

	public void toggleSidebar() {
		if (!mOpened) {
			requestContentChache();
		}
		toggleSidebar(0, 0, 100, true);
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
				mSidebar.view = detachOldView(mSidebar.view, child, params);
				return;
			}

			if ((mContent != null) && (mContent.id == id)) {
				mContent.view = detachOldView(mContent.view, child, params);
				return;
			}

			if ((mDragbar != null) && (mDragbar.id == id)) {
				mDragbar.view = detachOldView(mDragbar.view, child, params);
				return;
			}
		}
		throw new UnsupportedOperationException();
	}

	private View detachOldView(View oldView, View newView, LayoutParams params) {
		if ((oldView != null) && (oldView.getParent() != null)) {
			final ViewGroup parent = ((ViewGroup) oldView.getParent());
			final int newIndex = parent.indexOfChild(oldView);

			parent.removeView(oldView);
			parent.addView(newView, newIndex, params);
		}
		return newView;
	}

	private void requestDragBarLayout() {
		if (mDragbar == null) {
			return;
		}

		final RelativeLayout.LayoutParams cLp = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		final RelativeLayout.LayoutParams barLp = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		switch (mAlign) {
		case TOP:
			cLp.addRule(RelativeLayout.BELOW, mDragbar.id);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
					RelativeLayout.TRUE);
			break;
		case BOTTOM:
			cLp.addRule(RelativeLayout.ABOVE, mDragbar.id);

			barLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
					RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
					RelativeLayout.TRUE);
			break;
		case RIGHT:
			cLp.addRule(RelativeLayout.RIGHT_OF, mDragbar.id);

			barLp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
					RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
					RelativeLayout.TRUE);
			break;
		case LEFT:
		default:
			cLp.addRule(RelativeLayout.LEFT_OF, mDragbar.id);

			barLp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
					RelativeLayout.TRUE);
			barLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
			break;
		}

		mDragbar.view.setLayoutParams(barLp);
		mContentSwitcher.setLayoutParams(cLp);
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
				break;
			default:
				throw new IllegalArgumentException("Unsupported align");
			}

			requestDragBarLayout();

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

	public void setSidebarMode(int sidebarMode) {
		if (this.mSidebarMode != sidebarMode) {
			this.mSidebarMode = sidebarMode;
			requestLayout();
		}
	}

	class OpenListener implements Animation.AnimationListener {

		@Override
		public void onAnimationRepeat(Animation animation) {
		}

		@Override
		public void onAnimationStart(Animation animation) {
		}

		@Override
		public void onAnimationEnd(Animation animation) {
			mOpened = true;
			mDelta = 0;
			mSidebar.view.clearAnimation();
			mContentWraper.clearAnimation();

			requestLayout();
			invalidate();
			if (mSidebarListener != null) {
				mSidebarListener.onSidebarOpened();
			}
		}
	}

	class CloseListener implements Animation.AnimationListener {

		@Override
		public void onAnimationRepeat(Animation animation) {
		}

		@Override
		public void onAnimationStart(Animation animation) {
		}

		@Override
		public void onAnimationEnd(Animation animation) {
			mOpened = false;
			mDelta = 0;

			mSidebar.view.clearAnimation();
			mContentWraper.clearAnimation();
			mContentSwitcher.setDisplayedChild(mContentSwitcher
					.indexOfChild(mContent.view));

			requestLayout();
			invalidate();
			if (mSidebarListener != null) {
				mSidebarListener.onSidebarClosed();
			}
		}
	}

	public interface SidebarListener {
		public void onSidebarOpened();

		public void onSidebarClosed();
	}
}
