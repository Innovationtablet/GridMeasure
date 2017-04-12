package edu.psu.armstrong1.gridmeasure;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class CalculationActivity extends AppCompatActivity {
    // Constants
    public static final float CUTTER_MAX_X = 24;                // max width of the tile cutter
    public static final float CUTTER_MAX_Y = 24;                // max height of the tile cutter
    private static final String THREAD_KEY = "WORKER_THREAD";   // key to savedInstanceBundle for workerThreadMade

    // Variables
    String currentPhotoPath = null;                             // path to last picture taken
    ArrayList<PointF> polygonPoints;                            // the points of the PolygonView
    Context appContext = null;                                  // the application context (used if this is used as a class not an activity)
    boolean workerThreadMade = false;                           // whether the worker thread to do the calculations has been made yet
    private Handler handler;                                    // Handler used to communicate with the UI thread from the worker thread

    // Constructor for when using this as an activity
    public CalculationActivity() {
        // empty constructor
    }

    // Constructor for when using this class for corner/transform functions
    public CalculationActivity(Context context) {
        appContext = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculation);

        // Get the bitmap and points
        currentPhotoPath = getIntent().getStringExtra(TakePictureActivity.PHOTO_PATH_INTENT_KEY);
        polygonPoints = (ArrayList<PointF>) getIntent().getExtras().get(TakePictureActivity.POINTS_INTENT_KEY);

        // Only create a worker thread if one hasn't been made yet
        if (!workerThreadMade) {
            // Start a worker thread to do calculation
            (new Thread(new workerThread(polygonPoints))).start();
            handler = new Handler();
            workerThreadMade = true;
        }
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the local variables
        savedInstanceState.putString(TakePictureActivity.PHOTO_PATH_INTENT_KEY, currentPhotoPath);
        savedInstanceState.putSerializable(TakePictureActivity.POINTS_INTENT_KEY, polygonPoints);
        savedInstanceState.putBoolean(THREAD_KEY, workerThreadMade);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore state info and photo path
        super.onRestoreInstanceState(savedInstanceState);
        currentPhotoPath = savedInstanceState.getString(TakePictureActivity.PHOTO_PATH_INTENT_KEY);
        polygonPoints = (ArrayList<PointF>) savedInstanceState.getSerializable(TakePictureActivity.POINTS_INTENT_KEY);
        workerThreadMade = savedInstanceState.getBoolean(THREAD_KEY);
    }


    public void calculationsDone(ArrayList<PointF> transformedPoints) {
        // Go to ShowMeasurementActivity
        Intent intent = new Intent(getApplicationContext(), ShowMeasurementActivity.class);
        intent.putExtra(TakePictureActivity.POINTS_INTENT_KEY, transformedPoints);
        startActivity(intent);
    }


    // Called when the user clicks the New Job button
    public void dispatchNewJob(View view) {
        // Go back to the main page and clear out intermediate screens
        Intent intent = new Intent(view.getContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    public ArrayList<Integer> findCorners(ArrayList<PointF> points, boolean findAll) {
        ArrayList<Integer> retCorners = new ArrayList<>();          // array to hold acceptable corners
        ArrayList<Integer> corners90Deg = new ArrayList<>();        // array to hold possible corner that are 90 degrees
        TreeMap<Float, Integer> cornersAcute = new TreeMap<>();     // map to hold possible corners that are acute (in order by the dot product)

        // Variables used for computation
        float dotProd;
        int size = points.size();
        boolean noCornerFound = true;
        int curCorner;
        PointF corner = null;

        // Find acute and 90-degree angles
        for (int i = 0; i < size; i++) {
            Log.d("CalculationActivity", "Check point: " + points.get((i + 1) % size));

            // Get the dot product of the two vectors from the center point (i+1) to its neighbors
            dotProd = getDotProduct(points.get((i + 1) % size), points.get((i + 2) % size), points.get(i));

            // Check the dot product
            if (dotProd == 0) {
                // 90 degree angle results in dotProd = 0
                corners90Deg.add((i + 1) % size);
                Log.d("CalculationActivity", "  Found 90 degree angle");
            } else if (dotProd > 0) {
                // dotProd is positive only if the angle is acute
                cornersAcute.put(dotProd, (i + 1) % size);
                Log.d("CalculationActivity", "  Found acute angle");
            }
        }

        // Check whether corners are acceptable corners
        //   (If the corner were (0,0), can all other points fit in the first quadrant?)
        // Loop until we're out of corners to test if we're finding all corners, or until we find a corner/rule out all possible corners
        while ((noCornerFound || findAll) && (!corners90Deg.isEmpty() || !cornersAcute.isEmpty())) {
            boolean curCornerGood = true;

            // Get the next corner to try (first go through 90 deg corners)
            if (!corners90Deg.isEmpty()) {
                Log.d("CalculationActivity", "Checking next 90 degree corner");
                curCorner = corners90Deg.get(0);
                corners90Deg.remove(0);
            } else {
                // No more 90 degree corners, try the acute angle in increasing dot product magnitude
                // Note: smaller dot product magnitude generally implies the angle is closer to 90 degrees
                Log.d("CalculationActivity", "No more 90 degree corners - using next acute corner");
                curCorner = cornersAcute.firstEntry().getValue();
                cornersAcute.remove(cornersAcute.firstKey());
            }

            // Make sure all points are within 90 degrees of the two vectors formed from the corner to its neighbors
            // The left vector is referring to the vector between the curCorner and (curCorner+1) points
            // The right vector is referring to the vector between the curCorner and (curCorner-1) points
            // The center vector is referring to the vector between the curCorner and i points
            int i = (curCorner + 2) % size;
            corner = points.get(curCorner);
            PointF leftPoint = points.get((curCorner + 1) % size);
            PointF rightPoint = points.get((curCorner - 1 + size) % size);

            // Loop through all other points that aren't the corner or its immediate neighbors
            Log.d("CalculationActivity", "Checking corner: " + corner);
            while (curCornerGood && (i != (curCorner - 1 + size) % size)) {
                PointF curPt = points.get(i);
                // Check the dot product between the left/right and center vectors
                if (getDotProduct(corner, leftPoint, curPt) < 0 ||
                        getDotProduct(corner, rightPoint, curPt) < 0) {
                    // Larger than 90 degrees -> not a good corner
                    Log.d("CalculationActivity", "Point: " + curPt + " is not within 90 degrees of the corner");
                    curCornerGood = false;
                }
                // else - both angles are at most 90 degrees

                // Go to the next point
                i = (i + 1) % size;
            }

            if (curCornerGood) {
                // Corner passed all tests
                noCornerFound = false;
                retCorners.add(curCorner);
                Log.i("CalculationActivity", "Found acceptable corner: " + corner);
            }
        }

        // Check if a corner was found
        if (noCornerFound) {
            // None found
            Log.e("CalculationActivity", "No acceptable corners found!");
            return null;
        } else {
            // Return the indices of the corners found
            Log.d("CalculationActivity", "Found " + retCorners.size() + " corner(s)");
            return retCorners;
        }
    }

    // Rotates and translates points so that points[cornerIndex] is at (0,0) and all points
    //   are in the TR quadrant (positive x and y values)
    // Note: order should be +- 1 to decide which way to index into points (increasing or decreasing)
    public ArrayList<PointF> transformPoints(ArrayList<PointF> points, int cornerIndex, int order,
                                             boolean alignYAxis, boolean checkCutterBoundary) {
        float roundingConst = 10000;                            // rounding factor [keep log(roundingConst) decimal places]
                                                                //   used to avoid extremely small negative numbers that should be rounded to 0
        ArrayList<PointF> transformedPts = new ArrayList<>();
        int size = points.size();

        Log.d("CalculationActivity", "Transforming points based on corner: " + points.get(cornerIndex));

        // Define the translation vector to make the corner at (0,0)
        double h = -points.get(cornerIndex).x;
        double k = -points.get(cornerIndex).y;

        // Get the angle of rotation to be applied
        double angle;
        if (alignYAxis) {
            // Align first CW point on the y-axis
            Log.d("CalculationActivity", "Aligning first CW point on y-axis");
            // Get the angle between the y-axis and the first CW point (after translation)
            angle = Math.atan2(1, 0) - Math.atan2(points.get((cornerIndex + order + size) % size).y + k,
                    points.get((cornerIndex + order + size) % size).x + h);
        } else {
            // Align first CCW point on the x-axis
            Log.d("CalculationActivity", "Aligning first CCW point on x-axis");

            // Get the angle between the x-axis and the first CCW point (after translation)
            angle = Math.atan2(0, 1) - Math.atan2(points.get((cornerIndex - order + size) % size).y + k,
                    points.get((cornerIndex - order + size) % size).x + h);
        }

        // Pre-compute sin/cos of the angle
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);

        // Transform each point (start at corner and work clockwise)
        Log.d("CalculationActivity", "Transforming each point with (h,k) = (" + h + ", " + k + "), angle = " + angle);
        for (int i = 0; Math.abs(i) < size; i = i + order) {
            PointF point = points.get((i + cornerIndex + size) % size);
            float x = point.x;
            float y = point.y;

            // Calculate the new points
            // Derived from: https://www.cs.mtu.edu/~shene/COURSES/cs3621/NOTES/geometry/geo-tran.html
            // Note: This is translating then rotating the points
            float xNew = (float) (Math.round((cosA * x - sinA * y + h * cosA - k * sinA) * roundingConst) / roundingConst);
            float yNew = (float) (Math.round((sinA * x + cosA * y + h * sinA + k * cosA) * roundingConst) / roundingConst);

            // Make sure new point is valid
            if (xNew < 0 || yNew < 0) {
                // Invalid point (not in TR quadrant) - stop computing points
                Log.w("CalculationActivity", "Transformation yields point not in TR quadrant: (" + xNew + ", " + yNew + ")");
                return null;
            } else if (checkCutterBoundary && (xNew > CUTTER_MAX_X || yNew > CUTTER_MAX_Y)) {
                // Invalid point (outside cutter bounds) - stop computing points
                Log.w("CalculationActivity", "Transformation yields point outside cutter bounds: (" + xNew + ", " + yNew + ")");
                return null;
            } else {
                // Valid point - add it to the new array
                transformedPts.add(new PointF(xNew, yNew));
            }
        }

        return transformedPts;
    }

    public ArrayList<PointF> findCornerAndTransformPoints(ArrayList<PointF> points, boolean checkCutterBoundary) {
        ArrayList<PointF> transformedPts = null;
        ArrayList<Integer> corners = findCorners(points, true);
        int cornerIndex;

        // Make sure a corner was found
        if (corners == null) {
            Log.e("CalculationActivity", "No corner found. Can't transform points!");
            Toast.makeText(appContext, R.string.error_noAcceptableCornerFound, Toast.LENGTH_LONG).show();
            return null;
        }

        // Try to rotate points into the TR quadrant based on a corner
        while (!corners.isEmpty() && transformedPts == null) {
            // Take the first corner
            cornerIndex = corners.get(0);
            corners.remove(0);

            // Try to transform points with CW order being increasing index, align y-axis
            Log.d("CalculationActivity", "Assuming increasing index is CW order. Rotating/translating shape...");
            transformedPts = transformPoints(points, cornerIndex, 1, true, checkCutterBoundary);

            // Check if transform was successful
            if (transformedPts == null) {
                // Transform not successful - try aligning x-axis
                transformedPts = transformPoints(points, cornerIndex, 1, false, checkCutterBoundary);
            }

            // Check if transform was successful
            if (transformedPts == null) {
                // Transform not successful - try CW order being decreasing index
                Log.d("CalculationActivity", "Assuming decreasing index is CW order. Rotating/translating shape...");
                transformedPts = transformPoints(points, cornerIndex, -1, true, checkCutterBoundary);
            }

            // Check if transform was successful
            if (transformedPts == null) {
                // Transform not successful - try aligning x-axis
                transformedPts = transformPoints(points, cornerIndex, -1, false, checkCutterBoundary);
            }
        }

        // Log and show a toast if transformation failed
        if (transformedPts == null) {
            Log.e("CalculationActivity", "Could not transform points based on a corner point");
            Toast.makeText(appContext, R.string.error_noAcceptableTransformation, Toast.LENGTH_LONG).show();
        }

        return transformedPts;
    }

    private static float getDotProduct(PointF centerPt, PointF a, PointF b) {
        float x1, y1, x2, y2, dotProd;

        // Get the vectors (x1,y1) and (x2,y2) that go from centerPt to a and b
        x1 = a.x - centerPt.x;
        y1 = a.y - centerPt.y;
        x2 = b.x - centerPt.x;
        y2 = b.y - centerPt.y;

        // Get the dot product of the two vectors
        dotProd = (x1 * x2) + (y1 * y2);
        Log.d("CalculationAcitivty", "Dot product: (" + x1 + ", " + y1 + ") . (" + x2 + ", " + y2 + ") = " + dotProd);
        return dotProd;
    }

    final class workerThread implements Runnable {
        private ArrayList<PointF> points;

        public workerThread(ArrayList<PointF> pointsList) {
            points = pointsList;
        }

        public void run() {
            // Convert the points to real-world dimensions.
            Mat image = Imgcodecs.imread(currentPhotoPath, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
            // The code in this file works on ArrayLists, while the code in GridDetectionUtils
            // works on Lists. We do a conversion here.
            ArrayList<PointF> transformedPoints = new ArrayList<>(GridDetectionUtils.measurementsFromOutline(image, polygonPoints));

            // Transform the points if ChArUco detection was successful
            if (transformedPoints != null) {
                transformedPoints = findCornerAndTransformPoints(transformedPoints, false);
            } else {
                handler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), R.string.error_charucoFailed, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }

            // Go to the next activity if transformation was successful
            if (transformedPoints != null) {
                calculationsDone(transformedPoints);
            } else {
                handler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), R.string.error_transformFailed, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
        }
    };
}
