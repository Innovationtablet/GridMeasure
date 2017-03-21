package edu.psu.armstrong1.gridmeasure;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TakePictureActivity extends AppCompatActivity {
    static final int ZOOM_SIZE = 50;                            // number of pixels (square) to show for magnification
    static final int MAGNIFIER_INDICATOR_SIZE = 5;              // number of pixels (square) to use as an indicator for magnifier
    static final int INDICATOR_COLOR = Color.rgb(255, 0, 0);    // color of the indicator for the magnifier
    static final String PHOTO_PATH = "curPhotoPath";            // key to savedInstanceBundle for picture location
    String mCurrentPhotoPath;                                   // path to last picture taken
    Bitmap bitmap;                                              // the current picture
    Bitmap magnified;                                           // the magnified picture
    float zoomPosX = 0;                                         // x-coordinate for zoom location
    float zoomPosY = 0;                                         // y-coordinate for zoom location
    int photoH, photoW;                                         // dimensions of the picture
    float imageWidthDif = 0;                                    // difference in width between the scaled picture and the image view
    float imageHeightDif = 0;                                   // difference in height between the scaled picture and the image view
    boolean zooming = false;                                    // whether or not to zoom

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the status bar.
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

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
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                ((Activity) view.getContext()).startActivity(takePictureIntent);
            }
        } else {
            // No camera to handle intent - alert user
            Toast.makeText(getApplicationContext(), R.string.warn_no_camera, Toast.LENGTH_LONG).show();
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

        // Load the bitmap (from picture at mCurrentPhotoPath)
       // bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath);
        bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.test_img2);
        // Get the picture's dimensions
        photoW = bitmap.getWidth();
        photoH = bitmap.getHeight();


        // Try to get the image's rotation
        try {
            ExifInterface exif = new ExifInterface(mCurrentPhotoPath);
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rotate = exifToDegrees(rotation);
        } catch (IOException e) {
            // Error getting rotation - alert user
            Toast.makeText(getApplicationContext(), R.string.error_picture_rotation, Toast.LENGTH_LONG).show();
            rotate = 0;
        }

        // Put the rotated and scaled picture in the view (rotate picture to dominant view)
        bitmap = rotateBitmap(bitmap, rotateToDominant(rotate, photoH, photoW, targetH, targetW));
        imageView.setImageBitmap(bitmap);


        // Get updated picture dimensions after rotation
        photoW = bitmap.getWidth();
        photoH = bitmap.getHeight();

        // Make sure targetW/H aren't zero
        if (targetH == 0) {
            targetH = photoH;
        }
        if (targetW == 0) {
            targetW = photoW;
        }

        // Determine how much the image will be scaled down
        final float scaleFactor = Math.min(((float) targetW) / photoW, ((float) targetH) / photoH);

        // Get the padding around the image
        imageWidthDif = (targetW - photoW * scaleFactor) / 2;
        imageHeightDif = (targetH - photoH * scaleFactor) / 2;

        Log.d("takePicture", "photoH = " + photoH + "; photoW = " + photoW + "; viewH = " + targetH + "; viewW = " + targetW);
        Log.d("takePicture", "Width scale = " + ((float) photoW)/targetW + "; Height scale = " + ((float) photoH)/targetH + "; scale = " + scaleFactor);
        Log.d("takePicture", "imageWidthDif = " + imageWidthDif + "; imageHeightDif = " + imageHeightDif);

        // Add touch listener to add zooming
        imageView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent event) {

                // Get the type of touch event it was
                int action = event.getAction();

                // Get the location of the event
                zoomPosX = event.getX();
                zoomPosY = event.getY();

                // Turn on zooming if screen is being touched, off otherwise
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        zooming = true;
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        zooming = false;
                        break;

                    default:
                        break;
                }

                Log.d("takePicture", "Zoom (" + (zoomPosX - imageWidthDif) +", " + (zoomPosY - imageHeightDif) + "); Zooming = " + zooming);

                // Get the magnifier view
                ImageView magnifier = (ImageView) findViewById(R.id.takePicture_magnifyingGlass);

                if (zooming) {
                    // Get location in bitmap for magnification
                    int zoomLocX = (int) ((zoomPosX - imageWidthDif) / scaleFactor);
                    int zoomLocY = (int) ((zoomPosY - imageHeightDif) / scaleFactor);

                    // Get beginning position in bitmap for magnification
                    int beginX = Math.max(0, zoomLocX - ZOOM_SIZE / 2);
                    int beginY = Math.max(0, zoomLocY - ZOOM_SIZE / 2);
                    int endX = Math.min(bitmap.getWidth(), beginX + ZOOM_SIZE);
                    int endY = Math.min(bitmap.getHeight(), beginY + ZOOM_SIZE);

                    // Make sure window is ZOOM_SIZE x ZOOM_SIZE
                    if (endX - beginX < ZOOM_SIZE && beginX != 0) {
                        // if beginX = 0, either endX=beginX + ZOOM_SIZE or endX is as large as possible
                        // if beginX != 0, endX must be as large as possible
                        beginX = Math.max(0, endX - ZOOM_SIZE);
                    }
                    if (endY - beginY < ZOOM_SIZE && beginY != 0) {
                        // if beginY = 0, either endY=beginY + ZOOM_SIZE or endY is as large as possible
                        // if beginY != 0, endY must be as large as possible
                        beginY = Math.max(0, endY - ZOOM_SIZE);
                    }

                    // Extract portion of picture to magnify
                    magnified = Bitmap.createBitmap(bitmap, beginX, beginY, endX - beginX, endY - beginY);

                    // Get bounded box around where user is touching
                    int indicatorBeginX = Math.min(magnified.getWidth() - 1, Math.max(0, (zoomLocX - beginX) - MAGNIFIER_INDICATOR_SIZE / 2));
                    int indicatorBeginY = Math.min(magnified.getHeight() - 1, Math.max(0, (zoomLocY - beginY) - MAGNIFIER_INDICATOR_SIZE / 2));
                    int indicatorEndX = Math.min(magnified.getWidth() - 1, indicatorBeginX + MAGNIFIER_INDICATOR_SIZE);
                    int indicatorEndY = Math.min(magnified.getHeight() - 1, indicatorBeginY + MAGNIFIER_INDICATOR_SIZE);

                    // Add a box for where user is touching
                    for (int i = indicatorBeginX; i <= indicatorEndX; i++) {
                        for (int j = indicatorBeginY; j <= indicatorEndY; j++) {
                            magnified.setPixel(i, j, INDICATOR_COLOR);
                        }
                    }

                    // Put the magnified picture in the view
                    magnifier.setImageBitmap(magnified);

                    // Make sure magnifier isn't blocking where touch input is
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) magnifier.getLayoutParams();
                    if ((endX >= bitmap.getWidth() - (1.5 * magnifier.getWidth() / scaleFactor)) &&
                            (endY <= 1.5 * magnifier.getHeight() / scaleFactor)) {
                        // Touch input is near magnifier view (top right) - move magnifier to top left
                        params.gravity = Gravity.LEFT | Gravity.TOP;
                    } else {
                        // Magnifier can stay in top right
                        params.gravity = Gravity.RIGHT | Gravity.TOP;
                    }

                    // Set the gravity to desired location
                    magnifier.setLayoutParams(params);

                    // Show the magnifier view
                    magnifier.setVisibility(View.VISIBLE);
                } else {
                    // Not zooming - hide magnifier
                    magnifier.setVisibility(View.INVISIBLE);
                }

                return true;


            }
        });
        Log.d("takePicture", "Zoom (" + (zoomPosX - imageWidthDif) +", " + (zoomPosY - imageHeightDif) + "); Zooming = " + zooming);

        PolygonView polygonView = (PolygonView) findViewById(R.id.polygonView);
        Log.d("takePicture", "Pic dim (" + (targetW) +", " + (targetH)+ ")" );
        Log.d("takePicture", "0,0 (" + (imageWidthDif) +", " + (imageHeightDif)+ ")" );
        Log.d("takePicture", "0,max (" + (targetW-imageWidthDif) +", " + (imageHeightDif)+ ")" );
        Log.d("takePicture", "max,0 (" + (imageWidthDif) +", " + (targetH-imageHeightDif)+ ")" );
        Log.d("takePicture", "max,max (" + (targetW-imageWidthDif) +", " + (targetH-imageHeightDif)+ ")" );
        Map<Integer, PointF> pointf = getOutlinePoints( imageView);
        polygonView.setPoints(pointf);
        polygonView.setVisibility(View.VISIBLE);

    }

    private Map<Integer, PointF> getOutlinePoints(ImageView view) {
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(view.getWidth() / 4, view.getHeight() / 4));
        outlinePoints.put(1, new PointF(3 * view.getWidth() / 4, view.getHeight() / 4));
        outlinePoints.put(2, new PointF(view.getWidth() / 4, 3 * view.getHeight() / 4));
        outlinePoints.put(3, new PointF(3 * view.getWidth() / 4, 3 * view.getHeight() / 4));
        return outlinePoints;
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
