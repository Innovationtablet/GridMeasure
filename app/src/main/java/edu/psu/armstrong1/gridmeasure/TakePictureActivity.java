package edu.psu.armstrong1.gridmeasure;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TakePictureActivity extends AppCompatActivity {
    static final String PHOTO_PATH = "curPhotoPath";    // key to savedInstanceBundle for picture location
    String mCurrentPhotoPath;                           // path to last picture taken

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        this.setContentView(R.layout.activity_take_picture);

        // Get rid of action bar
        ActionBar bar = this.getSupportActionBar();
        if (bar != null) {
            bar.hide();
        }
    }

    @Override
    public void onWindowFocusChanged (boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Format picture if one is available (now that imageView has been created)
        if (mCurrentPhotoPath != null && hasFocus) {
            setPic();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the current photo path
        savedInstanceState.putString(PHOTO_PATH, mCurrentPhotoPath);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore state info and photo path
        super.onRestoreInstanceState(savedInstanceState);
        mCurrentPhotoPath = savedInstanceState.getString(PHOTO_PATH);
    }

    // Called when the user clicks the Take a Picture button
    public void dispatchTakePictureIntent(View view) {
        // Create intent to take a picture
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(view.getContext().getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile(view);
            } catch (IOException ex) {
                // Error occurred while creating the file - alert user
                Snackbar.make(findViewById(R.id.main_coordinatorLayout), R.string.error_file_save, Snackbar.LENGTH_LONG).show();
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                // Get the URI for the file
                Uri photoURI = FileProvider.getUriForFile(view.getContext(),
                        "edu.psu.armstrong1.gridmeasure.fileprovider",
                        photoFile);

                // Start activity to take picture and save it
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                ((Activity) view.getContext()).startActivity(takePictureIntent);
            }
        } else {
            // No camera to handle intent - alert user
            Snackbar.make(findViewById(R.id.main_coordinatorLayout), R.string.warn_no_camera, Snackbar.LENGTH_INDEFINITE).show();
        }
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
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void setPic() {
        float rotate;       // number of degrees to rotate picture

        // Get the view the picture will go in
        ImageView imageView = (ImageView) findViewById(R.id.takePicture_imageView);

        // Get the dimensions of the View
        int targetW = imageView.getWidth();
        int targetH = imageView.getHeight();

        // Get the dimensions of the bitmap (from picture at mCurrentPhotoPath)
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Make sure targetW/H aren't zero
        if (targetH == 0) {
            targetH = photoH;
        }
        if (targetW == 0) {
            targetW = photoW;
        }

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);

        // Try to get the image's rotation
        try {
            ExifInterface exif = new ExifInterface(mCurrentPhotoPath);
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rotate = exifToDegrees(rotation);
        } catch (IOException e) {
            // Error getting rotation - alert user
            Snackbar.make(findViewById(R.id.main_coordinatorLayout), R.string.error_picture_rotation, Snackbar.LENGTH_SHORT).show();
            rotate = 0;
        }

        // Put the rotated and scaled picture in the view (rotate picture to dominant view)
        imageView.setImageBitmap(rotateBitmap(bitmap, rotateToDominant(rotate, photoH, photoW, targetH, targetW)));
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        // Rotate source by angle degrees
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static float exifToDegrees(int exifOrientation) {
        // Decode orientation to degrees
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) { return 90; }
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {  return 180; }
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {  return 270; }
        return 0;
    }

    private static float rotateToDominant(float curRotation, int curHeight, int curWidth, int viewHeight, int viewWidth) {
        // Rotate picture to dominant view (portrait or landscape)
        if (viewHeight > viewWidth) {
            // Rotate picture to portrait layout
            if ((curRotation == 0 || curRotation == 180) && curWidth > curHeight) {
                // Picture's width is greater than height and rotation doesn't affect ratio -
                //   rotate another 90 degrees
                curRotation += 90;
            } else if ((curRotation == 90 || curRotation == 270) && curWidth < curHeight) {
                // Picture's height is greater than width and rotation flips ratio -
                //   rotate another 90 degrees
                curRotation -= 90;
            }
        } else if (viewHeight < viewWidth) {
            // Rotate picture to landscape layout
            if ((curRotation == 0 || curRotation == 180) && curWidth < curHeight) {
                // Picture's height is greater than width and rotation doesn't affect ratio -
                //   rotate another 90 degrees
                curRotation += 90;
            } else if ((curRotation == 90 || curRotation == 270) && curWidth > curHeight) {
                // Picture's width is greater than height and rotation flips ratio -
                //   rotate another 90 degrees
                curRotation -= 90;
            }
        }

        return curRotation;
    }
}
