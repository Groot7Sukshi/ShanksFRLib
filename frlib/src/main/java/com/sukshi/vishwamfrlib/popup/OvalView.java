package com.sukshi.vishwamfrlib.popup;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import com.sukshi.vishwamfrlib.R;

/**
 * Created by reenath on 09/07/18.
 */

public class OvalView extends View {


    private Bitmap bitmap;
    private Canvas cnvs;
    private Paint p = new Paint();
    private Paint transparentPaint = new Paint();;
    private Paint semiTransparentPaint = new Paint();;
    private Paint eyesPaint = new Paint();
    private int parentWidth;
    private int parentHeight;
    private int radius = 100;

    int height;
    int width;
    public RectF oval1, oval2, oval3;



    public OvalView(Context context) {
        super(context);
        init();
    }

    public OvalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
        transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        semiTransparentPaint.setColor(getResources().getColor(R.color.maskColor));
        semiTransparentPaint.setAlpha(90);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        DisplayMetrics metrics = new DisplayMetrics();
        ((Activity)getContext()).getWindowManager().getDefaultDisplay().getMetrics(metrics);

        height = metrics.heightPixels;
        width = metrics.widthPixels;
        bitmap = Bitmap.createBitmap(parentWidth, parentHeight, Bitmap.Config.ARGB_8888);
        cnvs = new Canvas(bitmap);


        if (width <= 800){


            oval1 = new RectF((width/2) - 200, (height/2) - 310, (width/2) + 200, (height/2) + 310);
            oval2 = new RectF((width/2) - 150, (height/2) - 90, (width/2) - 30, (height/2) - 20);
            oval3 = new RectF((width/2) + 30, (height/2) - 90, (width/2) + 150, (height/2) - 20);

        } else if (width > 1000 && width < 1400){

            oval1 = new RectF((width/2) - 320, (height/2) - 450, (width/2) + 320, (height/2) + 450);
            oval2 = new RectF((width/2) - 270, (height/2) - 140, (width/2) - 50, (height/2) - 40);
            oval3 = new RectF((width/2) + 50, (height/2) - 140, (width/2) + 270, (height/2) - 40);
        }


        eyesPaint.setColor(getResources().getColor(R.color.maskColor));
        eyesPaint.setStyle(Paint.Style.STROKE);
        eyesPaint.setStrokeWidth(5);

        cnvs.drawRect(0, 0, cnvs.getWidth(), cnvs.getHeight(), semiTransparentPaint);
        cnvs.drawOval(oval1,transparentPaint );
        cnvs.drawOval(oval2, eyesPaint);
        cnvs.drawOval(oval3, eyesPaint);

        //cnvs.drawCircle(parentWidth / 2, parentHeight / 2, radius, transparentPaint);
        canvas.drawBitmap(bitmap, 0, 0, p);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        parentHeight = MeasureSpec.getSize(heightMeasureSpec);

        this.setMeasuredDimension(parentWidth, parentHeight);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    }



