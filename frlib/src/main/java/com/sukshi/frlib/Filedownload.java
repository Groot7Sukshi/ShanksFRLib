package com.sukshi.frlib;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Filedownload {

    private static final String TAG = "Download Task";
    private Context context;
    private OndownloadComplete mConfirmClickListener;
        public ProgressDialog dialog;



    private String downloadUrl = "https://drive.google.com/uc?export=download&id=1ElSoMbG0XHhlGRPRAGXOfK3_SHab90o6", downloadFileName = "shape_predictor_5_face_landmarks.dat";
    private ProgressDialog progressDialog;

    public String downloadFileName1 = "dlib_face_recognition_resnet_model_v1.dat";
    public String downloadUrl1 = "https://drive.google.com/uc?export=download&id=1hRS37StO1XHTjwuD4ec6OnRtM-p2xDAS";


    public static interface OndownloadComplete {
        public void popUpclosed();
    }

    public Filedownload(Context context) {
        this.context = context;


        Log.e(TAG, downloadFileName);

        //Start Downloading Task
        new DownloadingTask().execute();
    }








    private class DownloadingTask extends AsyncTask<String, Integer, String> {

        File apkStorage = null;
        File outputFile = null, outPutfile1 = null;

        @Override
        protected void onPreExecute() {


            dialog = new ProgressDialog(context);
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

            dialog.setMessage("Donwloading");
            dialog.show();

            super.onPreExecute();
        }






        @Override
        protected void onPostExecute(String result) {
            try {
                if (outputFile != null && outPutfile1 !=null) {
                    dialog.dismiss();
                    Toast.makeText(context, "Downloaded Successfully", Toast.LENGTH_SHORT).show();

                    mConfirmClickListener.popUpclosed();

                } else {

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {

                        }
                    }, 3000);

                    Log.e(TAG, "Download Failed");

                }
            } catch (Exception e) {
                e.printStackTrace();

                //Change button text if exception occurs

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {

                    }
                }, 3000);
                Log.e(TAG, "Download Failed with Exception - " + e.getLocalizedMessage());

            }


            super.onPostExecute(result);
        }


       /* @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);
            progressDialog.setProgress(progress[0]);
        }
*/
        @Override
        protected String doInBackground(String... arg0) {
            try {
                URL url = new URL(downloadUrl);//Create Download URl
                HttpURLConnection c = (HttpURLConnection) url.openConnection();//Open Url Connection
                c.setRequestMethod("GET");//Set Request Method to "GET" since we are grtting data
                c.connect();//connect the URL Connection
                int lengthOfFile = c.getContentLength();

                URL url1 = new URL(downloadUrl1);//Create Download URl
                HttpURLConnection c1 = (HttpURLConnection) url1.openConnection();//Open Url Connection
                c1.setRequestMethod("GET");//Set Request Method to "GET" since we are grtting data
                c1.connect();//connect the URL Connection
                int lengthOfFile1 = c.getContentLength();



                //If Connection response is not OK then show Logs
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + c.getResponseCode()
                            + " " + c.getResponseMessage());

                }
                if (c1.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + c1.getResponseCode()
                            + " " + c1.getResponseMessage());

                }


                //Get File if SD card is present
                if (new CheckForSDCard().isSDCardPresent()) {

                    apkStorage = new File(
                            Environment.getExternalStorageDirectory() + "/"
                                    + "M-here");
                } else
                    Toast.makeText(context, "Oops!! There is no SD Card.", Toast.LENGTH_SHORT).show();

                //If File is not present create directory
                if (!apkStorage.exists()) {
                    apkStorage.mkdir();
                    Log.e(TAG, "Directory Created.");
                }

                outputFile = new File(apkStorage, downloadFileName);//Create Output file in Main File
                outPutfile1 = new File(apkStorage, downloadFileName1);//Create Output file in Main File



                //Create New File if not present
                if (!outputFile.exists()) {
                    outputFile.createNewFile();

                    FileOutputStream fos = new FileOutputStream(outputFile);//Get OutputStream for NewFile Location
                    InputStream is = c.getInputStream();//Get InputStream for connection
                    byte[] buffer = new byte[4096];//Set buffer type
                    int count;//init length
                    long total = 0;
                    while ((count = is.read(buffer)) != -1) {
                        total += count;

                        publishProgress(Integer.valueOf("" + (int) ((total * 100) / lengthOfFile)));


                        fos.write(buffer, 0, count);//Write new file
                    }
                    fos.close();
                    is.close();


                    Log.e(TAG, "File Created");
                }else {


                }

                if (!outPutfile1.exists()){

                    outPutfile1.createNewFile();
                    FileOutputStream fos1 = new FileOutputStream(outPutfile1);

                    InputStream is1 = c1.getInputStream();


                    byte[] buffer1 = new byte[4096];//Set buffer type
                    int count1;//init length
                    long total1 = 0;


                    while ((count1 = is1.read(buffer1)) != -1) {
                        total1 += count1;

                        publishProgress(Integer.valueOf("" + (int) ((total1 * 100) / lengthOfFile1)));


                        fos1.write(buffer1, 0, count1);//Write new file
                    }


                    fos1.close();
                    is1.close();
                }else {



                }


                //Close all connection after doing task



            } catch (Exception e) {

                //Read exception if something went wrong
                e.printStackTrace();
                outputFile = null;
                Log.e(TAG, "Download Error Exception " + e.getMessage());
            }



            return null;
        }
    }


}
