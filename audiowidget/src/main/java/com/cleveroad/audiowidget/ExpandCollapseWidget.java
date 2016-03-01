package com.cleveroad.audiowidget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import java.util.Random;

/**
 * Created by Александр on 25.02.2016.
 */
@SuppressLint("ViewConstructor")
class ExpandCollapseWidget extends View implements PlaybackState.PlaybackStateListener {

	static final byte DIRECTION_LEFT = 1;
	static final byte DIRECTION_RIGHT = 2;

	private static final float EXPAND_DURATION_F = (34 * Configuration.FRAME_SPEED);
	private static final long EXPAND_DURATION_L = (long) EXPAND_DURATION_F;
	private static final float EXPAND_COLOR_END_F = 9 * Configuration.FRAME_SPEED;
	private static final float EXPAND_SIZE_END_F = 12 * Configuration.FRAME_SPEED;
	private static final float EXPAND_POSITION_START_F = 10 * Configuration.FRAME_SPEED;
	private static final float EXPAND_POSITION_END_F = 18 * Configuration.FRAME_SPEED;
	private static final float EXPAND_BUBBLES_START_F = 18 * Configuration.FRAME_SPEED;
	private static final float EXPAND_BUBBLES_END_F = 32 * Configuration.FRAME_SPEED;
	private static final float EXPAND_ELEMENTS_START_F = 20 * Configuration.FRAME_SPEED;
	private static final float EXPAND_ELEMENTS_END_F = 27 * Configuration.FRAME_SPEED;

	private static final float COLLAPSE_DURATION_F = 12 * Configuration.FRAME_SPEED;
	private static final long COLLAPSE_DURATION_L = (long) COLLAPSE_DURATION_F;
	private static final float COLLAPSE_ELEMENTS_END_F = 3 * Configuration.FRAME_SPEED;
	private static final float COLLAPSE_SIZE_START_F = 2 * Configuration.FRAME_SPEED;
	private static final float COLLAPSE_SIZE_END_F = 12 * Configuration.FRAME_SPEED;
	private static final float COLLAPSE_POSITION_START_F = 3 * Configuration.FRAME_SPEED;
	private static final float COLLAPSE_POSITION_END_F = 12 * Configuration.FRAME_SPEED;


	private static final int INDEX_PLAYLIST = 0;
	private static final int INDEX_PREV = 1;
	private static final int INDEX_PLAY = 2;
	private static final int INDEX_NEXT = 3;
	private static final int INDEX_ALBUM = 4;
	private static final int INDEX_PAUSE = 5;

	private static final int TOTAL_BUBBLES_COUNT = 30;
	private static final float BUBBLE_MIN_SIZE = 10;
	private static final float BUBBLE_MAX_SIZE = 25;


	private final Paint paint;
	private final float radius;
	private final float widgetWidth;
	private final float widgetHeight;
	private final ColorChanger colorChanger;
	private final int playColor;
	private final int pauseColor;
	private final int widgetColor;
	private final Drawable[] drawables;
	private final Rect[] buttonBounds;
	private final float sizeStep;
	private final float[] bubbleSizes;
	private final float[] bubbleSpeeds;
	private final float[] bubblePositions;
	private final Random random;
	private final Paint bubblesPaint;
	private final RectF bounds;
	private final Rect tmpRect;
	private final PlaybackState playbackState;
	private final ValueAnimator expandAnimator;
	private final ValueAnimator collapseAnimator;
	private final Drawable defaultAlbumCover;

	private float bubblesTime;
	private boolean expanded;
	private boolean animatingExpand, animatingCollapse;
	private boolean updatedBubbles;
	private byte expandDirection;
	private AudioWidget.OnWidgetStateChangedListener onWidgetStateChangedListener;
	private int padding;
	private AudioWidget.OnControlsClickListener onControlsClickListener;

	public ExpandCollapseWidget(@NonNull Configuration configuration) {
		super(configuration.context());
		setLayerType(LAYER_TYPE_SOFTWARE, null);
		this.playbackState = configuration.playbackState();
		this.random = configuration.random();
		this.bubblesPaint = new Paint();
		this.bubblesPaint.setStyle(Paint.Style.FILL);
		this.bubblesPaint.setAntiAlias(true);
		this.bubblesPaint.setColor(configuration.expandedColor());
		this.paint = new Paint();
		this.paint.setColor(configuration.expandedColor());
		this.paint.setAntiAlias(true);
		this.paint.setShadowLayer(10, 2, 2, Color.argb(80, 0, 0, 0));
		this.radius = configuration.radius();
		this.widgetWidth = configuration.widgetWidth();
		this.colorChanger = new ColorChanger();
		this.playColor = configuration.darkColor();
		this.pauseColor = configuration.lightColor();
		this.widgetColor = configuration.expandedColor();
		this.tmpRect = new Rect();
		this.buttonBounds = new Rect[5];
		this.drawables = new Drawable[6];
		this.bounds = new RectF();
		this.drawables[INDEX_PLAYLIST] = configuration.playlistDrawable().getConstantState().newDrawable();
		this.drawables[INDEX_PREV] = configuration.prevDrawable().getConstantState().newDrawable();
		this.drawables[INDEX_PLAY] = configuration.playDrawable().getConstantState().newDrawable();
		this.drawables[INDEX_PAUSE] = configuration.pauseDrawable().getConstantState().newDrawable();
		this.drawables[INDEX_NEXT] = configuration.nextDrawable().getConstantState().newDrawable();
		this.drawables[INDEX_ALBUM] = defaultAlbumCover = configuration.albumDrawable().getConstantState().newDrawable();
		this.sizeStep = widgetWidth / 5f;
		this.widgetHeight = radius * 2;
		for (int i = 0; i < buttonBounds.length; i++) {
			buttonBounds[i] = new Rect();
		}
		this.bubbleSizes = new float[TOTAL_BUBBLES_COUNT];
		this.bubbleSpeeds = new float[TOTAL_BUBBLES_COUNT];
		this.bubblePositions = new float[TOTAL_BUBBLES_COUNT * 2];
		this.playbackState.addPlaybackStateListener(this);
		this.expandAnimator = ValueAnimator.ofInt(0, (int) EXPAND_DURATION_L).setDuration(EXPAND_DURATION_L);
		this.expandAnimator.addUpdateListener(animation -> {
			int position = (int) animation.getAnimatedValue();
			updateExpandAnimation(position);
			invalidate();
		});
		this.expandAnimator.addListener(new SimpleAnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {
				super.onAnimationStart(animation);
				animatingExpand = true;
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				super.onAnimationEnd(animation);
				animatingExpand = false;
				expanded = true;
				if (onWidgetStateChangedListener != null) {
					onWidgetStateChangedListener.onStateChanged(AudioWidget.State.EXPANDED);
				}
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				super.onAnimationCancel(animation);
				animatingExpand = false;
			}
		});
		this.collapseAnimator = ValueAnimator.ofInt(0, (int) COLLAPSE_DURATION_L).setDuration(COLLAPSE_DURATION_L);
		this.collapseAnimator.addUpdateListener(animation -> {
			int position = (int) animation.getAnimatedValue();
			updateCollapseAnimation(position);
			invalidate();
		});
		this.collapseAnimator.addListener(new SimpleAnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {
				super.onAnimationStart(animation);
				animatingCollapse = true;
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				super.onAnimationEnd(animation);
				animatingCollapse = false;
				expanded = false;
				if (onWidgetStateChangedListener != null) {
					onWidgetStateChangedListener.onStateChanged(AudioWidget.State.COLLAPSED);
				}
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				super.onAnimationCancel(animation);
				animatingCollapse = false;
			}
		});
		this.padding = (int) DrawableUtils.dpToPx(configuration.context(), 4);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int w = MeasureSpec.makeMeasureSpec((int) widgetWidth + padding * 2, MeasureSpec.EXACTLY);
		int h = MeasureSpec.makeMeasureSpec((int) (widgetHeight * 2) + padding * 2, MeasureSpec.EXACTLY);
		super.onMeasure(w, h);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		if (animatingExpand && bubblesTime >= 0) {
			int half = TOTAL_BUBBLES_COUNT / 2;
			for (int i = 0; i < TOTAL_BUBBLES_COUNT; i++) {
				float radius = bubbleSizes[i];
				float speed = bubbleSpeeds[i] * bubblesTime;
				float cx = bubblePositions[2 * i];
				float cy = bubblePositions[2 * i + 1];
				if (i < half)
					cy *= (1 - speed);
				else
					cy *= (1 + speed);
				canvas.drawCircle(cx, cy, radius, bubblesPaint);
			}
		}
		canvas.drawRoundRect(bounds, radius, radius, paint);
		drawMediaButtons(canvas);
	}

	private void drawMediaButtons(@NonNull Canvas canvas) {
		for (int i = 0; i < buttonBounds.length; i++) {
			Drawable drawable;
			if (i == INDEX_PLAY) {
				if (playbackState.state() == AudioWidget.Controller.STATE_PLAYING) {
					drawable = drawables[INDEX_PAUSE];
				} else {
					drawable = drawables[INDEX_PLAY];
				}
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && drawable.getAlpha() == 0) {
					Log.w("TEST", "wrong alpha value");
					drawable.setAlpha(255);
				}
			} else {
				drawable = drawables[i];
			}
			drawable.setBounds(buttonBounds[i]);
			drawable.draw(canvas);
		}
	}

	private void updateExpandAnimation(long position) {
		if (DrawableUtils.isBetween(position, 0, EXPAND_COLOR_END_F)) {
			float t = DrawableUtils.normalize(position, 0, EXPAND_COLOR_END_F);
			paint.setColor(colorChanger.nextColor(t));
		}
		if (DrawableUtils.isBetween(position, 0, EXPAND_SIZE_END_F)) {
			float time = DrawableUtils.normalize(position, 0, EXPAND_SIZE_END_F);
			float l, r, t, b;
			float height = radius * 2;
			t = radius;
			b = t + height;
			if (expandDirection == DIRECTION_LEFT) {
				r = widgetWidth;
				l = r - height - (widgetWidth - height) * time;
			} else {
				l = 0;
				r = l + height + (widgetWidth - height) * time;
			}
			bounds.set(l, t, r, b);
		} else if (position > EXPAND_SIZE_END_F) {
			if (expandDirection == DIRECTION_LEFT) {
				bounds.left = 0;
			} else {
				bounds.right = widgetWidth;
			}

		}
		if (DrawableUtils.isBetween(position, 0, EXPAND_POSITION_START_F)) {
			if (expandDirection == DIRECTION_LEFT) {
				calculateBounds(INDEX_ALBUM, buttonBounds[INDEX_PLAY]);
			} else {
				calculateBounds(INDEX_PLAYLIST, buttonBounds[INDEX_PLAY]);
			}
		}
		if (DrawableUtils.isBetween(position, 0, EXPAND_ELEMENTS_START_F)) {
			for (int i = 0; i < buttonBounds.length; i++) {
				if (i != INDEX_PLAY) {
					drawables[i].setAlpha(0);
				}
			}
		}
		if (DrawableUtils.isBetween(position, EXPAND_ELEMENTS_START_F, EXPAND_ELEMENTS_END_F)) {
			float time = DrawableUtils.normalize(position, EXPAND_ELEMENTS_START_F, EXPAND_ELEMENTS_END_F);
			expandCollapseElements(time);
		}
		if (DrawableUtils.isBetween(position, EXPAND_POSITION_START_F, EXPAND_POSITION_END_F)) {
			float time = DrawableUtils.normalize(position, EXPAND_POSITION_START_F, EXPAND_POSITION_END_F);
			Rect playBounds = buttonBounds[INDEX_PLAY];
			calculateBounds(INDEX_PLAY, playBounds);
			int l, t, r, b;
			t = playBounds.top;
			b = playBounds.bottom;
			if (expandDirection == DIRECTION_LEFT) {
				calculateBounds(INDEX_ALBUM, tmpRect);
				l = (int) DrawableUtils.reduce(tmpRect.left, playBounds.left, time);
				r = l + playBounds.width();
			} else {
				calculateBounds(INDEX_PLAYLIST, tmpRect);
				l = (int) DrawableUtils.enlarge(tmpRect.left, playBounds.left, time);
				r = l + playBounds.width();
			}
			playBounds.set(l, t, r, b);
		} else if (position >= EXPAND_POSITION_END_F) {
			calculateBounds(INDEX_PLAY, buttonBounds[INDEX_PLAY]);
		}
		if (DrawableUtils.isBetween(position, EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F)) {
			if (!updatedBubbles) {
				updatedBubbles = true;
				int half = TOTAL_BUBBLES_COUNT / 2;
				float step = widgetWidth / half;
				for (int i = 0; i < TOTAL_BUBBLES_COUNT; i++) {
					int index = i % half;
					float speed = 0.3f + 0.7f * random.nextFloat();
					float size = BUBBLE_MIN_SIZE + (BUBBLE_MAX_SIZE - BUBBLE_MIN_SIZE) * random.nextFloat();
					float radius = size / 2f;
					float cx = padding + index * step + step * random.nextFloat() * (random.nextBoolean() ? 1 : -1);
					float cy = bounds.centerY();
					bubbleSpeeds[i] = speed;
					bubbleSizes[i] = radius;
					bubblePositions[2 * i] = cx;
					bubblePositions[2 * i + 1] = cy;
				}
			}
			float time = DrawableUtils.normalize(position, EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F);
			bubblesPaint.setAlpha((int) DrawableUtils.customFunction(time, 0, 0, 255, 0.33f, 255, 0.66f, 0, 1f));
		} else if (position > EXPAND_BUBBLES_END_F) {
			updatedBubbles = false;
		}
		if (DrawableUtils.isBetween(position, EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F)) {
			bubblesTime = DrawableUtils.normalize(position, EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F);
		}
	}

	private void calculateBounds(int index, Rect bounds) {
		calculateBounds(index, bounds, Configuration.BUTTON_PADDING);
	}

	private void calculateBounds(int index, Rect bounds, int padding) {
		int l = (int) (index * sizeStep + padding);
		int t = (int) (radius + padding);
		int r = (int) ((index + 1) * sizeStep - padding);
		int b = (int) (radius * 3 - padding);
		bounds.set(l, t, r, b);
	}

	private void updateCollapseAnimation(long position) {
		if (DrawableUtils.isBetween(position, 0, COLLAPSE_ELEMENTS_END_F)) {
			float time = 1 - DrawableUtils.normalize(position, 0, COLLAPSE_ELEMENTS_END_F);
			expandCollapseElements(time);
		}
		if (position > COLLAPSE_ELEMENTS_END_F) {
			for (int i = 0; i < buttonBounds.length; i++) {
				if (i != INDEX_PLAY) {
					drawables[i].setAlpha(0);
				}
			}
		}
		if (DrawableUtils.isBetween(position, COLLAPSE_POSITION_START_F, COLLAPSE_POSITION_END_F)) {
			float time = DrawableUtils.normalize(position, COLLAPSE_POSITION_START_F, COLLAPSE_POSITION_END_F);
			Rect playBounds = buttonBounds[INDEX_PLAY];
			calculateBounds(INDEX_PLAY, playBounds);
			int l, t, r, b;
			t = playBounds.top;
			b = playBounds.bottom;
			if (expandDirection == DIRECTION_LEFT) {
				calculateBounds(INDEX_ALBUM, tmpRect);
				l = (int) DrawableUtils.enlarge(playBounds.left, tmpRect.left, time);
				r = l + playBounds.width();
			} else {
				calculateBounds(INDEX_PLAYLIST, tmpRect);
				l = (int) DrawableUtils.reduce(playBounds.left, tmpRect.left, time);
				r = l + playBounds.width();
			}
			buttonBounds[INDEX_PLAY].set(l, t, r, b);
		}
		if (DrawableUtils.isBetween(position, COLLAPSE_SIZE_START_F, COLLAPSE_SIZE_END_F)) {
			float time = DrawableUtils.normalize(position, COLLAPSE_SIZE_START_F, COLLAPSE_SIZE_END_F);
			paint.setColor(colorChanger.nextColor(time));
			float l, r, t, b;
			float height = radius * 2;
			t = radius;
			b = t + height;
			if (expandDirection == DIRECTION_LEFT) {
				r = widgetWidth;
				l = r - height - (widgetWidth - height) * (1 - time);
			} else {
				l = 0;
				r = l + height + (widgetWidth - height) * (1 - time);
			}
			bounds.set(l, t, r, b);
		}
	}

	private void expandCollapseElements(float time) {
		int alpha = (int) DrawableUtils.between(time * 255, 0, 255);
		for (int i = 0; i < buttonBounds.length; i++) {
			if (i != INDEX_PLAY) {
				int padding = 16;
				calculateBounds(i, buttonBounds[i]);
				float size = time * (sizeStep / 2f - padding);
				int cx = buttonBounds[i].centerX();
				int cy = buttonBounds[i].centerY();
				buttonBounds[i].set((int) (cx - size), (int) (cy - size), (int) (cx + size), (int) (cy + size));
				drawables[i].setAlpha(alpha);
			}
		}
	}

	public void onClick(float x, float y) {
		if (isAnimationInProgress())
			return;
		int index = -1;
		for (int i = 0; i < buttonBounds.length; i++) {
			calculateBounds(i, tmpRect, 0);
			if (tmpRect.contains((int) x, (int) y)) {
				index = i;
				break;
			}
		}
		switch (index) {
			case INDEX_PLAYLIST: {
				if (onControlsClickListener != null) {
					onControlsClickListener.onPlaylistClicked();
				}
				break;
			}
			case INDEX_PREV: {
				if (onControlsClickListener != null) {
					onControlsClickListener.onPreviousClicked();
				}
				break;
			}
			case INDEX_PLAY: {
				if (onControlsClickListener != null) {
					onControlsClickListener.onPlayPauseClicked();
				}
				break;
			}
			case INDEX_NEXT: {
				if (onControlsClickListener != null) {
					onControlsClickListener.onNextClicked();
				}
				break;
			}
			case INDEX_ALBUM: {
				if (onControlsClickListener != null) {
					onControlsClickListener.onAlbumClicked();
				}
				break;
			}
			default: {
				Log.w(ExpandCollapseWidget.class.getSimpleName(), "Unknown index: " + index);
				break;
			}
		}
	}

	public void expand(byte expandDirection) {
		if (expanded)
			return;
		this.expandDirection = expandDirection;
		startExpandAnimation();
	}

	private void startExpandAnimation() {
		if (isAnimationInProgress())
			return;
		animatingExpand = true;
		if (playbackState.state() == AudioWidget.Controller.STATE_PLAYING) {
			colorChanger
					.fromColor(playColor)
					.toColor(widgetColor);

		} else {
			colorChanger
					.fromColor(pauseColor)
					.toColor(widgetColor);
		}
		expandAnimator.start();
	}

	private void startCollapseAnimation() {
		if (isAnimationInProgress())
			return;
		collapseAnimator.start();
	}

	public boolean isAnimationInProgress() {
		return animatingCollapse || animatingExpand;
	}

	public void collapse() {
		if (!expanded) {
			return;
		}
		if (playbackState.state() == AudioWidget.Controller.STATE_PLAYING) {
			colorChanger
					.fromColor(widgetColor)
					.toColor(playColor);
		} else {
			colorChanger
					.fromColor(widgetColor)
					.toColor(pauseColor);
		}
		startCollapseAnimation();
	}

	@Override
	public void onStateChanged(int oldState, int newState, Object initiator) {
		invalidate();
	}

	@Override
	public void onProgressChanged(int position, int duration, float percentage) {

	}

	public ExpandCollapseWidget onWidgetStateChangedListener(AudioWidget.OnWidgetStateChangedListener onWidgetStateChangedListener) {
		this.onWidgetStateChangedListener = onWidgetStateChangedListener;
		return this;
	}

	public byte expandDirection() {
		return expandDirection;
	}

	public void onControlsClickListener(AudioWidget.OnControlsClickListener onControlsClickListener) {
		this.onControlsClickListener = onControlsClickListener;
	}

	public void albumCover(@Nullable Drawable albumCover) {
		if (drawables[INDEX_ALBUM] == albumCover)
			return;
		if (albumCover == null) {
			drawables[INDEX_ALBUM] = defaultAlbumCover;
		} else {
			if (albumCover.getConstantState() != null)
				drawables[INDEX_ALBUM] = albumCover.getConstantState().newDrawable();
			else
				drawables[INDEX_ALBUM] = albumCover;
		}
		Rect bounds = buttonBounds[INDEX_ALBUM];
		invalidate(bounds.left, bounds.top, bounds.right, bounds.bottom);
	}
}
