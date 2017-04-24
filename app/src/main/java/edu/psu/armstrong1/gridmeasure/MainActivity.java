package edu.psu.armstrong1.gridmeasure;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize GridDetectionUtils
        GridDetectionUtils.init(getFilesDir().getPath());
    }


    // Called when the user clicks the test bluetooth button
    public void testBluetooth(View view) {
        // Start BluetoothActivity
        Intent intent = new Intent(view.getContext(), BluetoothActivity.class);
        view.getContext().startActivity(intent);
    }

    // Called when the user clicks the take picture button
    public void takePicture(View view) {
        // Start TakePictureActivity without debug on
        startPictureActivity(view, false);
    }

    // Called when the user clicks the take picture button
    public void takePictureTest(View view) {
        // Start TakePictureActivity with debug on
        startPictureActivity(view, true);
    }

    // Called when user clicks the calibrate camera button
    public void calibrateCamera(View view) {
        //start CalibrateCamera Activity
        Intent intent = new Intent(view.getContext(), CalibrateCamera.class);
        view.getContext().startActivity(intent);
    }

    private void startPictureActivity(View view, boolean debugOn) {
        // Start TakePictureActivity
        Intent intent = new Intent(view.getContext(), TakePictureActivity.class);
        intent.putExtra(TakePictureActivity.DEBUG_INTENT_KEY, debugOn);
        view.getContext().startActivity(intent);
    }


}
