package edu.psu.armstrong1.gridmeasure;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class ShowMeasurementActivity extends AppCompatActivity {
    // Constants
    public static String[] columnNames = {"number", "innerLength", "outerLength"};
    public static int[] columnIds = {R.id.threeColList_Column1, R.id.threeColList_Column2, R.id.threeColList_Column3};
    public final String EXTENSION_AMOUNT_KEY = "ExtensionAmount";

    // Variables
    CalculationActivity calc;                               // CalculationActivity to do transforms of points
    ArrayList<PointF> basePoints;                           // the points after real-world conversion
    ArrayList<PointF> polygonPointsExtended;                // the points to be cut (after extending edges)
    int numberOfPoints;                                     // the number of points
    int tileBitmapHeight, tileBitmapWidth;                  // height and width of the tile bitmap
    float xOffset = 0, yOffset = 0;                         // offset between polygonPointsExtended and basePoints
    ImageView imgView;                                      // the image view containing the tile diagram
    ListView listView;                                      // the list view containing the list of side lengths
    EditText extensionEntered;                              // the EditText view where user inputs extension amount
    Paint innerPaint, outerPaint;                           // the paint objects for drawing the tile cuts
    float extensionAmount;                                  // the amount that edges were bumped out

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_measurement);

        // Get the points from the previous activity
        basePoints = (ArrayList<PointF>) getIntent().getExtras().get(TakePictureActivity.POINTS_INTENT_KEY);
        numberOfPoints = basePoints.size();

        // Get the tile image view
        imgView = (ImageView) findViewById(R.id.showMeasurement_tileImageView);
        listView = (ListView) findViewById(R.id.showMeasurement_measurementList);
        extensionEntered = (EditText) findViewById(R.id.showMeasurement_edgeExtensionAmount);

        // Create paint objects for lines
        innerPaint = new Paint();
        innerPaint.setColor(Color.RED);
        innerPaint.setStrokeWidth(3);
        outerPaint = new Paint();
        outerPaint.setColor(Color.BLUE);
        outerPaint.setStrokeWidth(3);

        // Get the extension amount
        if (savedInstanceState != null) {
            extensionAmount = savedInstanceState.getFloat(EXTENSION_AMOUNT_KEY);
        } else {
            extensionAmount = CalculationActivity.EXTEND_EDGES_AMOUNT;
        }

        // Put the extension amount in the EditText view
        extensionEntered.setText(Float.toString(extensionAmount));

        // Setup the CalculationActivity
        calc = new CalculationActivity(getApplicationContext());

        // Draw the tile cut diagram and points list
        createDiagramAndPointsList(extensionAmount);
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save extensionAmount
        savedInstanceState.putFloat(EXTENSION_AMOUNT_KEY, extensionAmount);

        super.onSaveInstanceState(savedInstanceState);
    }

    // Called when the user clicks the Send Measurements button
    public void dispatchSendMeasurements(View view) {
        // Write the points to a JSON string
        String jsonString = null;
        Log.d("ShowMeasurement", "Converting points to JSON");
        OutputStream out = new ByteArrayOutputStream();

        try {
            jsonString = JsonUtils.convertPoints(polygonPointsExtended);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.error_ioExceptionConvertingPts, Toast.LENGTH_SHORT).show();
            Log.e("ShowMeasurement", e.getMessage());
            Log.e("ShowMeasurement", e.getStackTrace().toString());
        }

        if (jsonString != null) {
            // Start BluetoothActivity
            Intent intent = new Intent(view.getContext(), BluetoothActivity.class);
            intent.putExtra(BluetoothActivity.STARTING_TEXT_INTENT_KEY, jsonString);
            view.getContext().startActivity(intent);
        }
    }


    // Called when the user clicks the extend edges button
    public void extendEdges(View view) {
        // Get the extension amount from the EditText view
        extensionAmount = Float.parseFloat(extensionEntered.getText().toString());

        // Draw the tile cut diagram and points list
        createDiagramAndPointsList(extensionAmount);
    }


    private void createDiagramAndPointsList(float edgeExtensionAmt) {
        // Get the extended points list
        polygonPointsExtended = calc.getExtendedPoints(basePoints, edgeExtensionAmt);

        // Reorder and re-transform base points to match extended points
        ArrayList<PointF> polygonPoints = calc.transformPointsUsingPreviousOptions(basePoints);

        // Get the offset amount
        PointF offset = getOffsetAmount(polygonPointsExtended.get(polygonPointsExtended.size() - 1),
                polygonPointsExtended.get(0),
                polygonPointsExtended.get(1),
                edgeExtensionAmt);
        Log.d("ShowMeasurement", "Offset amount:" + offset);
        xOffset = Math.abs(offset.x);
        yOffset = Math.abs(offset.y);

        // Create a blank bitmap with a tile
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.blank_tile, opt);
        tileBitmapHeight = bitmap.getHeight();
        tileBitmapWidth = bitmap.getWidth();

        // Create a canvas
        Canvas canvas = new Canvas(bitmap);

        // Create a list to hold the points as strings
        ArrayList<HashMap<String, String>> pointsList = new ArrayList<>();
        HashMap<String, String> item = new HashMap<>();
        item.put(columnNames[0], "#");
        item.put(columnNames[1], "Inner Side Length");
        item.put(columnNames[2], "Outer Side Length");
        pointsList.add(item);

        // Draw lines between each point and add each point to the list view
        for (int i = 0; i < numberOfPoints; i++) {
            // Draw the line between point i and point i+1 for the inner points
            PointF innerPt1 = polygonPoints.get(i);
            PointF innerPt2 = polygonPoints.get((i + 1) % numberOfPoints);
            PointF innerStart = convertCoordsToBitmapCoords(innerPt1.x + xOffset, innerPt1.y + yOffset);
            PointF innerEnd = convertCoordsToBitmapCoords(innerPt2.x + xOffset, innerPt2.y + yOffset);
            canvas.drawLine(innerStart.x, innerStart.y, innerEnd.x, innerEnd.y, innerPaint);
            Log.d("ShowMeasurement", "Inner line from (" + (innerPt1.x + xOffset) + ", " + (innerPt1.y + yOffset) + ") to " +
                    "(" + (innerPt2.x + xOffset) + ", " + (innerPt2.y + yOffset) + ")");

            // Draw the line between point i and point i+1 for the outer points
            PointF outerPt1 = polygonPointsExtended.get(i);
            PointF outerPt2 = polygonPointsExtended.get((i + 1) % numberOfPoints);
            PointF outerStart = convertCoordsToBitmapCoords(outerPt1.x, outerPt1.y);
            PointF outerEnd = convertCoordsToBitmapCoords(outerPt2.x, outerPt2.y);
            canvas.drawLine(outerStart.x, outerStart.y, outerEnd.x, outerEnd.y, outerPaint);
            Log.d("ShowMeasurement", "Outer line from (" + outerPt1.x + ", " + outerPt1.y + ") to " +
                    "(" + outerPt2.x + ", " + outerPt2.y + ")");

            // Make a new Hashmap to hold the list column values
            item = new HashMap<>();

            // Add point i to the list of points (point number and inner/outer distances)
            float innerDist = (float) Math.sqrt(Math.pow(innerPt2.x - innerPt1.x, 2) + Math.pow(innerPt2.y - innerPt1.y, 2));
            float outerDist = (float) Math.sqrt(Math.pow(outerPt2.x - outerPt1.x, 2) + Math.pow(outerPt2.y - outerPt1.y, 2));
            item.put(columnNames[0], Integer.toString(i+1));
            item.put(columnNames[1], String.format("%02.2f", innerDist));
            item.put(columnNames[2], String.format("%02.2f", outerDist));
            pointsList.add(item);
        }

        // Draw the image
        imgView.setImageBitmap(bitmap);

        // Add the points to the list view
        SimpleAdapter adapter = new SimpleAdapter(this, pointsList, R.layout.three_column_list_item, columnNames, columnIds);
        listView.setAdapter(adapter);

        // Redraw the views
        imgView.invalidate();
        listView.invalidate();
    }

    private PointF convertPointToBitmapCoords(PointF tileCoord) {
        return convertCoordsToBitmapCoords(tileCoord.x, tileCoord.y);
    }

    private PointF convertCoordsToBitmapCoords(float x, float y) {
        // Divide tile coordinate by max coordinate and multiply by bitmap's max coordinate
        PointF point = new PointF(x / CalculationActivity.CUTTER_MAX_X * tileBitmapWidth,
                tileBitmapHeight - (y / CalculationActivity.CUTTER_MAX_Y * tileBitmapHeight));
        return point;
    }

    private PointF getCenterPoint(ArrayList<PointF> points) {
        PointF center = new PointF(0,0);
        int size = points.size();
        for (PointF point : points) {
            center.offset(point.x / size, point.y / size);
        }

        return center;
    }

    // Assumes beforeCornerPt is the first CCW point from corner, afterCornerPt is first CW
    //   and that corner is the bottom left corner of all points at (0,0)
    private PointF getOffsetAmount(PointF beforeCornerPt, PointF corner, PointF afterCornerPt, float extensionAmount) {
        // Get the edges adjacent to corner
        PointF prevEdge = new PointF(beforeCornerPt.x - corner.x, beforeCornerPt.y - corner.y);
        PointF nextEdge = new PointF(afterCornerPt.x - corner.x, afterCornerPt.y - corner.y);

        // Get the normal vectors of each edge
        // Since the inside of the polygon is between prevEdge and nextEdge (CCW), the inward-pointing
        //   normal vector of the previous edge will have a non-positive x-component (prevEdge has a
        //   non-negative y-component). Similarly, the inward-pointing normal vector of the next edge
        //   will have a non-positive y-component (nextEdge has a non-negative x-component)
        PointF prevEdgeNormalVector = new PointF(-prevEdge.y, prevEdge.x);
        PointF nextEdgeNormalVector = new PointF(nextEdge.y, -nextEdge.x);

        // Normalize the vector lengths
        prevEdgeNormalVector.set(prevEdgeNormalVector.x / prevEdgeNormalVector.length() * extensionAmount,
                prevEdgeNormalVector.y / prevEdgeNormalVector.length() * extensionAmount);
        nextEdgeNormalVector.set(nextEdgeNormalVector.x / nextEdgeNormalVector.length() * extensionAmount,
                nextEdgeNormalVector.y / nextEdgeNormalVector.length() * extensionAmount);


        // Move the corner by each of the normal vectors (bump in each edge)
        PointF newCornerForNextEdge = new PointF(corner.x + nextEdgeNormalVector.x, corner.y + nextEdgeNormalVector.y);
        PointF newCornerForPrevEdge = new PointF(corner.x + prevEdgeNormalVector.x, corner.y + prevEdgeNormalVector.y);

        // Intersect the bumped out edges to find the new corner point
        return CalculationActivity.intersectionOfLines(newCornerForNextEdge, nextEdge, newCornerForPrevEdge, prevEdge);
    }
}
