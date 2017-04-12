package edu.psu.armstrong1.gridmeasure;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;

public class ShowMeasurementActivity extends AppCompatActivity {
    // Constants
    public static String[] columnNames = {"number", "point", "length"};
    public static int[] columnIds = {R.id.threeColList_Column1, R.id.threeColList_Column2, R.id.threeColList_Column3};

    // Variables
    ArrayList<PointF> polygonPoints;                        // the points to be cut
    int numberOfPoints;                                     // the number of points
    int tileBitmapHeight, tileBitmapWidth;                  // height and width of the tile bitmap

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_measurement);

        // Get the points from the previous activity
        polygonPoints = (ArrayList<PointF>) getIntent().getExtras().get(TakePictureActivity.POINTS_INTENT_KEY);

        // Points used for testing since no picture with ChArUco is available
        polygonPoints = new ArrayList<PointF>();
        polygonPoints.add(new PointF(0,0));
        polygonPoints.add(new PointF(0,24));
        polygonPoints.add(new PointF(17,17));
        polygonPoints.add(new PointF(12,12));
        polygonPoints.add(new PointF(18,13));
        polygonPoints.add(new PointF(15,0));
        // End testing code

        numberOfPoints = polygonPoints.size();

        // Get the tile image view
        ImageView imgView = (ImageView) findViewById(R.id.showMeasurement_tileImageView);
        ListView listView = (ListView) findViewById(R.id.showMeasurement_measurementList);

        // Create a blank bitmap with a tile
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.blank_tile, opt);
        tileBitmapHeight = bitmap.getHeight();
        tileBitmapWidth = bitmap.getWidth();

        // Create a canvas and paint for lines
        Canvas canvas = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(Color.RED);
        p.setStrokeWidth(3);

        // Create a list to hold the points as strings
        ArrayList<HashMap<String, String>> pointsList = new ArrayList<>();
        HashMap<String, String> item = new HashMap<>();
        item.put(columnNames[0], "#");
        item.put(columnNames[1], "Point");
        item.put(columnNames[2], "Side Length");
        pointsList.add(item);

        // Draw lines between each point and add each point to the list view
        for (int i = 0; i < numberOfPoints; i++) {
            // Draw the line between point i and point i+1
            PointF point1 = polygonPoints.get(i);
            PointF point2 = polygonPoints.get((i + 1) % numberOfPoints);
            PointF start = convertPointToBitmapCoords(point1);
            PointF end = convertPointToBitmapCoords(point2);
            canvas.drawLine(start.x, start.y, end.x, end.y, p);

            // Make a new Hashmap to hold the list column values
            item = new HashMap<>();

            // Add point i to the list of points
            float dist = (float) Math.sqrt(Math.pow(point2.x - point1.x, 2) + Math.pow(point2.y - point1.y, 2));
            item.put(columnNames[0], Integer.toString(i+1));
            item.put(columnNames[1], String.format("(%02.2f, %02.2f)", point1.x, point1.y));
            item.put(columnNames[2], String.format("%02.2f", dist));
            pointsList.add(item);
        }

        // Draw the image
        imgView.setImageBitmap(bitmap);

        // Add the points to the list view
        SimpleAdapter adapter = new SimpleAdapter(this, pointsList, R.layout.three_column_list_item, columnNames, columnIds);
        listView.setAdapter(adapter);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the local variables
        savedInstanceState.putSerializable(TakePictureActivity.POINTS_INTENT_KEY, polygonPoints);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore state info and photo path
        super.onRestoreInstanceState(savedInstanceState);
        polygonPoints = (ArrayList<PointF>) savedInstanceState.getSerializable(TakePictureActivity.POINTS_INTENT_KEY);
    }

    // Called when the user clicks the Send Measurements button
    public void dispatchSendMeasurements(View view) {
        // stub function to be filled in
    }

    private PointF convertPointToBitmapCoords(PointF tileCoord) {
        // Divide tile coordinate by max coordinate and multiply by bitmap's max coordinate
        PointF point = new PointF(tileCoord.x / CalculationActivity.CUTTER_MAX_X * tileBitmapWidth,
                                  tileBitmapHeight - (tileCoord.y / CalculationActivity.CUTTER_MAX_Y * tileBitmapHeight));
        return point;
    }
}
