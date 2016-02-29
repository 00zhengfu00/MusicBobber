package com.cleveroad.audiowidget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Created by Александр on 26.02.2016.
 */
class TouchManager implements View.OnTouchListener {

	private final View view;
	private final WindowManager windowManager;
	private final float[] bounds;
	private final int rootWidth;
	private final int rootHeight;

	private long onDownTimestamp;
	private boolean longClickCanceled;
	private float prevX;
	private float prevY;
	private float prevLeft;
	private float prevTop;
	private boolean movedFarEnough;
	private boolean longClickPerformed;
	private Callback callback;

	private TouchManager(@NonNull View view) {
		this.view = view;
		this.view.setOnTouchListener(this);
		Context context = view.getContext().getApplicationContext();
		this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		this.rootWidth = context.getResources().getDisplayMetrics().widthPixels;
		this.rootHeight = (int) (context.getResources().getDisplayMetrics().heightPixels - DrawableUtils.dpToPx(context, 25));

		this.bounds = new float[2];
	}

	public TouchManager callback(Callback callback) {
		this.callback = callback;
		return this;
	}

	@Override
	public boolean onTouch(@NonNull View v, @NonNull MotionEvent event) {
		if (callback != null && !callback.canBeTouched())
			return false;
		WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) view.getLayoutParams();
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				prevX = event.getRawX();
				prevY = event.getRawY();
				prevLeft = layoutParams.x;
				prevTop = layoutParams.y;
				onDownTimestamp = System.currentTimeMillis();
				longClickCanceled = false;
				movedFarEnough = false;
				longClickPerformed = false;
				postDelayed(() -> {
					if (!longClickCanceled && !movedFarEnough) {
						longClickPerformed = true;
						if (callback != null) {
							callback.onLongClick(prevX, prevY);
						}
					}
				}, Configuration.LONG_CLICK_THRESHOLD);
				if (callback != null) {
					callback.onTouched();
				}
				return true;
			}
			case MotionEvent.ACTION_MOVE: {
				if (longClickPerformed) {
					return false;
				}
				float diffX = event.getRawX() - prevX;
				float diffY = event.getRawY() - prevY;
				float l = prevLeft + diffX;
				float t = prevTop + diffY;
				float r = l + layoutParams.width;
				float b = t + layoutParams.height;
				if (view instanceof BoundsChecker) {
					((BoundsChecker) view).checkBounds(l, t, r, b, rootWidth, rootHeight, bounds);
					l = bounds[0];
					t = bounds[1];
				}
				movedFarEnough = Math.hypot(diffX, diffY) >= Configuration.MOVEMENT_THRESHOLD;
				layoutParams.x = (int) l;
				layoutParams.y = (int) t;
				windowManager.updateViewLayout(view, layoutParams);
				if (callback != null) {
					callback.onMoved(diffX, diffY);
				}
				return true;
			}
			case MotionEvent.ACTION_UP: {
				long curTime = System.currentTimeMillis();
				long diff = curTime - onDownTimestamp;
				if (diff <= Configuration.LONG_CLICK_THRESHOLD) {
					longClickCanceled = true;
				}
				if (diff <= Configuration.CLICK_THRESHOLD) {
					if (callback != null) {
						callback.onClick(prevX, prevY);
					}
				}
				if (callback != null) {
					callback.onReleased();
				}
				return true;
			}
			case MotionEvent.ACTION_OUTSIDE: {
				if (callback != null) {
					callback.onTouchOutside();
				}
				break;
			}
		}
		return false;
	}

	private void postDelayed(@NonNull Runnable runnable, long delayMillis) {
		view.postDelayed(runnable, delayMillis);
	}

	public static TouchManager create(View view) {
		return new TouchManager(view);
	}

	interface Callback {
		void onClick(float x, float y);
		void onLongClick(float x, float y);
		void onTouchOutside();
		void onTouched();
		void onMoved(float diffX, float diffY);
		void onReleased();
		boolean canBeTouched();
	}

	public static class SimpleCallback implements Callback {

		@Override
		public void onClick(float x, float y) {

		}

		@Override
		public void onLongClick(float x, float y) {

		}

		@Override
		public void onTouchOutside() {

		}

		@Override
		public void onTouched() {

		}

		@Override
		public void onMoved(float diffX, float diffY) {

		}

		@Override
		public void onReleased() {

		}

		@Override
		public boolean canBeTouched() {
			return true;
		}
	}

	interface BoundsChecker {
		void checkBounds(float left, float top, float right, float bottom, float screenWidth, float screenHeight, float[] outBounds);
	}
}
