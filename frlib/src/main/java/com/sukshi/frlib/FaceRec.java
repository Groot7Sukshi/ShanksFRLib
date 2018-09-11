package com.sukshi.frlib;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.sukshi.frlib.popup.FileUtils;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by reenath on 18-06-03.
 */
public class FaceRec {
    private static final String TAG = "dlib";
    private Activity activity;
    private Context context1;
    int nx1 = 0;
    int ny1 = 0;
    int nw1 = 0;
    int nh1 = 0;
    float  Ix, Iy;
    public float[] pixelArrayy;
    public ArrayList<Point> landmarks;

    public Bitmap processedBitmap;
  public   String landmarksString = "Empty";
    public boolean fitornot = true;

    MediaPlayer mediaPlayer;


    // accessed by native methods
    @SuppressWarnings("unused")
    private long mNativeFaceRecContext;
    private String dir_path = "";
    private int embedding_size = 128;
    private String log_tag = "FaceRec";

    static {
        try {
            System.loadLibrary("android_dlib");
            jniNativeClassInit();
            Log.d(TAG, "jniNativeClassInit success");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "library not found");
        }



    }


    public FaceRec(String sample_dir_path, Context context) {
        dir_path = sample_dir_path;
        jniInit(dir_path);
        this.context1 = context;
    }

    @Nullable
    @WorkerThread
    public void train() {
        jniTrain();
        return;
    }


    @Nullable
    public void copyfiles(){

        new initRecAsync().execute();
    }


    private class initRecAsync extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
        }

        protected Void doInBackground(Void... args) {
            // create dlib_rec_example directory in sd card and copy model files
            File folder = new File(Constants.getDLibDirectoryPath());
            boolean success = false;
            if (!folder.exists()) {
                success = folder.mkdirs();
            }
            if (success) {
                File image_folder = new File(Constants.getDLibImageDirectoryPath());
                image_folder.mkdirs();
                if (!new File(Constants.getFaceDescriptorModelPath()).exists()) {
                    FileUtils.copyFileFromRawToOthers(context1, R.raw.shape_predictor_5_face_landmarks, Constants.getFaceShapeModelPath());
                }
                if (!new File(Constants.getFaceDescriptorModelPath()).exists()) {
                    FileUtils.copyFileFromRawToOthers(context1, R.raw.dlib_face_recognition_resnet_model_v1, Constants.getFaceDescriptorModelPath());
                }
            } else {
                //Log.d(TAG, "error in setting dlib_rec_example directory");
            }
            return null;
        }

        protected void onPostExecute(Void result) {

        }
    }



    @Nullable
    @WorkerThread
    public List<VisionDetRet> recognize(@NonNull Bitmap bitmap) {
        VisionDetRet[] detRets = jniBitmapRec(bitmap);
        return Arrays.asList(detRets);
    }

    @Nullable
    @WorkerThread
    public List<VisionDetRet> detect(@NonNull Bitmap bitmap) {
        VisionDetRet[] detRets = jniBitmapDetect(bitmap);
        return Arrays.asList(detRets);
    }



    @Nullable
    @WorkerThread
    public List<VisionDetRet> get_landmarks(@NonNull Bitmap bitmap) {
        VisionDetRet[] detRets = jniBitmapLandMark(bitmap);
        return Arrays.asList(detRets);
    }

    @Nullable
    @WorkerThread
    public ArrayList<ArrayList<Float> > get_face_signature(@NonNull Bitmap bitmap) {

       /* if (bitmap != null){
       //  processedBitmap =    getFaceCroppedBitmap(bitmap);
          //  getLandmarks(processedBitmap);
        }*/
        float[] faceEncodings = jniBitmapFaceEncoding(bitmap);
        if (faceEncodings.length % embedding_size != 0) {
            Log.e(log_tag, "error in calculating embeddings");
        }

        ArrayList<ArrayList<Float> > result = new ArrayList<ArrayList<Float> >();

        for (int i=0; i<faceEncodings.length/embedding_size; i++) {
            ArrayList<Float> a = new ArrayList<Float>();
            for (int j=0; j<embedding_size; j++) {
                a.add(faceEncodings[i*embedding_size + j]);
            }
            result.add(a);
        }
        return result;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        release();
    }


    public Bitmap getFaceCroppedBitmap(Bitmap bitmap) {
        //we can't get cropped bitmap is bitmap is null or cropArea is null
        if (bitmap == null) {
            throw new RuntimeException("Initialize FaceRec using FaceRec.initialize() before using it.");
        }

        Ix = bitmap.getWidth();
        Iy = bitmap.getHeight();
        return Bitmap.createScaledBitmap(bitmap, 450, (int) (450 * Iy / Ix), true);
    }

    public Point getCentre(Point point1, Point point2) {

        int x1 = point1.x;
        int y1 = point1.y;
        int x2 = point2.x;
        int y2 = point2.y;

        Point centre = new Point((x1 + x2) / 2, (y1 + y2) / 2);

        return centre;
    }

    public boolean checkMask( Bitmap bitmap){

        Ix = bitmap.getWidth();
        Iy = bitmap.getHeight();
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context1
                .getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);
       float Sx = metrics.widthPixels;
       float Sy = metrics.heightPixels;
        float Rx = Ix / Sx, Ry = Iy / Sy, R450x = 450 / Ix, R450y = 450 / Ix;

        // Log.e("Rx, Ry and R450", Rx + ", " + Ry + ", " + R450x);


        if (Sx <= 800) {

            pixelArrayy = new float[]{((Sx / 2) - 150) * Rx * R450x, ((Sx / 2) - 30) * Rx * R450x, ((Sx / 2) + 30) * Rx * R450x, ((Sx / 2) + 150) * Rx * R450x, ((Sy / 2) - 90) * Ry * R450y - 25, ((Sy / 2) - 20) * Ry * R450y};

        } else if (Sx > 1000 && Sx < 1400) {

            pixelArrayy = new float[]{((Sx / 2) - 270) * Rx * R450x, ((Sx / 2) - 50) * Rx * R450x, ((Sx / 2) + 50) * Rx * R450x, ((Sx / 2) + 270) * Rx * R450x, ((Sy / 2) - 140) * Ry * R450y - 25, ((Sy / 2) - 40) * Ry * R450y};
        }


        Point point1 = new Point(landmarks.get(0));
        Point point2 = new Point(landmarks.get(1));
        Point point3 = new Point(landmarks.get(2));
        Point point4 = new Point(landmarks.get(3));

        Point centre1 = getCentre(point1, point2);

        Point centre2 = getCentre(point3, point4);

        if ((pixelArrayy[0] < centre2.x) && (centre2.x < pixelArrayy[1]) && (pixelArrayy[2] < centre1.x) && (centre1.x < pixelArrayy[3]) && (pixelArrayy[4] < centre1.y) && (centre1.y < pixelArrayy[5]) && (pixelArrayy[4] < centre2.y) && (centre2.y < pixelArrayy[5])) {

            fitornot = true;

            return fitornot;
        } else {

            fitornot = false;

            return fitornot;
        }

    }






    public String getLandmarks(Bitmap bitmap) {

        List<VisionDetRet> results = get_landmarks(bitmap);

        if (results.size() != 0 ){
           landmarks = results.get(0).getFaceLandmarks();


            landmarksString = landmarks.toString();


            String regex = "\\s*\\bPoint\\b\\s*";
            landmarksString = landmarksString.replaceAll(regex, "");
            String regex1 = "\\s*\\(\\b\\s*";
            landmarksString = landmarksString.replaceAll(regex1, "[");
            String regex2 = "\\s*\\)\\s*";
            landmarksString = landmarksString.replaceAll(regex2, "]");
            String regex3 = "\\s*\\b\\s*";

            landmarksString = landmarksString.replaceAll(regex3, "");
        }


        return landmarksString;


    }


    public void shuttersound(){


       MediaPlayer mediaPlayer = MediaPlayer.create(context1, R.raw.shutter);
        AudioManager audioManager = (AudioManager) context1.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 100, 0);
        mediaPlayer.start();

    }





    public void release() {
        jniDeInit();
    }

    @Keep
    private native static void jniNativeClassInit();

    @Keep
    private synchronized native int jniInit(String sample_dir_path);

    @Keep
    private synchronized native int jniDeInit();

    @Keep
    private synchronized native int jniTrain();

    @Keep
    private synchronized native VisionDetRet[] jniBitmapDetect(Bitmap bitmap);

    @Keep
    private synchronized native float[] jniBitmapFaceEncoding(Bitmap bitmap);

    @Keep
    private synchronized native VisionDetRet[] jniBitmapLandMark(Bitmap bitmap);

    @Keep
    private synchronized native VisionDetRet[] jniBitmapRec(Bitmap bitmap);




}
