package com.cleveroad.audiowidget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import java.util.Random;

/**
 * Audio widget implementation.
 */
public class AudioWidget {

    /**
     * Play/pause button view.
     */
    private final PlayPauseButton playPauseButton;

    /**
     * Expanded widget style view.
     */
    private final ExpandCollapseWidget expandCollapseWidget;

    /**
     * Remove widget view.
     */
    private final RemoveWidgetView removeWidgetView;

    /**
     * Playback state.
     */
    private PlaybackState playbackState;

    /**
     * Widget controller.
     */
    private final Controller controller;

    private final WindowManager windowManager;
    private final Handler handler;
    private final Point screenSize;
    private final Context context;

    /**
     * Bounds of remove widget view. Used for checking if play/pause button is inside this bounds
     * and ready for removing from screen.
     */
    private final RectF removeBounds;

    /**
     * Remove widget view Y position (hidden).
     */
    private float hiddenRemWidY;

    /**
     * Remove widget view Y position (visible).
     */
    private float visibleRemWidY;
    private float width, height, radius;
    private final OnControlsClickListenerWrapper onControlsClickListener;
    private boolean shown;
    private boolean released;
    private boolean removeWidgetShown;
    private OnWidgetStateChangedListener onWidgetStateChangedListener;

    @SuppressWarnings("deprecation")
    private AudioWidget(@NonNull Builder builder) {
        this.context = builder.context.getApplicationContext();
        this.handler = new Handler();
        this.screenSize = new Point();
        this.removeBounds = new RectF();
        this.controller = newController();
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            windowManager.getDefaultDisplay().getSize(screenSize);
        } else {
            screenSize.x = windowManager.getDefaultDisplay().getWidth();
            screenSize.y = windowManager.getDefaultDisplay().getHeight();
        }
        screenSize.y -= context.getResources().getDimensionPixelSize(R.dimen.aw_status_bar_height);

        Configuration configuration = prepareConfiguration(builder);
        playPauseButton = new PlayPauseButton(configuration);
        expandCollapseWidget = new ExpandCollapseWidget(configuration);
        removeWidgetView = new RemoveWidgetView(configuration);
        float smt = context.getResources().getDimensionPixelSize(R.dimen.aw_significant_movement_threshold);
        TouchManager playPauseButtonManager = TouchManager.create(playPauseButton, Configuration.CLICK_THRESHOLD, Configuration.LONG_CLICK_THRESHOLD, smt);
        TouchManager expandedWidgetManager = TouchManager.create(expandCollapseWidget, Configuration.CLICK_THRESHOLD, Configuration.LONG_CLICK_THRESHOLD, smt);

        playPauseButtonManager.callback(new PlayPauseButtonCallback());
        expandedWidgetManager.callback(new ExpandCollapseWidgetCallback());
        expandCollapseWidget.onWidgetStateChangedListener(new OnWidgetStateChangedListener() {
            @Override
            public void onWidgetStateChanged(@NonNull State state) {
                if (state == State.COLLAPSED) {
                    playPauseButton.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    windowManager.removeView(expandCollapseWidget);
                    playPauseButton.enableProgressChanges(true);
                }
                if (onWidgetStateChangedListener != null) {
                    onWidgetStateChangedListener.onWidgetStateChanged(state);
                }
            }

            @Override
            public void onWidgetPositionChanged(int cx, int cy) {

            }
        });
        onControlsClickListener = new OnControlsClickListenerWrapper();
        expandCollapseWidget.onControlsClickListener(onControlsClickListener);
    }

    private Configuration prepareConfiguration(@NonNull Builder builder) {
        int darkColor = builder.darkColorSet ? builder.darkColor : VersionUtil.color(context, R.color.aw_dark);
        int lightColor = builder.lightColorSet ? builder.lightColor : VersionUtil.color(context, R.color.aw_light);
        int progressColor = builder.progressColorSet ? builder.progressColor : VersionUtil.color(context, R.color.aw_progress);
        int expandColor = builder.expandWidgetColorSet ? builder.expandWidgetColor : VersionUtil.color(context, R.color.aw_expanded);
        int crossColor = builder.crossColorSet ? builder.crossColor : VersionUtil.color(context, R.color.aw_cross_default);
        int crossOverlappedColor = builder.crossOverlappedColorSet ? builder.crossOverlappedColor : VersionUtil.color(context, R.color.aw_cross_overlapped);
        int shadowColor = builder.shadowColorSet ? builder.shadowColor : VersionUtil.color(context, R.color.aw_shadow);

        Drawable playDrawable = builder.playDrawable != null ? builder.playDrawable : VersionUtil.drawable(context, R.drawable.ic_play);
        Drawable pauseDrawable = builder.pauseDrawable != null ? builder.pauseDrawable : VersionUtil.drawable(context, R.drawable.ic_pause);
        Drawable prevDrawable = builder.prevDrawable != null ? builder.prevDrawable : VersionUtil.drawable(context, R.drawable.ic_prev);
        Drawable nextDrawable = builder.nextDrawable != null ? builder.nextDrawable : VersionUtil.drawable(context, R.drawable.ic_next);
        Drawable playlistDrawable = builder.playlistDrawable != null ? builder.playlistDrawable : VersionUtil.drawable(context, R.drawable.ic_playlist);
        Drawable albumDrawable = builder.defaultAlbumDrawable != null ? builder.defaultAlbumDrawable : VersionUtil.drawable(context, R.drawable.ic_default_album);

        int buttonPadding = builder.buttonPaddingSet ? builder.buttonPadding : context.getResources().getDimensionPixelSize(R.dimen.aw_button_padding);
        float crossStrokeWidth = builder.crossStrokeWidthSet ? builder.crossStrokeWidth : context.getResources().getDimension(R.dimen.aw_cross_stroke_width);
        float progressStrokeWidth = builder.progressStrokeWidthSet ? builder.progressStrokeWidth : context.getResources().getDimension(R.dimen.aw_progress_stroke_width);
        float shadowRadius = builder.shadowRadiusSet ? builder.shadowRadius : context.getResources().getDimension(R.dimen.aw_shadow_radius);
        float shadowDx = builder.shadowDxSet ? builder.shadowDx : context.getResources().getDimension(R.dimen.aw_shadow_dx);
        float shadowDy = builder.shadowDySet ? builder.shadowDy : context.getResources().getDimension(R.dimen.aw_shadow_dy);
        float bubblesMinSize = builder.bubblesMinSizeSet ? builder.bubblesMinSize : context.getResources().getDimension(R.dimen.aw_bubbles_min_size);
        float bubblesMaxSize = builder.bubblesMaxSizeSet ? builder.bubblesMaxSize : context.getResources().getDimension(R.dimen.aw_bubbles_max_size);
        int prevNextExtraPadding = context.getResources().getDimensionPixelSize(R.dimen.aw_prev_next_button_extra_padding);

        height = context.getResources().getDimensionPixelSize(R.dimen.aw_player_height);
        width = context.getResources().getDimensionPixelSize(R.dimen.aw_player_width);
        radius = height / 2f;
        playbackState = new PlaybackState();
        return new Configuration.Builder()
                .context(context)
                .playbackState(playbackState)
                .random(new Random())
                .accDecInterpolator(new AccelerateDecelerateInterpolator())
                .darkColor(darkColor)
                .playColor(lightColor)
                .progressColor(progressColor)
                .expandedColor(expandColor)
                .widgetWidth(width)
                .radius(radius)
                .playlistDrawable(playlistDrawable)
                .playDrawable(playDrawable)
                .prevDrawable(prevDrawable)
                .nextDrawable(nextDrawable)
                .pauseDrawable(pauseDrawable)
                .albumDrawable(albumDrawable)
                .buttonPadding(buttonPadding)
                .prevNextExtraPadding(prevNextExtraPadding)
                .crossStrokeWidth(crossStrokeWidth)
                .progressStrokeWidth(progressStrokeWidth)
                .shadowRadius(shadowRadius)
                .shadowDx(shadowDx)
                .shadowDy(shadowDy)
                .shadowColor(shadowColor)
                .bubblesMinSize(bubblesMinSize)
                .bubblesMaxSize(bubblesMaxSize)
                .crossColor(crossColor)
                .crossOverlappedColor(crossOverlappedColor)
                .build();
    }

    /**
     * Create new controller.
     *
     * @return new controller
     */
    @NonNull
    private Controller newController() {
        return new Controller() {

            @Override
            public void start() {
                playbackState.start(this);
            }

            @Override
            public void pause() {
                playbackState.pause(this);
            }

            @Override
            public void stop() {
                playbackState.stop(this);
            }

            @Override
            public int duration() {
                return playbackState.duration();
            }

            @Override
            public void duration(int duration) {
                playbackState.duration(duration);
            }

            @Override
            public int position() {
                return playbackState.position();
            }

            @Override
            public void position(int position) {
                playbackState.position(position);
            }

            @Override
            public void onControlsClickListener(@Nullable OnControlsClickListener onControlsClickListener) {
                AudioWidget.this.onControlsClickListener.onControlsClickListener(onControlsClickListener);
            }

            @Override
            public void onWidgetStateChangedListener(@Nullable OnWidgetStateChangedListener onWidgetStateChangedListener) {
                AudioWidget.this.onWidgetStateChangedListener = onWidgetStateChangedListener;
            }

            @Override
            public void albumCover(@Nullable Drawable albumCover) {
                expandCollapseWidget.albumCover(albumCover);
            }

            @Override
            public void albumCover(@Nullable Bitmap bitmap) {
                if (bitmap == null)
                    expandCollapseWidget.albumCover(null);
                else
                    expandCollapseWidget.albumCover(new BitmapDrawable(context.getResources(), bitmap));
            }
        };
    }

    /**
     * Show widget at specified position.
     *
     * @param cx center x
     * @param cy center y
     */
    public void show(int cx, int cy) {
        if (shown) {
            return;
        }
        shown = true;
        float remWidX = screenSize.x / 2 - radius;
        hiddenRemWidY = screenSize.y + radius;
        visibleRemWidY = screenSize.y - height - radius;
        show(removeWidgetView, (int) remWidX, (int) hiddenRemWidY);
        show(playPauseButton, (int) (cx - height), (int) (cy - height));
    }

    /**
     * Hide widget.
     */
    public void hide() {
        if (!shown) {
            return;
        }
        shown = false;
        released = true;
        windowManager.removeView(playPauseButton);
        try {
            windowManager.removeView(expandCollapseWidget);
        } catch (IllegalArgumentException e) {
            // widget not added to window yet
        }
        if (onWidgetStateChangedListener != null) {
            onWidgetStateChangedListener.onWidgetStateChanged(State.REMOVED);
        }
    }

    /**
     * Get current visibility state.
     *
     * @return true if widget shown on screen, false otherwise.
     */
    public boolean isShown() {
        return shown;
    }

    /**
     * Get widget controller.
     *
     * @return widget controller
     */
    @NonNull
    public Controller controller() {
        return controller;
    }

    private void show(View view, int left, int top) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;
        params.x = left;
        params.y = top;
        windowManager.addView(view, params);
    }

    private class PlayPauseButtonCallback extends TouchManager.SimpleCallback {

        private final ValueAnimator.AnimatorUpdateListener animatorUpdateListener;
        private boolean readyToRemove;

        public PlayPauseButtonCallback() {
            animatorUpdateListener = animation -> {
                if (!removeWidgetShown)
                    return;
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) removeWidgetView.getLayoutParams();
                float y = (float) animation.getAnimatedValue();
                params.y = (int) y;
                windowManager.updateViewLayout(removeWidgetView, params);
            };
        }

        @Override
        public void onClick(float x, float y) {
            playPauseButton.onClick();
            if (onControlsClickListener != null) {
                onControlsClickListener.onPlayPauseClicked();
            }
        }

        @Override
        public void onLongClick(float x, float y) {
            released = true;
            playPauseButton.enableProgressChanges(false);
            playPauseButton.postDelayed(this::checkSpaceAndShowExpanded, PlayPauseButton.PROGRESS_CHANGES_DURATION);
        }

        @Override
        public boolean canBeTouched() {
            return !playPauseButton.isAnimationInProgress();
        }

        @SuppressWarnings("deprecation")
        private void checkSpaceAndShowExpanded() {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) playPauseButton.getLayoutParams();
            int x = params.x;
            int y = params.y;
            int expandDirection;
            if (x + height > screenSize.x / 2) {
                expandDirection = ExpandCollapseWidget.DIRECTION_LEFT;
            } else {
                expandDirection = ExpandCollapseWidget.DIRECTION_RIGHT;
            }
            if (expandDirection == ExpandCollapseWidget.DIRECTION_LEFT) {
                x -= width - height * 1.5f;
            } else {
                x += height / 2f;
            }
            playPauseButton.setLayerType(View.LAYER_TYPE_NONE, null);
            show(expandCollapseWidget, x, y);
            expandCollapseWidget.expand(expandDirection);
        }

        @Override
        public void onTouched(float x, float y) {
            super.onTouched(x, y);
            released = false;
            handler.postDelayed(() -> {
                if (!released) {
                    removeWidgetShown = true;
                    ValueAnimator animator = ValueAnimator.ofFloat(hiddenRemWidY, visibleRemWidY);
                    animator.setDuration(200);
                    animator.addUpdateListener(animatorUpdateListener);
                    animator.start();
                }
            }, Configuration.LONG_CLICK_THRESHOLD);
            playPauseButton.onTouchDown();
        }

        @Override
        public void onMoved(float diffX, float diffY) {
            super.onMoved(diffX, diffY);
            boolean curReadyToRemove = isReadyToRemove();
            if (curReadyToRemove != readyToRemove) {
                readyToRemove = curReadyToRemove;
                removeWidgetView.setOverlapped(readyToRemove);
            }
        }

        @Override
        public void onReleased(float x, float y) {
            super.onReleased(x, y);
            playPauseButton.onTouchUp();
            released = true;
            if (removeWidgetShown) {
                ValueAnimator animator = ValueAnimator.ofFloat(visibleRemWidY, hiddenRemWidY);
                animator.setDuration(200);
                animator.addUpdateListener(animatorUpdateListener);
                animator.addListener(new SimpleAnimatorListener() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeWidgetShown = false;
                        if (!shown) {
                            windowManager.removeView(removeWidgetView);
                        }
                    }
                });
                animator.start();
            }
            if (isReadyToRemove()) {
                hide();
            } else {
                if (onWidgetStateChangedListener != null) {
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) playPauseButton.getLayoutParams();
                    onWidgetStateChangedListener.onWidgetPositionChanged((int) (params.x + height), (int) (params.y + height));
                }
            }
        }

        private boolean isReadyToRemove() {
            WindowManager.LayoutParams removeParams = (WindowManager.LayoutParams) removeWidgetView.getLayoutParams();
            removeBounds.set(removeParams.x, removeParams.y, removeParams.x + height, removeParams.y + height);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) playPauseButton.getLayoutParams();
            float cx = params.x + height;
            float cy = params.y + height;
            return removeBounds.contains(cx, cy);
        }
    }

    private class ExpandCollapseWidgetCallback extends TouchManager.SimpleCallback {

        @Override
        public void onTouched(float x, float y) {
            super.onTouched(x, y);
            expandCollapseWidget.onTouched(x, y);
        }

        @Override
        public void onReleased(float x, float y) {
            super.onReleased(x, y);
            expandCollapseWidget.onReleased(x, y);
        }

        @Override
        public void onClick(float x, float y) {
            expandCollapseWidget.onClick(x, y);
        }

        @Override
        public void onTouchOutside() {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) expandCollapseWidget.getLayoutParams();
            if (params.x < 0 && expandCollapseWidget.expandDirection() == ExpandCollapseWidget.DIRECTION_RIGHT) {
                expandCollapseWidget.expandDirection(ExpandCollapseWidget.DIRECTION_LEFT);
                updatePlayPauseButtonPosition();
            } else if (params.x + width > screenSize.x && expandCollapseWidget.expandDirection() == ExpandCollapseWidget.DIRECTION_LEFT) {
                expandCollapseWidget.expandDirection(ExpandCollapseWidget.DIRECTION_RIGHT);
                updatePlayPauseButtonPosition();
            }
            expandCollapseWidget.collapse();
        }

        @Override
        public boolean canBeTouched() {
            return !expandCollapseWidget.isAnimationInProgress();
        }

        @Override
        public void onMoved(float diffX, float diffY) {
            super.onMoved(diffX, diffY);
            updatePlayPauseButtonPosition();
        }

        private void updatePlayPauseButtonPosition() {
            WindowManager.LayoutParams widgetParams = (WindowManager.LayoutParams) expandCollapseWidget.getLayoutParams();
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) playPauseButton.getLayoutParams();
            if (expandCollapseWidget.expandDirection() == ExpandCollapseWidget.DIRECTION_RIGHT) {
                params.x = (int) (widgetParams.x - radius);
            } else {
                params.x = (int) (widgetParams.x + width - height - radius);
            }
            params.y = widgetParams.y;
            windowManager.updateViewLayout(playPauseButton, params);
            if (onWidgetStateChangedListener != null) {
                onWidgetStateChangedListener.onWidgetPositionChanged((int) (params.x + height), (int) (params.y + height));
            }
        }
    }

    private class OnControlsClickListenerWrapper implements OnControlsClickListener {

        private OnControlsClickListener onControlsClickListener;

        public OnControlsClickListenerWrapper onControlsClickListener(OnControlsClickListener inner) {
            this.onControlsClickListener = inner;
            return this;
        }

        @Override
        public void onPlaylistClicked() {
            if (onControlsClickListener != null) {
                onControlsClickListener.onPlaylistClicked();
            }
        }

        @Override
        public void onPreviousClicked() {
            if (onControlsClickListener != null) {
                onControlsClickListener.onPreviousClicked();
            }
        }

        @Override
        public boolean onPlayPauseClicked() {
            if (onControlsClickListener == null || onControlsClickListener.onPlayPauseClicked()) {
                if (playbackState.state() != Configuration.STATE_PLAYING) {
                    playbackState.start(AudioWidget.this);
                } else {
                    playbackState.pause(AudioWidget.this);
                }
                return true;
            }
            return false;
        }

        @Override
        public void onNextClicked() {
            if (onControlsClickListener != null) {
                onControlsClickListener.onNextClicked();
            }
        }

        @Override
        public void onAlbumClicked() {
            if (onControlsClickListener != null) {
                onControlsClickListener.onAlbumClicked();
            }
        }
    }

    public static class Builder {

        private final Context context;

        @ColorInt
        private int darkColor;
        @ColorInt
        private int lightColor;
        @ColorInt
        private int progressColor;
        @ColorInt
        private int crossColor;
        @ColorInt
        private int crossOverlappedColor;
        @ColorInt
        private int shadowColor;
        @ColorInt
        private int expandWidgetColor;
        private int buttonPadding;
        private float crossStrokeWidth;
        private float progressStrokeWidth;
        private float shadowRadius;
        private float shadowDx;
        private float shadowDy;
        private float bubblesMinSize;
        private float bubblesMaxSize;
        private Drawable playDrawable;
        private Drawable prevDrawable;
        private Drawable nextDrawable;
        private Drawable playlistDrawable;
        private Drawable defaultAlbumDrawable;
        private Drawable pauseDrawable;
        private boolean darkColorSet;
        private boolean lightColorSet;
        private boolean progressColorSet;
        private boolean crossColorSet;
        private boolean crossOverlappedColorSet;
        private boolean shadowColorSet;
        private boolean expandWidgetColorSet;
        private boolean buttonPaddingSet;
        private boolean crossStrokeWidthSet;
        private boolean progressStrokeWidthSet;
        private boolean shadowRadiusSet;
        private boolean shadowDxSet;
        private boolean shadowDySet;
        private boolean bubblesMinSizeSet;
        private boolean bubblesMaxSizeSet;
        private Interpolator accDecInterpolator;
        public boolean accDecInterpolatorSet;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        /**
         * Set dark color (playing state).
         * @param darkColor dark color
         */
        public Builder darkColor(@ColorInt int darkColor) {
            this.darkColor = darkColor;
            darkColorSet = true;
            return this;
        }

        /**
         * Set light color (paused state).
         * @param lightColor light color
         */
        public Builder lightColor(@ColorInt int lightColor) {
            this.lightColor = lightColor;
            lightColorSet = true;
            return this;
        }

        /**
         * Set progress bar color.
         * @param progressColor progress bar color
         */
        public Builder progressColor(@ColorInt int progressColor) {
            this.progressColor = progressColor;
            progressColorSet = true;
            return this;
        }

        /**
         * Set remove widget cross color.
         * @param crossColor cross color
         */
        public Builder crossColor(@ColorInt int crossColor) {
            this.crossColor = crossColor;
            crossColorSet = true;
            return this;
        }

        /**
         * Set remove widget cross color in overlapped state (audio widget overlapped remove widget).
         * @param crossOverlappedColor cross color in overlapped state
         */
        public Builder crossOverlappedColor(@ColorInt int crossOverlappedColor) {
            this.crossOverlappedColor = crossOverlappedColor;
            crossOverlappedColorSet = true;
            return this;
        }

        /**
         * Set shadow color.
         * @param shadowColor shadow color
         */
        public Builder shadowColor(@ColorInt int shadowColor) {
            this.shadowColor = shadowColor;
            shadowColorSet = true;
            return this;
        }

        /**
         * Set widget color in expanded state.
         * @param expandWidgetColor widget color in expanded state
         */
        public Builder expandWidgetColor(@ColorInt int expandWidgetColor) {
            this.expandWidgetColor = expandWidgetColor;
            expandWidgetColorSet = true;
            return this;
        }

        /**
         * Set button padding in pixels. Default value: 10dp.
         * @param buttonPadding button padding
         */
        public Builder buttonPadding(int buttonPadding) {
            this.buttonPadding = buttonPadding;
            buttonPaddingSet = true;
            return this;
        }

        /**
         * Set stroke width of remove widget. Default value: 4dp.
         * @param crossStrokeWidth stroke width of remove widget
         */
        public Builder crossStrokeWidth(float crossStrokeWidth) {
            this.crossStrokeWidth = crossStrokeWidth;
            crossStrokeWidthSet = true;
            return this;
        }

        /**
         * Set stroke width of progress bar. Default value: 4dp.
         * @param progressStrokeWidth stroke width of progress bar
         */
        public Builder progressStrokeWidth(float progressStrokeWidth) {
            this.progressStrokeWidth = progressStrokeWidth;
            progressStrokeWidthSet = true;
            return this;
        }

        /**
         * Set shadow radius. Default value: 5dp.
         * @param shadowRadius shadow radius.
         * @see Paint#setShadowLayer(float, float, float, int)
         */
        public Builder shadowRadius(float shadowRadius) {
            this.shadowRadius = shadowRadius;
            shadowRadiusSet = true;
            return this;
        }

        /**
         * Set shadow dx. Default value: 1dp.
         * @param shadowDx shadow dx
         * @see Paint#setShadowLayer(float, float, float, int)
         */
        public Builder shadowDx(float shadowDx) {
            this.shadowDx = shadowDx;
            shadowDxSet = true;
            return this;
        }

        /**
         * Set shadow dx. Default value: 1dp.
         * @param shadowDy shadow dy
         * @see Paint#setShadowLayer(float, float, float, int)
         */
        public Builder shadowDy(float shadowDy) {
            this.shadowDy = shadowDy;
            shadowDySet = true;
            return this;
        }

        /**
         * Set bubbles minimum size in pixels. Default value: 5dp.
         * @param bubblesMinSize bubbles minimum size
         */
        public Builder bubblesMinSize(float bubblesMinSize) {
            this.bubblesMinSize = bubblesMinSize;
            bubblesMinSizeSet = true;
            return this;
        }

        /**
         * Set bubbles maximum size in pixels. Default value: 10dp.
         * @param bubblesMaxSize bubbles maximum size
         */
        public Builder bubblesMaxSize(float bubblesMaxSize) {
            this.bubblesMaxSize = bubblesMaxSize;
            bubblesMaxSizeSet = true;
            return this;
        }

        /**
         * Set drawable for play button.
         * @param playDrawable drawable for play button
         */
        public Builder playDrawable(@NonNull Drawable playDrawable) {
            this.playDrawable = playDrawable;
            return this;
        }

        /**
         * Set drawable for previous track button.
         * @param prevDrawable drawable for previous track button
         */
        public Builder prevTrackDrawale(@NonNull Drawable prevDrawable) {
            this.prevDrawable = prevDrawable;
            return this;
        }

        /**
         * Set drawable for next track button.
         * @param nextDrawable drawable for next track button.
         */
        public Builder nextTrackDrawable(@NonNull Drawable nextDrawable) {
            this.nextDrawable = nextDrawable;
            return this;
        }

        /**
         * Set drawable for playlist button.
         * @param playlistDrawable drawable for playlist button
         */
        public Builder playlistDrawable(@NonNull Drawable playlistDrawable) {
            this.playlistDrawable = playlistDrawable;
            return this;
        }

        /**
         * Set drawable for default album icon.
         * @param defaultAlbumCover drawable for default album icon
         */
        public Builder defaultAlbumDrawable(@NonNull Drawable defaultAlbumCover) {
            this.defaultAlbumDrawable = defaultAlbumCover;
            return this;
        }

        /**
         * Set drawable for pause button.
         * @param pauseDrawable drawable for pause button
         */
        public Builder pauseDrawable(@NonNull Drawable pauseDrawable) {
            this.pauseDrawable = pauseDrawable;
            return this;
        }

        /**
         * Create new audio widget.
         * @return new audio widget
         * @throws IllegalStateException if size parameters have wrong values (less than zero).
         */
        public AudioWidget build() {
            if (buttonPaddingSet) {
                checkOrThrow(buttonPadding, "Button padding");
            }
            if (shadowRadiusSet) {
                checkOrThrow(shadowRadius, "Shadow radius");
            }
            if (shadowDxSet) {
                checkOrThrow(shadowDx, "Shadow dx");
            }
            if (shadowDySet) {
                checkOrThrow(shadowDy, "Shadow dy");
            }
            if (bubblesMinSizeSet) {
                checkOrThrow(bubblesMinSize, "Bubbles min size");
            }
            if (bubblesMaxSizeSet) {
                checkOrThrow(bubblesMaxSize, "Bubbles max size");
            }
            if (bubblesMinSizeSet && bubblesMaxSizeSet && bubblesMaxSize < bubblesMinSize) {
                throw new IllegalArgumentException("Bubbles max size must be greater than bubbles min size");
            }
            if (crossStrokeWidthSet) {
                checkOrThrow(crossStrokeWidth, "Cross stroke width");
            }
            if (progressStrokeWidthSet) {
                checkOrThrow(progressStrokeWidth, "Progress stroke width");
            }
            return new AudioWidget(this);
        }

        private void checkOrThrow(int number, String name) {
            if (number < 0)
                throw new IllegalArgumentException(name + " must be equals or greater zero.");
        }

        private void checkOrThrow(float number, String name) {
            if (number < 0)
                throw new IllegalArgumentException(name + " must be equals or greater zero.");
        }

    }

    /**
     * Audio widget controller.
     */
    public interface Controller {

        /**
         * Start playback.
         */
        void start();

        /**
         * Pause playback.
         */
        void pause();

        /**
         * Stop playback.
         */
        void stop();

        /**
         * Get track duration.
         *
         * @return track duration
         */
        int duration();

        /**
         * Set track duration.
         *
         * @param duration track duration
         */
        void duration(int duration);

        /**
         * Get track position.
         *
         * @return track position
         */
        int position();

        /**
         * Set track position.
         *
         * @param position track position
         */
        void position(int position);

        /**
         * Set controls click listener.
         *
         * @param onControlsClickListener controls click listener
         */
        void onControlsClickListener(@Nullable OnControlsClickListener onControlsClickListener);

        /**
         * Set widget state change listener.
         *
         * @param onWidgetStateChangedListener widget state change listener
         */
        void onWidgetStateChangedListener(@Nullable OnWidgetStateChangedListener onWidgetStateChangedListener);

        /**
         * Set album cover.
         *
         * @param albumCover album cover or null to set default one
         */
        void albumCover(@Nullable Drawable albumCover);

        /**
         * Set album cover.
         *
         * @param albumCover album cover or null to set default one
         */
        void albumCover(@Nullable Bitmap albumCover);
    }

    /**
     * Listener for control clicks.
     */
    public interface OnControlsClickListener {

        /**
         * Called when playlist button clicked.
         */
        void onPlaylistClicked();

        /**
         * Called when previous track button clicked.
         */
        void onPreviousClicked();

        /**
         * Called when play/pause button clicked.
         */
        boolean onPlayPauseClicked();

        /**
         * Called when next track button clicked.
         */
        void onNextClicked();

        /**
         * Called when album icon clicked.
         */
        void onAlbumClicked();
    }

    /**
     * Listener for widget state changes.
     */
    public interface OnWidgetStateChangedListener {

        /**
         * Called when widget state changed.
         *
         * @param state new widget state
         */
        void onWidgetStateChanged(@NonNull State state);

        /**
         * Called when position of widget is changed.
         *
         * @param cx center x
         * @param cy center y
         */
        void onWidgetPositionChanged(int cx, int cy);
    }

    public enum State {
        COLLAPSED,
        EXPANDED,
        REMOVED
    }
}
