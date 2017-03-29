package edu.psu.armstrong1.gridmeasure;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
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

    private void startPictureActivity(View view, boolean debugOn) {
        // Start TakePictureActivity
        Intent intent = new Intent(view.getContext(), TakePictureActivity.class);
        intent.putExtra(TakePictureActivity.DEBUG_INTENT_KEY, debugOn);
        view.getContext().startActivity(intent);
    }


}
