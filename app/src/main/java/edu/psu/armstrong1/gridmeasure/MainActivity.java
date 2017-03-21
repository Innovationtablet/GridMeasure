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
        // Start TakePictureActivity
        Intent intent = new Intent(view.getContext(), TakePictureActivity.class);
        view.getContext().startActivity(intent);
    }
}
