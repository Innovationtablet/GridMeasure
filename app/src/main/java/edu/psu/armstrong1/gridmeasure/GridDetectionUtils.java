package edu.psu.armstrong1.gridmeasure;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.CharucoBoard;
import org.opencv.aruco.Dictionary;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hfs50 on 3/20/2017.
 */

class GridDetectionUtils {

    private static final String TAG = "GridDetectionUtils";

    // FIXME: 4/12/2017 Why do these show up as "Cannot resolve corresponding JNI function..." in AS?
    public static native String stringFromJNI(long inMatAddr, long outMatAddr);
    private static native void calibrateWithCharucoNative(String[] imageFilepaths);
    private static native void calibrateWithCharucoMatsNative(Mat[] images);
    private static native float[] measurementsFromOutlineNative(Mat image, float[] points);
    public static native void init(String fileStoragePath);
    public static native void undistort(long inMatAddr, long outMatAddr );
    public static native void drawAxis(long inMatAddr, long outMatAddr );

    /**
     *  Wrapper for native call.
     */
    public static void  calibrateWithCharuco(String[] imageFilepaths) {
        calibrateWithCharucoNative(imageFilepaths);
    }

    public static void calibrateWithCharuco(Mat[] images) {
        calibrateWithCharucoMatsNative(images);
    }

    /**
     *  Wrapper for native call.
     */
    public static List<PointF> measurementsFromOutline(Mat image, List<PointF> points) {
        float[] pointsArr = new float[points.size()*2];
        for (int i = 0; i < points.size(); i++) {
            pointsArr[i*2] = points.get(i).x;
            pointsArr[i*2+1] =  points.get(i).y;
        }

        float[] worldPointsArr = measurementsFromOutlineNative(image, pointsArr);

        ArrayList<PointF> out = new ArrayList<>();
        for (int i = 0; i < worldPointsArr.length; i += 2) {
            out.add(new PointF(worldPointsArr[i], worldPointsArr[i+1]));
        }
        return out;
    }

    /**
     *
     * @param in
     * @return a Bitmap test image for now - easiest way for me to debug what the code's doing.
     */
    static Bitmap getPossibleGridCorners(Bitmap in) {

        Mat inMat, grayMat, cannyMat, lines;
        int threshold1 = 80, threshold2 = 100;
        int houghThreshold = 50, minLineSize = 20, lineGap = 20;

        inMat = new Mat();
        Utils.bitmapToMat(in, inMat);

        grayMat = new Mat();
        Imgproc.cvtColor(inMat, grayMat, Imgproc.COLOR_RGB2GRAY);

        cannyMat = new Mat();
        Imgproc.Canny(inMat, cannyMat, threshold1, threshold2);

        lines = new Mat();
        Imgproc.HoughLinesP(cannyMat, lines, 1, Math.PI/180, houghThreshold, minLineSize, lineGap);

        for (int x = 0; x < lines.cols(); x++)
        {
            double[] vec = lines.get(0, x);
            double x1 = vec[0],
                    y1 = vec[1],
                    x2 = vec[2],
                    y2 = vec[3];
            Point start = new Point(x1, y1);
            Point end = new Point(x2, y2);

            Imgproc.line(inMat, start, end, new Scalar(255,0,0), 3);

        }

        Bitmap bmp = Bitmap.createBitmap(inMat.width(), inMat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inMat, bmp);

        return bmp;
        //return new ArrayList<>();
    }

    static Bitmap findCharuco(Bitmap in) {
        Mat inMat;

        inMat = new Mat();
        Utils.bitmapToMat(in, inMat);

        Mat outMat = new Mat();
        stringFromJNI(inMat.getNativeObjAddr(), outMat.getNativeObjAddr());
        Bitmap bmp = Bitmap.createBitmap(outMat.width(), outMat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outMat, bmp);

        return bmp;

/*
        Mat inMat;

        inMat = new Mat();
        Utils.bitmapToMat(in, inMat);

        Imgproc.cvtColor(inMat, inMat, Imgproc.COLOR_RGB2GRAY);

        Imgproc.resize(inMat, inMat, new Size(0,0), .1f, .1f, Imgproc.INTER_NEAREST);

        // this is all from https://github.com/opencv/opencv_contrib/blob/3.1.0/modules/aruco/tutorials/charuco_detection/charuco_detection.markdown
        //cv::aruco::Dictionary dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);
        //cv::aruco::CharucoBoard board = cv::aruco::CharucoBoard::create(5, 7, 0.04, 0.02, dictionary);

        int squaresX = 5;
        int squaresY = 7;
        int squareLength = 100;
        int markerLength = 50;
        int margins = squareLength - markerLength;

        int borderBits = 1;

        Dictionary dict = Aruco.getPredefinedDictionary(Aruco.DICT_7X7_1000);

        Size imageSize = new Size(squaresX * squareLength + 2 * margins, squaresY * squareLength + 2 * margins);

        CharucoBoard board = CharucoBoard.create(squaresX, squaresY, (float)squareLength,
                (float)markerLength, dict);

        // show created board
        //Mat boardImage = new Mat();
        //board.draw(imageSize, boardImage, margins, borderBits);

        //Bitmap bmp = Bitmap.createBitmap(boardImage.width(), boardImage.height(), Bitmap.Config.ARGB_8888);
        //Utils.matToBitmap(boardImage, bmp);

        ArrayList<Mat> markerCorners = new ArrayList<>();
        Mat markerIds = new Mat();
        Aruco.detectMarkers(inMat,dict,markerCorners,markerIds);

        if(markerIds.rows() > 0) {
            Mat charucoCorners = new Mat();
            Mat charucoIds = new Mat();
            Aruco.interpolateCornersCharuco(markerCorners, markerIds, inMat, board, charucoCorners, charucoIds);

            //draw
            Aruco.drawDetectedCornersCharuco(inMat, charucoCorners);
        }

        Bitmap bmp = Bitmap.createBitmap(inMat.width(), inMat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inMat, bmp);

        return bmp;*/
    }
}
