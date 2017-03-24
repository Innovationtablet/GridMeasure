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
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
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
    static boolean DEBUGGING_MODE = false;                      // flag denoting whether or not to show the test image
    static final int ZOOM_SIZE = 50;                            // number of pixels (square) to show for magnification
    static final int MAGNIFIER_INDICATOR_SIZE = 5;              // number of pixels (square) to use as an indicator for magnifier
    static final int INDICATOR_COLOR = Color.rgb(255, 0, 0);    // color of the indicator for the magnifier
    static final String PHOTO_PATH = "curPhotoPath";            // key to savedInstanceBundle for picture location
    static final String ROTATION_KEY = "curRotation";           // key to savedInstanceBundle for previousRotation
    static final String WIDTH_DIF_KEY = "curWidthDif";          // key to savedInstanceBundle for imageWidthDif
    static final String HEIGHT_DIF_KEY = "curHeightDif";        // key to savedInstanceBundle for imageHeightDif
    static final String SCALE_FACTOR_KEY = "curScaleFactor";    // key to savedInstanceBundle for scaleFactor
    static final String POINTS_KEY = "curPoints";               // key to savedInstanceBundle for PolygonView's points
    static final String NEW_PICTURE_KEY = "curNewPicture";      // key to savedInstanceBundle for newPicture
    static final String CIRCLE_DIAMETER_KEY = "curCircleDia";   // key to savedInstanceBundle for circleDiameter
    String mCurrentPhotoPath = null;                            // path to last picture taken
    Bitmap bitmap;                                              // the current picture
    Bitmap magnified;                                           // the magnified picture
    float zoomImageViewX = 0;                                   // x-coordinate for zoom location
    float zoomImageViewY = 0;                                   // y-coordinate for zoom location
    int zoomLocX, zoomLocY;                                     // x,y-coordinates for zoom location in bitmap
    int photoH, photoW;                                         // dimensions of the picture
    double scaleFactor, previousScaleFactor;                    // scale factor of the picture to fit the image view and the scale factor from before rotation
    double imageWidthDif, prevImageWidthDif;                    // difference in width between the scaled picture and the image view
    double imageHeightDif, prevImageHeightDif;                  // difference in height between the scaled picture and the image view
    int previousRotation = -1;                                  // previous rotation value of image
    boolean zooming = false;                                    // whether or not to zoom
    boolean newPicture = false;
    PolygonView polygonView;                                    // the PolygonView for the bounding box
    Map<Integer, PointF> polygonPoints;                         // the points of the PolygonView
    float circleDiameter;                                       // diameter of circles in PolygonView

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

        // Check for debug flag
        DEBUGGING_MODE = getIntent().getBooleanExtra("DEBUG_FLAG", false);
    }

    @Override
    public void onWindowFocusChanged (boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Format picture if one is available (now that imageView has been created)
        if ((mCurrentPhotoPath != null || DEBUGGING_MODE) && hasFocus) {
            setPic();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the current photo path
        savedInstanceState.putString(PHOTO_PATH, mCurrentPhotoPath);

        // Save details about bounding box and image to redraw bounding box
        savedInstanceState.putInt(ROTATION_KEY, previousRotation);
        savedInstanceState.putDouble(WIDTH_DIF_KEY, imageWidthDif);
        savedInstanceState.putDouble(HEIGHT_DIF_KEY, imageHeightDif);
        savedInstanceState.putDouble(SCALE_FACTOR_KEY, scaleFactor);
        savedInstanceState.putBoolean(NEW_PICTURE_KEY, newPicture);
        if (polygonView != null) {
            savedInstanceState.putSerializable(POINTS_KEY, (HashMap) polygonView.getPoints());
            savedInstanceState.putFloat(CIRCLE_DIAMETER_KEY, polygonView.getCircleDiameter());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore state info and photo path
        super.onRestoreInstanceState(savedInstanceState);
        mCurrentPhotoPath = savedInstanceState.getString(PHOTO_PATH);

        // Restore bounding box and image info to redraw bounding box
        previousRotation = savedInstanceState.getInt(ROTATION_KEY);
        prevImageWidthDif = savedInstanceState.getDouble(WIDTH_DIF_KEY);
        prevImageHeightDif = savedInstanceState.getDouble(HEIGHT_DIF_KEY);
        previousScaleFactor = savedInstanceState.getDouble(SCALE_FACTOR_KEY);
        newPicture = savedInstanceState.getBoolean(NEW_PICTURE_KEY, false);
        circleDiameter = savedInstanceState.getFloat(CIRCLE_DIAMETER_KEY, 0);
        if (previousRotation != -1) {
            polygonPoints = (Map<Integer, PointF>) savedInstanceState.getSerializable(POINTS_KEY);
        }
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
                previousRotation = -1;      // denotes that this is a new picture -> use default bounding box
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                ((Activity) view.getContext()).startActivity(takePictureIntent);
            }
        } else {
            // No camera to handle intent - alert user
            Toast.makeText(getApplicationContext(), R.string.warn_no_camera, Toast.LENGTH_LONG).show();
        }
    }

    // Called when the user clicks the Accept Outline button
    public void dispatchCalculationIntent(View view) {
        // stub function to be filled in
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
        float rotate;                       // number of degrees to rotate picture
        Map<Integer, PointF> pointf;        // points for the bounding box

        // Check if this is debugging mode
        if (DEBUGGING_MODE) {
            // Debug mode - Use debugging/test image
            bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.test_img2);
            rotate = 90;
        } else {
            // Load the bitmap (from picture at mCurrentPhotoPath)
            bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath);

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
        }

        // Get the picture's dimensions
        photoW = bitmap.getWidth();
        photoH = bitmap.getHeight();

        // Get the view the picture will go in
        ImageView imageView = (ImageView) findViewById(R.id.takePicture_imageView);

        // Get the dimensions of the View
        int targetW = imageView.getWidth();
        int targetH = imageView.getHeight();

        // Put the rotated and scaled picture in the view (rotate picture to dominant view)
        rotate = rotateToDominant(rotate, photoH, photoW, targetH, targetW);
        bitmap = rotateBitmap(bitmap, rotate);
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
        scaleFactor = Math.min(((double) targetW) / photoW, ((double) targetH) / photoH);

        // Get the padding around the image
        imageWidthDif = (targetW - photoW * scaleFactor) / 2;
        imageHeightDif = (targetH - photoH * scaleFactor) / 2;

        Log.d("takePicture", "photoH = " + photoH + "; photoW = " + photoW + "; viewH = " + targetH + "; viewW = " + targetW);
        Log.d("takePicture", "Width scale = " + ((double) photoW)/targetW + "; Height scale = " + ((double) photoH)/targetH + "; scale = " + scaleFactor);
        Log.d("takePicture", "imageWidthDif = " + imageWidthDif + "; imageHeightDif = " + imageHeightDif);

        // Add touch listener to add zooming
        imageView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent event) {

                // Get the type of touch event it was
                int action = event.getAction();

                // Get the location of the event
                zoomImageViewX = event.getX();
                zoomImageViewY = event.getY();

                // Turn on zooming if screen is being touched, off otherwise
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        zoomLocation(zoomImageViewX, zoomImageViewY, false);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopZooming();
                        break;

                    default:
                        break;
                }

                return true;
            }
        });

        // Add bounding box to image
        polygonView = (PolygonView) findViewById(R.id.polygonView);

        // Check if device was just rotated
        if (previousRotation != -1)
        {
            // Device was rotated - redraw bounding box correctly
            Log.d("takePicture", "New rotation = " + rotate + "; Previous rotation = " + previousRotation);

            // Get the rotation mod 360 degrees (add 360 to avoid negative output)
            int resultingRotation = (360 + (int) rotate - previousRotation) % 360;

            // Convert the old points to new ones point-by-point
            pointf = new HashMap<>();
            for (Map.Entry<Integer, PointF> entry : polygonPoints.entrySet()) {
                //  Step 1: Convert view coordinates to bitmap coordinates of old rotation
                Point bitmapPt = convertViewPointToBitmapPoint(entry.getValue(), prevImageWidthDif, prevImageHeightDif, previousScaleFactor, true);
                int newX, newY;

                //  Step 2: Perform rotation on coordinates in bitmap coordinates
                switch (resultingRotation) {
                    case 90:
                        newX = photoW - bitmapPt.y;
                        newY = bitmapPt.x;
                        break;
                    case 180:
                        newX = photoW - bitmapPt.x;
                        newY = photoH - bitmapPt.y;
                        break;
                    case 270:
                        newX = bitmapPt.y;
                        newY = photoH - bitmapPt.x;
                        break;
                    default:
                        // No net rotation
                        newX = bitmapPt.x;
                        newY = bitmapPt.y;
                        break;
                }

                //  Step 3: Convert bitmap coordinates to view coordinates of new rotation
                PointF viewPt = convertBitmapToViewPoint(newX, newY, imageWidthDif, imageHeightDif, scaleFactor, true);
                pointf.put(entry.getKey(), viewPt);

                Log.d("takePicture", "Bitmap: " + bitmapPt + " -> (" + newX + ", " + newY + ")");
                Log.d("takePicture", "View: " + entry.getValue() + " -> " + viewPt);
            }

            Log.d("takePicture", "Resulting rotation = " + resultingRotation);
            Log.d("takePicture", "Old points: " + polygonPoints);
            Log.d("takePicture", "New points: " + pointf);

        } else {
            // First time seeing this image - draw default bounding box
            pointf = getOutlinePoints(imageView);
        }

        // Set points and make bounding box visible
        polygonView.setPoints(pointf);
        polygonView.setVisibility(View.VISIBLE);

        // Save rotation value in case device is rotated
        previousRotation = (int) rotate;

        // Show Accept Outline button
        findViewById(R.id.button_AcceptOutline).setVisibility(View.VISIBLE);
    }

    private Map<Integer, PointF> getOutlinePoints(ImageView view) {
        // Place beginning points in a rectangle around the center to take up about half the image view
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(view.getWidth() / 4, view.getHeight() / 4));
        outlinePoints.put(1, new PointF(3 * view.getWidth() / 4, view.getHeight() / 4));
        outlinePoints.put(2, new PointF(view.getWidth() / 4, 3 * view.getHeight() / 4));
        outlinePoints.put(3, new PointF(3 * view.getWidth() / 4, 3 * view.getHeight() / 4));
        Log.d("TakePictureActivity", "Starting Points:" + outlinePoints);
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

    // Note: If bitmapCoords = true, (x,y) is with respect to the bitmap, i.e., (0,0) is top left of bitmap
    //          Else, (x,y) is with respect to the imageView, i.e., (0,0) is top left of imageView
    public void zoomLocation(float zoomPosX, float zoomPosY, boolean bitmapCoords) {
        Log.d("TakePictureActivity", "zoomLocation(" + zoomPosX + ", " + zoomPosY + ", " + bitmapCoords + ")");
        Log.d("TakePictureActivity", "Points: " + polygonView.getPoints());
        if (!bitmapCoords) {
            // Get location in bitmap for magnification
            zoomLocX = convertViewToBitmapCoords(zoomPosX, imageWidthDif, scaleFactor);
            zoomLocY = convertViewToBitmapCoords(zoomPosY, imageHeightDif, scaleFactor);
        } else {
            zoomLocX = (int) zoomPosX;
            zoomLocY = (int) zoomPosY;
        }

        zooming = true;
        zoomLocation();
    }

    public void stopZooming() {
        zooming = false;
        zoomLocation();
    }

    private void zoomLocation() {
        Log.d("takePicture", "Zoom at bitmap coords (" + zoomLocX +", " + zoomLocY + "); Zooming = " + zooming);

        // Get the magnifier view
        ImageView magnifier = (ImageView) findViewById(R.id.takePicture_magnifyingGlass);

        if (zooming) {
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
    }

    private int convertViewToBitmapCoords(double viewCoord, double imagePadding, double scale) {
        return (int) ((viewCoord - imagePadding) / scale);
    }

    // Note: imagePadding is the width between the beginning of the image view and the beginning of the actual image
    private float convertBitmapToViewCoords(int bitmapCoord, double imagePadding, double scale) {
        return (float) ((bitmapCoord * scale) + imagePadding);
    }

    // Note: If adjustForPolygonViewCircles is true, the output will be the coordinates such that the
    //          center of the circle will be over the input coordinates
    // Note: Padding values are the widths between the image view and the start of the actual image
    private PointF convertBitmapToViewPoint(int bitmapX, int bitmapY, double imageWidthPadding,
                                            double imageHeightPadding, double scale, boolean adjustForPolygonViewCircles) {
        float xCoord = convertBitmapToViewCoords(bitmapX, imageWidthPadding, scale);
        float yCoord = convertBitmapToViewCoords(bitmapY, imageHeightPadding, scale);

        if (adjustForPolygonViewCircles) {
            xCoord -= circleDiameter / 2;
            yCoord -= circleDiameter / 2;
        }

        return new PointF(xCoord, yCoord);
    }

    // See convertBitmapToViewPoint for notes
    private PointF convertBitmapPointToViewPoint(Point bitmapPt, double imageWidthPadding, double imageHeightPadding,
                                                 double scale, boolean adjustForPolygonViewCircles) {
        return convertBitmapToViewPoint(bitmapPt.x, bitmapPt.y, imageWidthPadding, imageHeightPadding, scale, adjustForPolygonViewCircles);
    }

    // Note: If adjustForPolygonViewCircles is true, the input location is taken as the circle's location
    //          which is the top left of the circle, and the output will be the coordinates
    //          corresponding to the center of the circle
    // Note: Padding values are the widths between the image view and the start of the actual image
    private Point convertViewToBitmapPoint(double viewX, double viewY, double imageWidthPadding,
                                           double imageHeightPadding, double scale, boolean adjustForPolygonViewCircles) {
        if (adjustForPolygonViewCircles) {
            viewX += circleDiameter / 2;
            viewY += circleDiameter / 2;
        }

        int xCoord = convertViewToBitmapCoords(viewX, imageWidthPadding, scale);
        int yCoord = convertViewToBitmapCoords(viewY, imageHeightPadding, scale);

        return new Point(xCoord, yCoord);
    }

    // See convertViewToBitmapPoint for notes
    private Point convertViewPointToBitmapPoint(PointF viewPt, double imageWidthPadding, double imageHeightPadding,
                                                 double scale, boolean adjustForPolygonViewCircles) {
        return convertViewToBitmapPoint(viewPt.x, viewPt.y, imageWidthPadding, imageHeightPadding, scale, adjustForPolygonViewCircles);
    }
}
