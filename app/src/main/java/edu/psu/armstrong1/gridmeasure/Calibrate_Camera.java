package edu.psu.armstrong1.gridmeasure;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Calibrate_Camera extends AppCompatActivity {


    static final int PICTURE_REQUEST = 1;
    int number_of_pics = 0;
    List<String> fileNames = new ArrayList<String>();
    String currentPhotoPath = null;                             // path to last image taken
    int previousRotation = -1;                                  // previous rotation value of image


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the status bar.
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        this.setContentView(R.layout.activity_calibrate__camera);

        // Get rid of action bar
        ActionBar bar = this.getSupportActionBar();
        if (bar != null) {
            bar.hide();
        }

        setContentView(R.layout.activity_calibrate__camera);
    }

    public void dispatchTakePictureIntent(View view) {
            // Create intent to take a picture
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            // Ensure that there's a camera activity to handle the intent
            if (takePictureIntent.resolveActivity(view.getContext().getPackageManager()) != null) {
                // Create the File where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile(view);
                    // add file path to fileNames List for calibration
                    fileNames.add(currentPhotoPath);
                    number_of_pics += 1;
                } catch (IOException ex) {
                    // Error occurred while creating the file - alert user
                    Toast.makeText(getApplicationContext(), R.string.error_file_save, Toast.LENGTH_SHORT).show();
                }

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    // Get the URI for the file
                    Uri photoURI = FileProvider.getUriForFile(view.getContext(),
                            "edu.psu.armstrong1.gridmeasure.fileprovider",
                            photoFile);

                    // Get permissions
                    List<ResolveInfo> resInfoList = getApplicationContext().getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        getApplicationContext().grantUriPermission(packageName, photoURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }

                    // Start activity to take picture and save it
                    previousRotation = -1;      // denotes that this is a new picture -> use default bounding box
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    ((Activity) view.getContext()).startActivityForResult(takePictureIntent, PICTURE_REQUEST);
                }
            } else {
                // No camera to handle intent - alert user
                Toast.makeText(getApplicationContext(), R.string.warn_no_camera, Toast.LENGTH_LONG).show();
            }
            if(number_of_pics==1) {
                findViewById(R.id.button_CalibrationAddPictures).setVisibility(View.VISIBLE);
                findViewById(R.id.button_CalibrationTakePicture).setVisibility(View.GONE);
            }
            if(number_of_pics==5){
                findViewById(R.id.button_CalibrationFinish).setVisibility(View.VISIBLE);
            }
    }

    public void callChuruco(View view){
        String[] fileNames2 = fileNames.toArray(new String[fileNames.size()]);
        GridDetectionUtils.calibrateWithCharuco(fileNames2);
    }

    private File createImageFile(View view) throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = view.getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();

        return image;
    }

}
