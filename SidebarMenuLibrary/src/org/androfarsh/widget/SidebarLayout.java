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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

public class SidebarLayout extends ViewGroup {
	private static final String RES_TYPE_LAYOUT = "layout";
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

	private boolean mAnimated;

	private int mDuration = DURATION;
	private Interpolator mInterpolator = new LinearInterpolator();

	private ViewHolder mContent;
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

	private int mSidebarMode = FIXED;
	private int mContentMode = SLIDE;
	private int mSidebarHierarchy = UNDER_CONTENT;

	private boolean mInitialized;

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

		init();
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

		setSidebarHierarchy(a.getInt(
				R.styleable.SidebarLayout_sidebar_hierarchy, UNDER_CONTENT));

		setSidebarMode(a.getInt(R.styleable.SidebarLayout_sidebar_mode, FIXED));

		setContentMode(a.getInt(R.styleable.SidebarLayout_content_mode, SLIDE));

		mAlign = a.getInt(R.styleable.SidebarLayout_sidebar_align, LEFT);

		mDebugMode = a.getBoolean(R.styleable.SidebarLayout_debug_mode, false);

		final int interpolatorId = a.getResourceId(
				R.styleable.SidebarLayout_android_interpolator, UNKNOWN);
		if (interpolatorId != UNKNOWN) {
			mInterpolator = AnimationUtils.loadInterpolator(context,
					interpolatorId);
		}

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
			if (viewHolder.id == View.NO_ID) {
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

		if (mSidebarHierarchy == UNDER_CONTENT){
			super.addView(mSidebar.view, UNKNOWN, generateDefaultLayoutParams());
			super.addView(mContent.view, UNKNOWN, generateDefaultLayoutParams());
		}else {
			super.addView(mContent.view, UNKNOWN, generateDefaultLayoutParams());
			super.addView(mSidebar.view, UNKNOWN, generateDefaultLayoutParams());
		}
	
		mOpenListener = new OpenListener();
		mCloseListener = new CloseListener();
		mInitialized = true;
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
		t = l = 0;

		final int sidebarLeft, sidebarTop, sidebarRight, sidebarBottom;
		final Rect contentRect = new Rect();
		switch (mAlign) {
		case BOTTOM:
			if (getSidebarMode() == FIXED) {
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

			resolveContentLayout(contentRect, l, t, r, b, -mSidebarHeight);
			break;
		case TOP:
			if (getSidebarMode() == FIXED) {
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

			resolveContentLayout(contentRect, l, t, r, b, mSidebarHeight);
			break;
		case RIGHT:
			if (getSidebarMode() == FIXED) {
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

			resolveContentLayout(contentRect, l, t, r, b, -mSidebarWidth);
			break;
		case LEFT:
		default:
			if (getSidebarMode() == FIXED) {
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

			resolveContentLayout(contentRect, l, t, r, b, mSidebarWidth);
			break;
		}

		if (mSidebar.view.getVisibility() != View.GONE) {
			mSidebar.view.layout(sidebarLeft, sidebarTop, sidebarRight,
					sidebarBottom);
		}
		if (mContent.view.getVisibility() != View.GONE) {
			mContent.view.layout(contentRect.left, contentRect.top,
					contentRect.right, contentRect.bottom);
		}

		if (getContentMode() == SLIDE) {
			if ((mAlign & VERTICAL_MASK) > 0) {
				mDragRect.set(contentRect.left,
						(mAlign == TOP ? contentRect.top - mOffsetSidebar
								: contentRect.bottom - mOffsetContent),
						contentRect.right, (mAlign == TOP ? contentRect.top
								+ mOffsetContent : contentRect.bottom
								+ mOffsetSidebar));
			} else {
				mDragRect.set((mAlign == LEFT ? contentRect.left
						- mOffsetSidebar : contentRect.right - mOffsetContent),
						contentRect.top, (mAlign == LEFT ? contentRect.left
								+ mOffsetContent : contentRect.right
								+ mOffsetSidebar), contentRect.bottom);
			}
		} else {
			if ((mAlign & VERTICAL_MASK) > 0) {
				mDragRect
						.set(sidebarLeft,
								(mAlign == TOP ? sidebarBottom - mOffsetSidebar
										: sidebarTop - mOffsetContent),
								sidebarRight, (mAlign == TOP ? sidebarBottom
										+ mOffsetContent : sidebarTop
										+ mOffsetSidebar));
			} else {
				mDragRect.set((mAlign == LEFT ? sidebarRight - mOffsetSidebar
						: sidebarLeft - mOffsetContent), sidebarTop,
						(mAlign == LEFT ? sidebarRight + mOffsetContent
								: sidebarLeft + mOffsetSidebar), sidebarBottom);
			}
		}
	}

	private void resolveContentLayout(Rect result, int l, int t, int r, int b,
			int sidebarSize) {
		result.set(l, t, r, b);
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
		if (mAnimated) {
			return;
		}

		final int sidebarSize = (mAlign & VERTICAL_MASK) > 0 ? mSidebarHeight
				: mSidebarWidth;

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
			mAnimated = true;
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
				mSidebar.view = detachOldView(mSidebar.view, child, params);
				return;
			}

			if ((mContent != null) && (mContent.id == id)) {
				mContent.view = detachOldView(mContent.view, child, params);
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
		mSize = new SizeResolver(size, TypedValue.TYPE_FRACTION);
		requestLayout();
	}

	public void setSidebarSizeDimention(float size) {
		mSize = new SizeResolver(size, TypedValue.TYPE_DIMENSION);
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
						(mSidebarHierarchy != sidebarHierarchy) ? 0 : UNKNOWN, 
						mSidebar.view.getLayoutParams());
			}
			requestLayout();
		}
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
			mAnimated = false;
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

	class CloseListener implements AnimatorListener {

		@Override
		public void onAnimationRepeat(Animator animation) {
		}

		@Override
		public void onAnimationStart(Animator animation) {
		}

		@Override
		public void onAnimationEnd(Animator animation) {
			mAnimated = false;
			mOpened = false;
			mDelta = 0;

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
