package edu.psu.armstrong1.gridmeasure;

import android.content.Intent;
import android.graphics.PointF;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.util.Map;

public class CalculationActivity extends AppCompatActivity {
    String currentPhotoPath = null;                             // path to last picture taken
    Map<Integer, PointF> polygonPoints;                         // the points of the PolygonView


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculation);

        // Get the bitmap and points
        currentPhotoPath = getIntent().getStringExtra(TakePictureActivity.PHOTO_PATH_INTENT_KEY);
        polygonPoints = (Map<Integer, PointF>) getIntent().getExtras().get(TakePictureActivity.POINTS_INTENT_KEY);
    }


    public void calculationsDone() {
        findViewById(R.id.calculation_progressBar).setVisibility(View.GONE);
        findViewById(R.id.button_sendMeasurements).setVisibility(View.VISIBLE);
    }


    // Called when user clicks on the progress spinner
    // Note: This is a temporary function
    public void endCalculation(View view) {
        calculationsDone();
    }


    // Called when the user clicks the Send Measurements button
    public void dispatchSendMeasurements(View view) {
        // stub function to be filled in
    }


    // Called when the user clicks the New Job button
    public void dispatchNewJob(View view) {
        // Go back to the main page and clear out intermediate screens
        Intent intent = new Intent(view.getContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
