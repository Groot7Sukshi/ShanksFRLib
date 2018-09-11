package com.sukshi.frlib;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Arrays;

/**
 * Created by reenath on 22/05/18.
 */

public class FaceCrop {


    private Context context;
    private Bitmap bitmap;
    int nx1 = 0;
    int ny1 = 0;
    int nw1 = 0;
    int nh1 = 0;
    float Sx, Sy, Ix, Iy;
    public float[] arrayOfpixels;
    String ovalPixelsJsonArrayString;
    JSONArray jsonPixelArrayy;

    MediaPlayer mediaPlayer;


    public FaceCrop(){

        getFaceCroppedBitmap();
    }




    public FaceCrop(Context context, Bitmap bitmap) {
        this.bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        this.context = context;
        getFaceCroppedBitmap();
    }


    public Bitmap getFaceCroppedBitmap() {
        if (bitmap == null) {
            throw new RuntimeException("Initialize FaceDetectionCrop using FaceDetectionCrop.initialize() before using it.");
        }

        DisplayMetrics metrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(metrics);

        Sx = metrics.widthPixels;
        Sy = metrics.heightPixels;

        Ix = bitmap.getWidth();
        Iy = bitmap.getHeight();
        float Rx = Ix / Sx, Ry = Iy / Sy, R450x = 450 / Ix, R450y = 450 / Ix;

        float[] pixelArray = {((Sx / 2) - 150), ((Sx / 2) - 30), ((Sx / 2) + 30), ((Sx / 2) + 150), ((Sy / 2) - 90), ((Sy / 2) - 20)};

        if (Sx <= 800) {

            arrayOfpixels = new float[]{((Sx / 2) - 150) * Rx * R450x, ((Sx / 2) - 30) * Rx * R450x, ((Sx / 2) + 30) * Rx * R450x, ((Sx / 2) + 150) * Rx * R450x, ((Sy / 2) - 90) * Ry * R450y, ((Sy / 2) - 20) * Ry * R450y};

        } else if (Sx > 1000 && Sx < 1400) {

            arrayOfpixels = new float[]{((Sx / 2) - 270) * Rx * R450x, ((Sx / 2) - 50) * Rx * R450x, ((Sx / 2) + 50) * Rx * R450x, ((Sx / 2) + 270) * Rx * R450x, ((Sy / 2) - 140) * Ry * R450y, ((Sy / 2) - 40) * Ry * R450y};
        }

         jsonPixelArrayy = new JSONArray(Arrays.asList(arrayOfpixels));
        JSONArray ovalPixelsJsonArray = null;
        try {
            ovalPixelsJsonArray = jsonPixelArrayy.getJSONArray(0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ovalPixelsJsonArrayString = ovalPixelsJsonArray.toString();
        return Bitmap.createScaledBitmap(bitmap, 450, (int) (450 * Iy / Ix), true);
    }

    public void shuttersound(){


        mediaPlayer = MediaPlayer.create(context, R.raw.shutter);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 100, 0);
        mediaPlayer.start();

    }



    public String ovalPixels() {

        return ovalPixelsJsonArrayString;

    }

    public static synchronized FaceCrop faceCrop(Context context, Bitmap bitmap) {
        return new FaceCrop(context, bitmap);
    }

}
