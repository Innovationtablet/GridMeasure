package edu.psu.armstrong1.gridmeasure;

import android.graphics.PointF;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by hfs50 on 4/8/2017.
 */
@RunWith(AndroidJUnit4.class)
public class ComputerVisionUtilsTest {

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("computer-vision-utils-lib");
    }

    @BeforeClass
    public static void setupOpenCV() {
        BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(InstrumentationRegistry.getContext()) {
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

        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, InstrumentationRegistry.getContext(), mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Test
    public void testCalibrate() throws IOException {
        InputStream is1 = this.getClass().getClassLoader().getResourceAsStream("img1.jpg");
        InputStream is2 = this.getClass().getClassLoader().getResourceAsStream("img2.jpg");
        InputStream is3 = this.getClass().getClassLoader().getResourceAsStream("img3.jpg");

        Mat[] images = new Mat[3];
        images[0] = new Mat();
        byte[] bytes = new byte[0];

        images[0] = Imgcodecs.imdecode(new MatOfByte(IOUtils.toByteArray(is1)), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        images[1] = Imgcodecs.imdecode(new MatOfByte(IOUtils.toByteArray(is2)), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        images[2] = Imgcodecs.imdecode(new MatOfByte(IOUtils.toByteArray(is3)), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);

        ComputerVisionUtils.calibrateWithCharuco(images);

        ArrayList<PointF> list = new ArrayList<>();
        list.add(new PointF(915,3625));
        list.add(new PointF(624,1075));
        list.add(new PointF(2147,1097));
        list.add(new PointF(2310,3142));

        List<PointF> out = ComputerVisionUtils.measurementsFromOutline(images[0], list);

    }


}
