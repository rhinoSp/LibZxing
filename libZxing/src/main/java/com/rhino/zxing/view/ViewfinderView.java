package com.rhino.zxing.view;

/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.annotation.ColorRes;
import android.support.v4.content.ContextCompat;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import com.rhino.zxing.R;
import com.rhino.zxing.camera.CameraManager;
import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final long ANIMATION_DELAY = 8L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 8;

    private static final int CORNER_RECT_WIDTH = 8;  //扫描区边角的宽
    private static final int CORNER_RECT_HEIGHT = 40; //扫描区边角的高
    private static final int SCANNER_LINE_MOVE_DISTANCE = 6;  //扫描线移动距离
    private static final int SCANNER_LINE_HEIGHT = 10;  //扫描线宽度

    private CameraManager cameraManager;
    private final Paint paint;
    private final TextPaint textPaint;
    private Bitmap resultBitmap;
    private final int maskColor;
    //    private final int resultColor;
    //扫描区域边框颜色
    private final int frameColor;
    //扫描线颜色
    private final int laserColor;
    //四角颜色
    private final int cornerColor;
    private final int resultPointColor;
    //    private int scannerAlpha;
    private final float labelTextPadding;
    private TextLocation labelTextLocation;
    //扫描区域提示文本
    private String labelText;
    //扫描区域提示文本颜色
    private int labelTextColor;
    private float labelTextSize;
    public int scannerStart = 0;
    public int scannerEnd = 0;
    private boolean isShowResultPoint;

    //扫码框宽
    private int frameWidth;
    //扫码框宽
    private int frameHeight;
    //扫码框宽线条宽度
    private float frameLineWidth;

    // 扫描边框宽
    private float cornerLineWidth;
    // 扫描边框高
    private float cornerLineHeight;


    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;

    public enum TextLocation {
        TOP(0), BOTTOM(1);

        private int mValue;

        TextLocation(int value) {
            mValue = value;
        }

        private static TextLocation getFromInt(int value) {

            for (TextLocation location : TextLocation.values()) {
                if (location.mValue == value) {
                    return location;
                }
            }

            return TextLocation.TOP;
        }


    }

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        //初始化自定义属性信息
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ViewfinderView);
        maskColor = array.getColor(R.styleable.ViewfinderView_maskColor, ContextCompat.getColor(context, R.color.viewfinder_mask));
        frameColor = array.getColor(R.styleable.ViewfinderView_frameColor, ContextCompat.getColor(context, R.color.viewfinder_frame));
        cornerColor = array.getColor(R.styleable.ViewfinderView_cornerColor, ContextCompat.getColor(context, R.color.viewfinder_corner));
        laserColor = array.getColor(R.styleable.ViewfinderView_laserColor, ContextCompat.getColor(context, R.color.viewfinder_laser));
        resultPointColor = array.getColor(R.styleable.ViewfinderView_resultPointColor, ContextCompat.getColor(context, R.color.viewfinder_result_point_color));

        labelText = array.getString(R.styleable.ViewfinderView_labelText);
        labelTextColor = array.getColor(R.styleable.ViewfinderView_labelTextColor, ContextCompat.getColor(context, R.color.viewfinder_text_color));
        labelTextSize = array.getDimension(R.styleable.ViewfinderView_labelTextSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, getResources().getDisplayMetrics()));
        labelTextPadding = array.getDimension(R.styleable.ViewfinderView_labelTextPadding, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics()));
        labelTextLocation = TextLocation.getFromInt(array.getInt(R.styleable.ViewfinderView_labelTextLocation, 0));

        isShowResultPoint = array.getBoolean(R.styleable.ViewfinderView_showResultPoint, false);

        frameWidth = array.getDimensionPixelSize(R.styleable.ViewfinderView_frameWidth, 0);
        frameHeight = array.getDimensionPixelSize(R.styleable.ViewfinderView_frameHeight, 0);
        frameLineWidth = array.getDimensionPixelSize(R.styleable.ViewfinderView_frameLineWidth, 1);

        cornerLineWidth = array.getDimensionPixelSize(R.styleable.ViewfinderView_cornerLineWidth, CORNER_RECT_WIDTH);
        cornerLineHeight = array.getDimensionPixelSize(R.styleable.ViewfinderView_cornerLineHeight, CORNER_RECT_HEIGHT);

        array.recycle();

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;

    }

    private DisplayMetrics getDisplayMetrics() {
        return getResources().getDisplayMetrics();
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    public void setLabelText(String labelText) {
        this.labelText = labelText;
    }

    public void setLabelTextColor(int color) {
        this.labelTextColor = color;
    }

    public void setLabelTextColorResource(@ColorRes int id) {
        this.labelTextColor = ContextCompat.getColor(getContext(), id);
    }

    public void setLabelTextSize(float textSize) {
        this.labelTextSize = textSize;
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {

        Rect frame;
        if (frameWidth > 0 && frameWidth < getWidth() && frameHeight > 0 && frameHeight < getHeight()) {
            //扫码框默认居中，当自定义扫码框宽高时，支持利用内距偏移
            int leftOffset = (getWidth() - frameWidth) / 2 + getPaddingLeft() - getPaddingRight();
            int topOffset = (getHeight() - frameHeight) / 2 + getPaddingTop() - getPaddingBottom();
            frame = new Rect(leftOffset, topOffset, leftOffset + frameWidth, topOffset + frameHeight);
        } else {
            if (cameraManager == null) {
                return; // not ready yet, early draw before done configuring
            }
            frame = cameraManager.getFramingRect();
        }


        if (frame == null) {
            return;
        }

        if (scannerStart == 0 || scannerEnd == 0) {
            scannerStart = frame.top;
            scannerEnd = frame.bottom - SCANNER_LINE_HEIGHT;
        }

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        drawExterior(canvas, frame, width, height);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {

            // Draw a red "laser scanner" line through the middle to show decoding is active
//            paint.setColor(laserColor);
//            paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
//            scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
            // Draw a two pixel solid black border inside the framing rect
            drawFrame(canvas, frame);
            // 绘制边角
            drawCorner(canvas, frame);
            // Draw a red "laser scanner" line through the middle to show decoding is active
            drawLaserScanner(canvas, frame);
            //绘制提示信息
            drawTextInfo(canvas, frame);
            //绘制扫码结果点
            drawResultPoint(canvas, frame);
            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY,
                    frame.left - POINT_SIZE,
                    frame.top - POINT_SIZE,
                    frame.right + POINT_SIZE,
                    frame.bottom + POINT_SIZE);
        }
    }

    //绘制文本
    private void drawTextInfo(Canvas canvas, Rect frame) {
        if (!TextUtils.isEmpty(labelText)) {
            textPaint.setColor(labelTextColor);
            textPaint.setTextSize(labelTextSize);
            textPaint.setTextAlign(Paint.Align.CENTER);
            StaticLayout staticLayout = new StaticLayout(labelText, textPaint, canvas.getWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
            if (labelTextLocation == TextLocation.BOTTOM) {
                canvas.translate(frame.left + frame.width() / 2, frame.bottom + labelTextPadding);
                staticLayout.draw(canvas);
            } else {
                canvas.translate(frame.left + frame.width() / 2, frame.top - labelTextPadding - staticLayout.getHeight());
                staticLayout.draw(canvas);
            }
        }

    }

    //绘制边角
    private void drawCorner(Canvas canvas, Rect frame) {
        paint.setColor(cornerColor);
        //左上
        canvas.drawRect(frame.left, frame.top, frame.left + cornerLineWidth, frame.top + cornerLineHeight, paint);
        canvas.drawRect(frame.left, frame.top, frame.left + cornerLineHeight, frame.top + cornerLineWidth, paint);
        //右上
        canvas.drawRect(frame.right - cornerLineWidth, frame.top, frame.right, frame.top + cornerLineHeight, paint);
        canvas.drawRect(frame.right - cornerLineHeight, frame.top, frame.right, frame.top + cornerLineWidth, paint);
        //左下
        canvas.drawRect(frame.left, frame.bottom - cornerLineWidth, frame.left + cornerLineHeight, frame.bottom, paint);
        canvas.drawRect(frame.left, frame.bottom - cornerLineHeight, frame.left + cornerLineWidth, frame.bottom, paint);
        //右下
        canvas.drawRect(frame.right - cornerLineWidth, frame.bottom - cornerLineHeight, frame.right, frame.bottom, paint);
        canvas.drawRect(frame.right - cornerLineHeight, frame.bottom - cornerLineWidth, frame.right, frame.bottom, paint);
    }

    //绘制扫描线
    private void drawLaserScanner(Canvas canvas, Rect frame) {
        paint.setColor(laserColor);
        //线性渐变
        LinearGradient linearGradient = new LinearGradient(
                frame.left, scannerStart,
                frame.left, scannerStart + SCANNER_LINE_HEIGHT,
                shadeColor(laserColor),
                laserColor,
                Shader.TileMode.MIRROR);

        paint.setShader(linearGradient);
        if (scannerStart <= scannerEnd) {
            //椭圆
            RectF rectF = new RectF(frame.left + 2 * SCANNER_LINE_HEIGHT, scannerStart, frame.right - 2 * SCANNER_LINE_HEIGHT, scannerStart + SCANNER_LINE_HEIGHT);
            canvas.drawOval(rectF, paint);
            scannerStart += SCANNER_LINE_MOVE_DISTANCE;
        } else {
            scannerStart = frame.top;
        }
        paint.setShader(null);
    }

    //处理颜色模糊
    public int shadeColor(int color) {
        String hax = Integer.toHexString(color);
        String result = "20" + hax.substring(2);
        return Integer.valueOf(result, 16);
    }

    // 绘制扫描区边框 Draw a two pixel solid black border inside the framing rect
    private void drawFrame(Canvas canvas, Rect frame) {
        paint.setColor(frameColor);
        // top
        canvas.drawRect(frame.left, frame.top, frame.right, frame.top + frameLineWidth, paint);
        // left
        canvas.drawRect(frame.left, frame.top + frameLineWidth, frame.left + frameLineWidth, frame.bottom - frameLineWidth, paint);
        // right
        canvas.drawRect(frame.right - frameLineWidth, frame.top + frameLineWidth, frame.right, frame.bottom - frameLineWidth, paint);
        // bottom
        canvas.drawRect(frame.left, frame.bottom - frameLineWidth, frame.right, frame.bottom, paint);
    }

    // 绘制模糊区域 Draw the exterior (i.e. outside the framing rect) darkened
    private void drawExterior(Canvas canvas, Rect frame, int width, int height) {
        paint.setColor(maskColor);
        // top
        canvas.drawRect(0, 0, width, frame.top, paint);
        // left
        canvas.drawRect(0, frame.top, frame.left, frame.bottom, paint);
        // right
        canvas.drawRect(frame.right, frame.top, width, frame.bottom, paint);
        // bottom
        canvas.drawRect(0, frame.bottom, width, height, paint);
    }

    //绘制扫码结果点
    private void drawResultPoint(Canvas canvas, Rect frame) {
        if (!isShowResultPoint) {
            return;
        }

        List<ResultPoint> currentPossible = possibleResultPoints;
        List<ResultPoint> currentLast = lastPossibleResultPoints;

        if (currentPossible.isEmpty()) {
            lastPossibleResultPoints = null;
        } else {
            possibleResultPoints = new ArrayList<>(5);
            lastPossibleResultPoints = currentPossible;
            paint.setAlpha(CURRENT_POINT_OPACITY);
            paint.setColor(resultPointColor);
            synchronized (currentPossible) {
                for (ResultPoint point : currentPossible) {
                    if (point.getX() < frame.left || point.getX() > frame.right ||
                            point.getY() < frame.top || point.getY() > frame.bottom) {
                        continue;
                    }
                    canvas.drawCircle(point.getX(), point.getY(), POINT_SIZE, paint);
                }
            }
        }
        if (currentLast != null) {
            paint.setAlpha(CURRENT_POINT_OPACITY / 2);
            paint.setColor(resultPointColor);
            synchronized (currentLast) {
                float radius = POINT_SIZE / 2.0f;
                for (ResultPoint point : currentLast) {
                    if (point.getX() < frame.left || point.getX() > frame.right ||
                            point.getY() < frame.top || point.getY() > frame.bottom) {
                        continue;
                    }
                    canvas.drawCircle(point.getX(), point.getY(), radius, paint);
                }
            }
        }
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    public boolean isShowResultPoint() {
        return isShowResultPoint;
    }

    /**
     * 设置显示结果点
     *
     * @param showResultPoint 是否显示结果点
     */
    public void setShowResultPoint(boolean showResultPoint) {
        isShowResultPoint = showResultPoint;
    }

//    /**
//     * Draw a bitmap with the result points highlighted instead of the live scanning display.
//     *
//     * @param barcode An image of the decoded barcode.
//     */
//    public void drawResultBitmap(Bitmap barcode) {
//        resultBitmap = barcode;
//        invalidate();
//    }

    public void addPossibleResultPoint(ResultPoint point) {
        if (isShowResultPoint) {
            List<ResultPoint> points = possibleResultPoints;
            synchronized (points) {
                points.add(point);
                int size = points.size();
                if (size > MAX_RESULT_POINTS) {
                    // trim it
                    points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
                }
            }
        }

    }

}