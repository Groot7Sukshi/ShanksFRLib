package com.sukshi.frlib.popup;


import android.app.Dialog;
import android.content.Context;
import android.gesture.Gesture;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.sukshi.frlib.R;

import java.util.List;
import java.util.Random;

public class GesturePopup extends Dialog {
    private View mDialogView;
    private AnimationSet mScaleInAnim;
    private AnimationSet mScaleOutAnim;
    private Animation mErrorInAnim;
    private AnimationSet mErrorXInAnim;
    private AnimationSet mSuccessLayoutAnimSet;
    private Animation mSuccessBowAnim;
    private TextView mTitleTextView;
    private String mTitleText;
    long popupclose, closedyepic;
    private String mContentText;
    private boolean mShowCancel;
    private String mCancelText;
    private String mConfirmText;
    private int mAlertType;
    private FrameLayout mErrorFrame;
    private ImageView mErrorX;
    private Drawable mCustomImgDrawable;
    private Button mConfirmButton;
    private OnPopupClicklistener mConfirmClickListener;
    public static int gesture1Number;

    public static final int NORMAL_TYPE = 0;
    public static final int ERROR_TYPE = 1;
    public static final int SUCCESS_TYPE = 2;
    public static final int WARNING_TYPE = 3;
    public static final int CUSTOM_IMAGE_TYPE = 4;
    public String Gesturetwo = null;

    MediaPlayer mediaPlayer;

        float  Ix, Iy;


    public static interface OnPopupClicklistener {
        public void popUpclosed();
        public void secGespopUpclosed();
    }

    public GesturePopup(Context context) {
        this(context, NORMAL_TYPE);
        this.mConfirmClickListener = (OnPopupClicklistener) context;
    }

    public GesturePopup(Context context, String secGesture){

        this(context, NORMAL_TYPE);



        this.mConfirmClickListener = (OnPopupClicklistener) context;


    }


    public GesturePopup(Context context, int alertType) {
        super(context, R.style.alert_dialog);
        setCancelable(true);
        this.mConfirmClickListener = (OnPopupClicklistener) context;

        setCanceledOnTouchOutside(false);


        Random rand = new Random();
        gesture1Number = rand.nextInt(9) + 1;



        mAlertType = alertType;
        mErrorXInAnim = (AnimationSet) OptAnimationLoader.loadAnimation(getContext(), R.anim.error_x_in);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
            List<Animation> childAnims = mErrorXInAnim.getAnimations();
            int idx = 0;
            for (; idx < childAnims.size(); idx++) {
                if (childAnims.get(idx) instanceof AlphaAnimation) {
                    break;
                }
            }
            if (idx < childAnims.size()) {
                childAnims.remove(idx);
            }
        }


        mSuccessBowAnim = OptAnimationLoader.loadAnimation(getContext(), R.anim.success_bow_roate);
        mSuccessLayoutAnimSet = (AnimationSet) OptAnimationLoader.loadAnimation(getContext(), R.anim.success_mask_layout);
        mScaleInAnim = (AnimationSet) OptAnimationLoader.loadAnimation(getContext(), R.anim.dialog_scale_in);
        mScaleOutAnim = (AnimationSet) OptAnimationLoader.loadAnimation(getContext(), R.anim.dialog_scale_out);




        mScaleOutAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mDialogView.setVisibility(View.GONE);
                mDialogView.post(new Runnable() {
                    @Override
                    public void run() {


                        GesturePopup.super.dismiss();


                    }
                });
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        new CountDownTimer(2000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onFinish() {

                popupclose = System.currentTimeMillis();
                GesturePopup.super.dismiss();
                mConfirmClickListener.secGespopUpclosed();


// TODO Auto-generated method stub
            }
        }.start();
        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onFinish() {

                popupclose = System.currentTimeMillis();
                mConfirmClickListener.popUpclosed();


// TODO Auto-generated method stub
            }
        }.start();
    }


    public void shuttersound(){


        mediaPlayer = MediaPlayer.create(getContext(), R.raw.shutter);
        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 100, 0);
        mediaPlayer.start();

    }



    public void showPopup (){


        GesturePopup.super.show();

    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alert_dialog);
        mDialogView = getWindow().getDecorView().findViewById(android.R.id.content);
        mTitleTextView = (TextView) findViewById(R.id.title_text);
        mErrorFrame = (FrameLayout) findViewById(R.id.error_frame);
        mErrorX = (ImageView) mErrorFrame.findViewById(R.id.error_x);

        if (gesture1Number == 1) {

            mErrorX.setImageResource(R.drawable.left_close);
            mTitleTextView.setText("Tilt your Face to left with your eyes closed");



        } else if (gesture1Number == 2) {

            mErrorX.setImageResource(R.drawable.right_close);

            mTitleTextView.setText("Tilt your Face to right with your eyes closed");



        } else if (gesture1Number == 3) {

            mErrorX.setImageResource(R.drawable.stright_close);
            mTitleTextView.setText("Keep your Face straight with your eyes closed");



        } else if (gesture1Number == 4) {

            mErrorX.setImageResource(R.drawable.left_eyes);

            mTitleTextView.setText("Tilt your Face to left with your eyes open");



        } else if (gesture1Number == 5) {

            mErrorX.setImageResource(R.drawable.right_eyes);

            mTitleTextView.setText("Tilt your Face to right with your eyes open");

        } else if (gesture1Number == 6) {
            mErrorX.setImageResource(R.drawable.stright_eyes);

            mTitleTextView.setText("Keep your Face straight with your eyes open");



        }else if (gesture1Number == 7) {
            mErrorX.setImageResource(R.drawable.left_wink);

            mTitleTextView.setText("Tilt your Face to left and wink with any eye");



        }else if (gesture1Number == 8) {
            mErrorX.setImageResource(R.drawable.right_wink);

            mTitleTextView.setText("Tilt your Face to right and wink with any eye");



        }else if (gesture1Number == 9) {
            mErrorX.setImageResource(R.drawable.straight_wink_mask);

            mTitleTextView.setText("Keep your Face straight and wink with any eye");



        }
        setTitleText(mTitleText);
        setContentText(mContentText);
        showCancelButton(mShowCancel);
        setCancelText(mCancelText);
        setConfirmText(mConfirmText);
        changeAlertType(mAlertType, true);
    }

    private void restore() {
        mErrorFrame.setVisibility(View.GONE);

        mErrorFrame.clearAnimation();
        mErrorX.clearAnimation();
    }

    private void playAnimation() {
        if (mAlertType == ERROR_TYPE) {
            mErrorFrame.startAnimation(mErrorInAnim);
            mErrorX.startAnimation(mErrorXInAnim);
        } else if (mAlertType == SUCCESS_TYPE) {

        }
    }


    public int gesture (){

        return gesture1Number;

    }

    public Bitmap getFaceCroppedBitmap(Bitmap bitmap) {
        closedyepic = System.currentTimeMillis();


          Ix = bitmap.getWidth();
                Iy = bitmap.getHeight();
        //we can't get cropped bitmap is bitmap is null or cropArea is null

        //crop bitmap with calculated cropArea
        return Bitmap.createScaledBitmap(bitmap, 450, (int) (450 * Iy / Ix), true);
    }

    public String get_time() {


        String delay = String.valueOf(closedyepic - popupclose);

        return delay;


    }


    private void changeAlertType(int alertType, boolean fromCreate) {
        mAlertType = alertType;
        // call after created views
        if (mDialogView != null) {
            if (!fromCreate) {
                // restore all of views state before switching alert type
                restore();
            }
            switch (mAlertType) {
                case ERROR_TYPE:
                    mErrorFrame.setVisibility(View.VISIBLE);
                    break;

                case WARNING_TYPE:
                    break;
                case CUSTOM_IMAGE_TYPE:
                    setCustomImage(mCustomImgDrawable);
                    break;
            }
            if (!fromCreate) {
                playAnimation();
            }
        }
    }

    public int getAlerType() {
        return mAlertType;
    }

    public void changeAlertType(int alertType) {
        changeAlertType(alertType, false);
    }


    public String getTitleText() {
        return mTitleText;
    }

    public GesturePopup setTitleText(String text) {
        mTitleText = text;
        if (mTitleTextView != null && mTitleText != null) {
            mTitleTextView.setText(mTitleText);
        }
        return this;
    }

    public GesturePopup setCustomImage(Drawable drawable) {
        mCustomImgDrawable = drawable;

        return this;
    }

    public GesturePopup setCustomImage(int resourceId) {
        return setCustomImage(getContext().getResources().getDrawable(resourceId));
    }

    public String getContentText() {
        return mContentText;
    }

    public GesturePopup setContentText(String text) {
        mContentText = text;

        return this;
    }

    public boolean isShowCancelButton() {
        return mShowCancel;
    }

    public GesturePopup showCancelButton(boolean isShow) {
        mShowCancel = isShow;

        return this;
    }

    public String getCancelText() {
        return mCancelText;
    }

    public GesturePopup setCancelText(String text) {
        mCancelText = text;

        return this;
    }

    public String getConfirmText() {
        return mConfirmText;
    }

    public GesturePopup setConfirmText(String text) {
        mConfirmText = text;
        if (mConfirmButton != null && mConfirmText != null) {
            mConfirmButton.setText(mConfirmText);
        }
        return this;
    }

    public GesturePopup setCancelClickListener(OnPopupClicklistener listener) {
        return this;
    }


    public GesturePopup setConfirmClickListener(OnPopupClicklistener listener) {
        mConfirmClickListener = listener;
        return this;
    }

    protected void onStart() {
        mDialogView.startAnimation(mScaleInAnim);
        playAnimation();
    }

    public void dismiss() {
        mDialogView.startAnimation(mScaleOutAnim);
    }


}
