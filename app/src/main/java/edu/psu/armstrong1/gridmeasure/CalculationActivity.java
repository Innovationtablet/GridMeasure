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
import java.util.TreeMap;

public class CalculationActivity extends AppCompatActivity {
    // Constants
    public static final float CUTTER_MAX_X = 24;                    // max width of the tile cutter
    public static final float CUTTER_MAX_Y = 24;                    // max height of the tile cutter
    public static final float EXTEND_EDGES_AMOUNT = (float) 0.25;   // default amount to extend the tile's edges (inches)
    private static final String THREAD_KEY = "WORKER_THREAD";       // key to savedInstanceBundle for workerThreadMade
    public static String EXTENDED_PTS_INTENT_KEY = "EXTENDED";      // key to the intent for the polygon points after extending edges

    // Variables
    String currentPhotoPath = null;                             // path to last picture taken
    ArrayList<PointF> polygonPoints;                            // the points of the PolygonView
    Context appContext = null;                                  // the application context (used if this is used as a class not an activity)
    boolean workerThreadMade = false;                           // whether the worker thread to do the calculations has been made yet
    private Handler handler;                                    // Handler used to communicate with the UI thread from the worker thread
    int cornerUsed, orderUsed;                                  // the transform variables used to successfully transform the points
    boolean alignYUsed;

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


        // Get the value of workerThreadMade from the saved bundle if there is one
        if (savedInstanceState != null) {
            workerThreadMade = savedInstanceState.getBoolean(THREAD_KEY, false);
            currentPhotoPath = savedInstanceState.getString(TakePictureActivity.PHOTO_PATH_INTENT_KEY);
            polygonPoints = (ArrayList<PointF>) savedInstanceState.getSerializable(TakePictureActivity.POINTS_INTENT_KEY);
        } else {
            // Get the bitmap and points from the intent
            currentPhotoPath = getIntent().getStringExtra(TakePictureActivity.PHOTO_PATH_INTENT_KEY);
            polygonPoints = (ArrayList<PointF>) getIntent().getExtras().get(TakePictureActivity.POINTS_INTENT_KEY);
        }

        // Only create a worker thread if one hasn't been made yet
        if (!workerThreadMade) {
            // Start a worker thread to do calculation
            (new Thread(new workerThread(polygonPoints))).start();
            handler = new Handler();
            workerThreadMade = true;
        }

        appContext = getApplicationContext();
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the local variables
        savedInstanceState.putString(TakePictureActivity.PHOTO_PATH_INTENT_KEY, currentPhotoPath);
        savedInstanceState.putSerializable(TakePictureActivity.POINTS_INTENT_KEY, polygonPoints);
        savedInstanceState.putBoolean(THREAD_KEY, workerThreadMade);

        super.onSaveInstanceState(savedInstanceState);
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
        ArrayList<Integer> corners90Deg = new ArrayList<>();        // array to hold possible corners that are 90 degrees
        TreeMap<Float, Integer> cornersAcute = new TreeMap<>();     // map to hold possible corners that are acute (in order by the dot product)

        // Variables used for computation
        float dotProd;                          // result of a dot product
        int size = points.size();               // size of the points list
        boolean noCornerFound = true;           // flag denoting whether a corner was found
        int curCorner;                          // index into points of the current corner being examined
        PointF corner = null;                   // current corner being examined

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
            // Else obtuse angle - not acceptable corner
        }

        // Check whether "corners" are acceptable corners
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
    // Note: order should be +- 1 to decide which way to index into points (increasing or decreasing) for CW order
    public ArrayList<PointF> transformPoints(ArrayList<PointF> points, int cornerIndex, int order,
                                             boolean alignYAxis, boolean checkCutterBoundary, boolean checkTRQuadrant) {

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
            float xNew = Math.round((cosA * x - sinA * y + h * cosA - k * sinA) * roundingConst) / roundingConst;
            float yNew = Math.round((sinA * x + cosA * y + h * sinA + k * cosA) * roundingConst) / roundingConst;

            // Make sure new point is valid
            if (checkTRQuadrant && (xNew < 0 || yNew < 0)) {
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

    // Note: alignInputOutputArrays denotes whether PointF's at the same indices of input and output are matching points
    public ArrayList<PointF> findCornerAndTransformPoints(ArrayList<PointF> points, boolean checkCutterBoundary) {
        ArrayList<PointF> transformedPts = null;
        int order = 1;      // assume CW order (at first)

        // Find acceptable corners
        ArrayList<Integer> corners = findCorners(points, true);

        // Make sure a corner was found
        if (corners == null) {
            Log.e("CalculationActivity", "No corner found. Can't transform points!");
            Toast.makeText(appContext, R.string.error_noAcceptableCornerFound, Toast.LENGTH_LONG).show();
            return null;
        }

        // Try to rotate points into the TR quadrant based on a corner
        int cornerIndex = 0;
        boolean alignY = true;
        while (!corners.isEmpty() && transformedPts == null) {
            // Take the first corner and try to transform the points
            cornerIndex = corners.get(0);
            corners.remove(0);

            // Try to transform points with CW order being increasing index, align y-axis
            Log.d("CalculationActivity", "Assuming increasing index is CW order. Rotating/translating shape...");
            transformedPts = transformPoints(points, cornerIndex, order, alignY, checkCutterBoundary, true);

            // Check if transform was successful
            if (transformedPts == null) {
                // Transform not successful - try aligning x-axis
                alignY = false;
                transformedPts = transformPoints(points, cornerIndex, order, alignY, checkCutterBoundary, true);
            }

            // Check if transform was successful
            if (transformedPts == null) {
                // Transform not successful - try CW order being decreasing index
                Log.d("CalculationActivity", "Assuming decreasing index is CW order. Rotating/translating shape...");
                order = -1;
                alignY = true;
                transformedPts = transformPoints(points, cornerIndex, order, alignY, checkCutterBoundary, true);
            }

            // Check if transform was successful
            if (transformedPts == null) {
                // Transform not successful - try aligning x-axis
                alignY = false;
                transformedPts = transformPoints(points, cornerIndex, order, alignY, checkCutterBoundary, true);
            }
        }

        // Log and show a toast if transformation failed
        if (transformedPts == null) {
            Log.e("CalculationActivity", "Could not transform points based on a corner point");
            Toast.makeText(appContext, R.string.error_noAcceptableTransformation, Toast.LENGTH_LONG).show();
        }

        // Store the corner and order used to align points
        if (transformedPts != null) {
            cornerUsed = cornerIndex;
            orderUsed = order;
            alignYUsed = alignY;
        }

        return transformedPts;
    }

    public ArrayList<PointF> transformPointsUsingPreviousOptions(ArrayList<PointF> points) {
        return transformPoints(points, cornerUsed, orderUsed, alignYUsed, false, false);
    }

    public ArrayList<PointF> getExtendedPoints(ArrayList<PointF> points, float amount) {
        return findCornerAndTransformPoints(extendEdges(points, amount), false);
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

    // Note: amount should be in inches
    public static ArrayList<PointF> extendEdges(ArrayList<PointF> points, float amount) {
        ArrayList<PointF> transformedPoints = new ArrayList<>();
        ArrayList<PointF> edgeVectors = new ArrayList<>();
        ArrayList<PointF> normalVectors = new ArrayList<>();
        int size = points.size();

        // Get the edge vectors
        // Note: edge[i] goes from point i to i+1
        for (int i = 0; i < size; i++) {
            PointF startPt = points.get(i);
            PointF endPt = points.get((i + 1) % size);
            edgeVectors.add(new PointF(endPt.x - startPt.x, endPt.y - startPt.y));
        }

        // Find the normal vector that points inward for each edge using ray casting
        for (int i = 0; i < size; i++) {
            Log.d("CalculationActivity", "Getting normal vector for edge " + i);
            int intersections = 0;
            PointF curPt = points.get(i);
            PointF normalVector = new PointF(edgeVectors.get(i).y, -edgeVectors.get(i).x);
            PointF midPtOnEdge = new PointF((curPt.x + points.get((i + 1) % size).x) / 2,
                                            (curPt.y + points.get((i + 1) % size).y) / 2);

            /*
            Ray Casting Algorithm:
                Checks how many edges the vector goes through. If that number is even, the vector
                  must point outward. If that number is odd, the vector points inward. When the
                  vector goes from outside to inside the polygon, it must go out again at some point.
                  Thus, if it started pointing outside the polygon, it would go in then out of the
                  polygon x times, yielding an even number of intersections. If the vector started
                  pointing inside the polygon, it would go out of the polygon once and then in and
                  out of the polygon x times, yielding an odd number of intersections.
             */
            Log.d("CalculationActivity", "Testing normal vector " + normalVector);
            for (int j = 0; j < size; j++) {
                // Don't check the edge the vector starts on
                if (i != j && vectorIntersectsEdge(midPtOnEdge, normalVector, points.get(j), edgeVectors.get(j))) {
                    // Vector intersects with edge j
                    Log.d("CalculationActivity", "Intersects with edge " + j);
                    intersections++;
                }
            }

            if (intersections % 2 == 1) {
                // Odd number of intersections -> vector points inward -> reverse normal vector
                Log.d("CalculationActivity", "Vector pointed inward. Reversing it...");
                normalVector.set(-edgeVectors.get(i).y, edgeVectors.get(i).x);
            }

            // Normalize the length of the vector
            normalVector.set(amount * normalVector.x / normalVector.length(),
                             amount * normalVector.y / normalVector.length());

            // Add the outward-facing normal vector to the array
            Log.d("CalculationActivity", "Normalized vector for edge " + i + ": " + normalVector);
            normalVectors.add(normalVector);
        }


        // Move each edge out by amount
        //   Move edge[i] by normalVectors[i] and edge[i-1] by normalVectors[i-1]
        //   Calculate the amount of movement for point[i] (point between edges i and i-1)
        //   Intersecting the bumped out edges yields the new corner point
        // Note: Simply moving each corner point by the normal vectors of the adjacent edges yields
        //   an incorrect result for all non-right angles
        for (int i = 0; i < size; i++) {
            PointF curCorner = points.get(i);
            PointF normalVector_nextEdge = normalVectors.get(i);
            PointF normalVector_prevEdge = normalVectors.get((i - 1 + size) % size);

            // Move the corner by each of the normal vectors (bump out each edge)
            PointF newCornerForNextEdge = new PointF(curCorner.x + normalVector_nextEdge.x, curCorner.y + normalVector_nextEdge.y);
            PointF newCornerForPrevEdge = new PointF(curCorner.x + normalVector_prevEdge.x, curCorner.y + normalVector_prevEdge.y);

            // Intersect the bumped out edges to find the new corner point
            Log.d("CalculationActivity", "Bumping out corner " + curCorner + " by " + normalVector_nextEdge + " and " + normalVector_prevEdge);
            transformedPoints.add(intersectionOfLines(newCornerForNextEdge, edgeVectors.get(i), newCornerForPrevEdge, edgeVectors.get((i - 1 + size) % size)));
        }

        return transformedPoints;
    }


    // Note: Assumes that the lines actually intersect
    public static PointF intersectionOfLines(PointF line1Pt, PointF line1Slope, PointF line2Pt, PointF line2Slope) {
        // Check if one line is vertical
        if (line1Slope.x == 0) {
            // line1 is vertical, line2 is not -> get y value of intersection

            // Equation of line2: y = m*(x - line2Pt.x) + line2Pt.y
            float y_intersection = (line2Slope.y / line2Slope.x) * (line1Pt.x - line2Pt.x) + line2Pt.y;

            return new PointF(line1Pt.x, y_intersection);

        } else if (line2Slope.x == 0) {
            // line2 is vertical, line1 is not -> get y value of intersection

            // Equation of line1: y = m * (x - line1Pt.x) + line1Pt.y
            float y_intersection = (line1Slope.y / line1Slope.x) * (line2Pt.x - line1Pt.x) + line1Pt.y;

            return new PointF(line2Pt.x, y_intersection);
        }

        // Else: Neither line is vertical

        // Calculate the slope
        float slope_line1 = line1Slope.y / line1Slope.x;
        float slope_line2 = line2Slope.y / line2Slope.x;

        // Lines intersect - find the intersection
        // Note: Equation came from setting y=mx+b for each line equal and solving for x
        float x_intersection = (slope_line2 * line2Pt.x - slope_line1 * line1Pt.x + line1Pt.y - line2Pt.y) / (slope_line2 - slope_line1);

        // Get the y-value from equation of line1: y = m * (x - line1Pt.x) + line1Pt.y
        float y_intersection = (line1Slope.y / line1Slope.x) * (x_intersection - line1Pt.x) + line1Pt.y;

        return new PointF(x_intersection, y_intersection);
    }


    // Note: Checks if vector intersects edge including edgeStart but not edgeEnd
    // Note: edgeEnd is assumed to be (edgeBegin + edgeSlope)
    private static boolean vectorIntersectsEdge(PointF vectorStart, PointF vectorSlope, PointF edgeStart, PointF edgeSlope) {
        // Check if both lines are vertical/horizontal
        if ((vectorSlope.x == 0 && edgeSlope.x == 0) || (vectorSlope.y == 0 && edgeSlope.y == 0)) {
            return false;
        }

        // Check if one line is vertical
        if (vectorSlope.x == 0) {
            // vector is vertical, edge is not -> get y value of intersection
            float edgeYMin = Math.min(edgeStart.y, edgeStart.y + edgeSlope.y);
            float edgeYMax = Math.max(edgeStart.y, edgeStart.y + edgeSlope.y);

            // Equation of edge line: y = m*(x - edgeStart.x) + edgeStart.y
            float y_intersection = (edgeSlope.y / edgeSlope.x) * (vectorStart.x - edgeStart.x) + edgeStart.y;

            return ((edgeYMin < y_intersection) && (y_intersection < edgeYMax)) || y_intersection == edgeStart.y;

        } else if (edgeSlope.x == 0) {
            // edge is vertical, vector is not -> get y value of intersection
            float edgeYMin = Math.min(edgeStart.y, edgeStart.y + edgeSlope.y);
            float edgeYMax = Math.max(edgeStart.y, edgeStart.y + edgeSlope.y);

            // Equation of vector line: y = m * (x - vectorStart.x) + vectorStart.y
            float y_intersection = (vectorSlope.y / vectorSlope.x) * (edgeStart.x - vectorStart.x) + vectorStart.y;

            return ((edgeYMin < y_intersection) && (y_intersection < edgeYMax)) || y_intersection == edgeStart.y;
        }

        // Else: Neither line is horizontal/vertical

        // Calculate the slope
        float slope_Vector = vectorSlope.y / vectorSlope.x;
        float slope_Edge = edgeSlope.y / edgeSlope.x;

        if (slope_Vector == slope_Edge) {
            // Lines are parallel - don't intersect
            return false;
        } else {
            // Lines intersect - find the intersection
            float edgeXMin = Math.min(edgeStart.x, edgeStart.x + edgeSlope.x);
            float edgeXMax = Math.max(edgeStart.x, edgeStart.x + edgeSlope.x);

            // Equation came from setting y=mx+b for each line equal and solving for x
            float x_intersection = (slope_Edge * edgeStart.x - slope_Vector * vectorStart.x + vectorStart.y - edgeStart.y) / (slope_Edge - slope_Vector);

            return ((edgeXMin < x_intersection) && (x_intersection < edgeXMax)) || x_intersection == edgeStart.x;
        }
    }


    final class workerThread implements Runnable {
        private ArrayList<PointF> points;

        public workerThread(ArrayList<PointF> pointsList) {
            points = pointsList;
        }

        public void run() {
            // Convert the points to real-world dimensions.
            Mat image = Imgcodecs.imread(currentPhotoPath, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
            // The code in this file works on ArrayLists, while the code in ComputerVisionUtils
            // works on Lists. We do a conversion here.
            ArrayList<PointF> transformedPoints = new ArrayList<>(ComputerVisionUtils.measurementsFromOutline(image, polygonPoints));

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
