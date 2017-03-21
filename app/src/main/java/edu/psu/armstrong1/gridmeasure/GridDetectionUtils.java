package edu.psu.armstrong1.gridmeasure;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

/**
 * Created by hfs50 on 3/20/2017.
 */

class GridDetectionUtils {

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
}
