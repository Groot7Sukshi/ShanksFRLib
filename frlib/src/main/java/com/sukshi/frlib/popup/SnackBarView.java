package com.sukshi.frlib.popup;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sukshi.frlib.R;

public class SnackBarView extends View {


    public OnSnackbarDismissed mConfirmClickListener;

    RelativeLayout relativeLayout;





    public SnackBarView( Context context, RelativeLayout relativeLayout) {

        super(context);

        this.mConfirmClickListener = (OnSnackbarDismissed) context;
this.relativeLayout = relativeLayout;

        showSnackbar(relativeLayout);
    }

    public static interface OnSnackbarDismissed {
        public void snackbarClosed();
    }

    public SnackBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SnackBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }





    public void showSnackbar(RelativeLayout relativeLayout){


      Snackbar snackbar = Snackbar.make(relativeLayout, "Close Eyes", Snackbar.LENGTH_SHORT);

        android.support.design.widget.Snackbar.SnackbarLayout layout = (android.support.design.widget.Snackbar.SnackbarLayout) snackbar.getView();
        TextView textView = (TextView) layout.findViewById(android.support.design.R.id.snackbar_text);

        textView.setTextSize(20);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        } else {
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();


        snackbar.addCallback(new Snackbar.Callback() {

            @Override
            public void onDismissed(Snackbar snackbar, int event) {


                new CountDownTimer(1000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void onFinish() {

                        mConfirmClickListener.snackbarClosed();


// TODO Auto-generated method stub
                    }
                }.start();



            }

            @Override
            public void onShown(Snackbar snackbar) {



            }
        });


    }


}
