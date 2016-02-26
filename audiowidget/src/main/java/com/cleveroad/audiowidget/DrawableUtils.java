package com.cleveroad.audiowidget;

import android.view.View;

/**
 * Helpful utils class.
 */
class DrawableUtils {

	private DrawableUtils() {}

	/**
	 * Trapeze function.
	 * @param t current value
	 * @param a value at <b>aT</b> point of time
	 * @param aT first point
	 * @param b value at <b>bT</b> point of time
	 * @param bT second point
	 * @param c value at <b>cT</b> point of time
	 * @param cT third point
	 * @param d value at <b>dT</b> point of time
	 * @param dT forth point
	 * @return calculated value
	 */
	public static float trapeze(float t, float a, float aT, float b, float bT, float c, float cT, float d, float dT) {
		if (t < aT) {
			return a;
		}
		if (t >= aT && t < bT) {
			float norm = normalize(t, aT, bT);
			return a + norm * (b - a);
		}
		if (t >= bT && t < cT) {
			float norm = normalize(t, bT, cT);
			return b + norm * (c - b);
		}
		if (t >= cT && t <= dT) {
			float norm = normalize(t, cT, dT);
			return c + norm * (d - c);
		}
		return d;
	}

	public static float customFunction(float t, float ... pairs) {
		if (pairs.length == 0 || pairs.length % 2 != 0) {
			throw new IllegalArgumentException("Length of pairs must be multiple by 2 and greater than zero.");
		}
		if (t < pairs[1]) {
			return pairs[0];
		}
		int size = pairs.length / 2;
		for (int i=0; i<size - 1; i++) {
			float a = pairs[2 * i];
			float b = pairs[2 * (i + 1)];
			float aT = pairs[2 * i + 1];
			float bT = pairs[2 * (i + 1) + 1];
			if (t >= aT && t <= bT) {
				float norm = normalize(t, aT, bT);
				return a + norm * (b - a);
			}
		}
		return pairs[pairs.length - 2];
	}

	/**
	 * Normalize value between minimum and maximum.
	 * @param val value
	 * @param minVal minimum value
	 * @param maxVal maximum value
	 * @return normalized value in range <code>0..1</code>
	 * @throws IllegalArgumentException if value is out of range <code>[minVal, maxVal]</code>
	 */
	public static float normalize(float val, float minVal, float maxVal) {
		if (val < minVal)
			return 0;
		if (val > maxVal)
			return 1;
		float t = (val - minVal) / (maxVal - minVal);
		return t;
	}

	/**
	 * Quadratic Bezier curve.
	 * @param t time
	 * @param p0 start point
	 * @param p1 control point
	 * @param p2 end point
	 * @return point on Bezier curve at some time <code>t</code>
	 */
	public static float quad(float t, float p0, float p1, float p2) {
		return (float) (p0 * Math.pow(1 - t, 2) + p1 * 2 * t * (1 - t) + p2 * t * t);
	}

	/**
	 * Rotate point P around center point C.
	 * @param pX x coordinate of point P
	 * @param pY y coordinate of point P
	 * @param cX x coordinate of point C
	 * @param cY y coordinate of point C
	 * @param angleInDegrees rotation angle in degrees
	 * @return new x coordinate
	 */
	public static float rotateX(float pX, float pY, float cX, float cY, float angleInDegrees) {
		double angle = Math.toRadians(angleInDegrees);
		return (float) (Math.cos(angle) * (pX - cX) - Math.sin(angle) * (pY - cY) + cX);
	}

	/**
	 * Rotate point P around center point C.
	 * @param pX x coordinate of point P
	 * @param pY y coordinate of point P
	 * @param cX x coordinate of point C
	 * @param cY y coordinate of point C
	 * @param angleInDegrees rotation angle in degrees
	 * @return new y coordinate
	 */
	public static float rotateY(float pX, float pY, float cX, float cY, float angleInDegrees) {
		double angle = Math.toRadians(angleInDegrees);
		return (float) (Math.sin(angle) * (pX - cX) + Math.cos(angle) * (pY - cY) + cY);
	}

	/**
	 * Checks if value belongs to range <code>[start, end]</code>
	 * @param value value
	 * @param start start of range
	 * @param end end of range
	 * @return true if value belongs to range, false otherwise
	 */
	public static boolean isBetween(float value, float start, float end) {
		if (start > end) {
			float tmp = start;
			start = end;
			end = tmp;
		}
		return value >= start && value <= end;
	}

	public static float between(float val, float min, float max) {
		return Math.min(Math.max(val, min), max);
	}

	public static int between(int val, int min, int max) {
		return Math.min(Math.max(val, min), max);
	}

	/**
	 * Enlarge value from startValue to endValue
	 * @param startValue start size
	 * @param endValue end size
	 * @param time time of animation
	 * @return new size value
	 */
	public static float enlarge(float startValue, float endValue, float time) {
		if (startValue > endValue)
			throw new IllegalArgumentException("Start size can't be larger than end size.");
		return startValue + (endValue - startValue) * time;
	}

	/**
	 * Reduce value from startValue to endValue
	 * @param startValue start size
	 * @param endValue end size
	 * @param time time of animation
	 * @return new size value
	 */
	public static float reduce(float startValue, float endValue, float time) {
		if (startValue < endValue)
			throw new IllegalArgumentException("End size can't be larger than start size.");
		return endValue + (startValue - endValue) * (1 - time);
	}

	public static float centerX(View view) {
		return (view.getLeft() + view.getRight()) / 2f;
	}

	public static float centerY(View view) {
		return (view.getTop() + view.getBottom()) / 2f;
	}
}
