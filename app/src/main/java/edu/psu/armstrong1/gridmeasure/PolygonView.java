/*
Code derived from jhansireddy's AndroidScannerDemo Github repository, specifically the PolygonView Class:
    https://github.com/jhansireddy/AndroidScannerDemo
    https://github.com/jhansireddy/AndroidScannerDemo/blob/master/ScanDemoExample/scanlibrary/src/main/java/com/scanlibrary/PolygonView.java

The work is protected under the following license:
    MIT License

    Copyright (c) 2016 Jhansi Karee

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

package edu.psu.armstrong1.gridmeasure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.support.v7.widget.PopupMenu;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jhansi on 28/03/15.
 */
public class PolygonView extends FrameLayout {
    public static final int STARTING_NUM_POINTS = 4;    // Number of points to start with
    public static final int MINIMUM_NUM_POINTS = 3;     // Minimum number of points shape must have
    public static final int DPAD_MOVEMENT_PIXELS = 1;   // the number of pixels to move with each click of the dpad
    public static final int DPAD_UP = 1;                // Constants for the directions
    public static final int DPAD_DOWN = -1;
    public static final int DPAD_LEFT = -2;
    public static final int DPAD_RIGHT = 2;

    protected Context context;
    private Paint paint;                                // Paint object describing how to draw lines
    private ArrayList<ImageView> pointers;              // list of points of the bounding box
    private PolygonView polygonView;                    // reference to this
    private float circleDiameter = 0;                   // diameter of each circle point
    PointF centerPoint;                                 // center point of the bounding box
    float totalMovementX;                               // total movement when moving points around
    float totalMovementY;                               //   (used for detecting long-presses)
    public boolean dpadShowing = false;                 // whether or not the dpad is showing
    RelativeLayout dpad;                                // the dpad
    int dpadDimension;                                  // the size of the dpad
    int dpadPointer;                                    // pointer index that the dpad is on

    public PolygonView(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public PolygonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public PolygonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    private void init() {
        // Initialize variables
        polygonView = this;

        // Set starting points
        setStartingPointsBox(new Point(0,0), new Point(this.getWidth(), this.getHeight()), STARTING_NUM_POINTS);

        initPaint();
    }

    @Override
    protected void attachViewToParent(View child, int index, ViewGroup.LayoutParams params) {
        super.attachViewToParent(child, index, params);
    }

    private void initPaint() {
        paint = new Paint();
        paint.setColor(getResources().getColor(R.color.blue));
        paint.setStrokeWidth(2);
        paint.setAntiAlias(true);
    }

    // If initializeStartingPositions is false, all points will be at (0,0)
    // Else, if initializeStartingPositions is true, points are arranged in a rectangle on the boundary of PolygonView
    public void setNumberOfPoints(int numberOfPoints, boolean initializeStartingPositions) {
        if (numberOfPoints >= MINIMUM_NUM_POINTS) {
            if (initializeStartingPositions) {
                setStartingPointsBox(new Point(0, 0), new Point(this.getWidth(), this.getHeight()), numberOfPoints);
            } else {
                initializePoints(numberOfPoints);
            }
        } else {
            Log.d("PolygonView", "setNumberOfPoints failed. Not enough points (" + numberOfPoints + ")");
        }
    }

    private void initializePoints(int numberOfPoints) {
        // Remove old pointers if applicable
        if (pointers != null) {
            for (ImageView point : pointers) {
                removeView(point);
            }
        }

        // Initialize pointers
        pointers = new ArrayList<ImageView>(numberOfPoints);

        // Initialize each point to (0,0)
        for (int i = 0; i < numberOfPoints; i++) {
            ImageView v = getImageView(0,0);
            pointers.add(v);
            addView(v);
        }
    }

    public void setStartingPointsBox(Point topLeft, Point bottomRight, int numberOfPoints) {
        int index = 0;

        if (numberOfPoints < MINIMUM_NUM_POINTS) {
            Log.d("PolygonView", "setStartingPointsBox failed. Not enough points (" + numberOfPoints + ")");
            return;
        }

        // Initialize the points
        initializePoints(numberOfPoints);

        // Add points evenly around the border in a square
        int pointsToDistrib = numberOfPoints - 4;

        if (numberOfPoints == MINIMUM_NUM_POINTS) {
            // Only using 3 points -> pointsToDistrib should be 0 (not -1)
            pointsToDistrib = 0;
        }

        int pointsPerSide[] = new int[4];
        int leftOverPoints = pointsToDistrib % 4;
        int boxWidth = bottomRight.x - topLeft.x;
        int boxHeight = bottomRight.y - topLeft.y;

        // Set the points per side
        Arrays.fill(pointsPerSide, 0, leftOverPoints, (int) ((pointsToDistrib / 4) + 1));
        Arrays.fill(pointsPerSide, leftOverPoints, 4, (int) (pointsToDistrib / 4));

        // Add point to the top left corner
        setPointerToLocation(index, topLeft.x, topLeft.y);

        // Add points along top side
        for (int i = 1; i <= pointsPerSide[0]; i++) {
            setPointerToLocation(index + i, topLeft.x + i * boxWidth / (pointsPerSide[0] + 1), topLeft.y);
        }

        // Update index
        index += pointsPerSide[0] + 1;

        // Add point to the top right corner
        setPointerToLocation(index, bottomRight.x, topLeft.y);

        // Add points along right side
        for (int i = 1; i <= pointsPerSide[1]; i++) {
            setPointerToLocation(index + i, bottomRight.x, topLeft.y + i * boxHeight / (pointsPerSide[1] + 1));
        }

        // Update index
        index += pointsPerSide[1] + 1;

        // Add point to the bottom right corner
        setPointerToLocation(index, bottomRight.x, bottomRight.y);

        // Add points along bottom side
        for (int i = 1; i <= pointsPerSide[2]; i++) {
            setPointerToLocation(index + i, bottomRight.x - i * boxWidth / (pointsPerSide[2] + 1), bottomRight.y);
        }

        // Update index
        index += pointsPerSide[2] + 1;

        // If there are only 3 points, don't put a 4th
        if (numberOfPoints > MINIMUM_NUM_POINTS) {
            // Add point to the bottom left corner
            setPointerToLocation(index, topLeft.x, bottomRight.y);
        }

        // Add points along left side
        for (int i = 1; i <= pointsPerSide[3]; i++) {
            setPointerToLocation(index + i, topLeft.x, bottomRight.y - i * boxHeight / (pointsPerSide[3] + 1));
        }
    }

    private void setPointerToLocation(int index, float x, float y) {
        // Set the point at index to the location in point
        ImageView v = pointers.get(index);
        v.setX(x);
        v.setY(y);
    }

    public Map<Integer, PointF> getPoints() {
        Map<Integer, PointF> points = new HashMap<>();
        for (int i = 0; i < pointers.size(); i++) {
            points.put(i, new PointF(pointers.get(i).getX(), pointers.get(i).getY()));
        }

        return points;
    }

    public ArrayList<PointF> getPointsArray() {
        ArrayList<PointF> points = new ArrayList<>();
        for (int i = 0; i < pointers.size(); i++) {
            points.add(new PointF(pointers.get(i).getX(), pointers.get(i).getY()));
        }

        return points;
    }

    public void setPoints(Map<Integer, PointF> pointFMap) {
        if (isValidShape(pointFMap)) {
            setPointsCoordinates(pointFMap);
        }
    }

    private void setPointsCoordinates(Map<Integer, PointF> pointFMap) {
        int i = 0;

        // Set the points to the locations in pointFMap
        // Note: This will set as many points as possible (if pointFMap.size != pointers.size)
        while (i < pointers.size() && i < pointFMap.size()) {
            setPointerToLocation(i, pointFMap.get(i).x, pointFMap.get(i).y);
            i++;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Get the points
        Map<Integer, PointF> points = getPoints();
        int numberOfPoints = points.size();
        circleDiameter = pointers.get(0).getHeight();

        // Draw lines between successive points
        for (int i = 0; i < numberOfPoints; i++) {
            canvas.drawLine(points.get(i).x + (circleDiameter / 2), points.get(i).y + (circleDiameter / 2),
                    points.get((i + 1) % numberOfPoints).x + (circleDiameter / 2), points.get((i + 1) % numberOfPoints).y + (circleDiameter / 2), paint);
        }
    }

    private ImageView getImageView(int x, int y) {
        // Create image view with circle
        ImageView imageView = new ImageView(context);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageView.setLayoutParams(layoutParams);
        imageView.setImageResource(R.drawable.circle);

        // Set the location
        imageView.setX(x);
        imageView.setY(y);

        // Add listeners
        imageView.setOnLongClickListener(new LongPressListenerImpl());
        imageView.setOnTouchListener(new TouchListenerImpl());
        return imageView;
    }

    public void removeAllColorFilters() {
        for (ImageView v: pointers) {
            v.clearColorFilter();
        }
    }

    public void darkenPointer(int index) {
        pointers.get(index).setColorFilter(R.color.orange);
    }

    public void darkenPointers(ArrayList<Integer> indices) {
        for (int index: indices) {
            darkenPointer(index);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    public boolean isValidShape(Map<Integer, PointF> pointFMap) {
        return pointFMap.size() >= MINIMUM_NUM_POINTS;
    }

    private class TouchListenerImpl implements OnTouchListener {

        PointF DownPT = new PointF(); // Record Mouse Position When Pressed Down
        PointF StartPT = new PointF(); // Record Start Position of 'img'

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.d("PolygonView", "TouchListenerImpl event: " + event);
            int eid = event.getAction();
            switch (eid) {
                case MotionEvent.ACTION_MOVE:
                    // Get the movement coordinates
                    PointF mv = new PointF(event.getX() - DownPT.x, event.getY() - DownPT.y);

                    // Make sure movement would be within the view
                    if (((StartPT.x + mv.x + v.getWidth()) < polygonView.getWidth() && (StartPT.y + mv.y + v.getHeight() < polygonView.getHeight())) && ((StartPT.x + mv.x) > 0 && StartPT.y + mv.y > 0)) {
                        // Calculate new location
                        v.setX((int) (StartPT.x + mv.x));
                        v.setY((int) (StartPT.y + mv.y));
                        StartPT = new PointF(v.getX(), v.getY());

                        // Calculate total movement of this motion
                        totalMovementX += Math.abs(mv.x);
                        totalMovementY += Math.abs(mv.y);
                    }
                    break;
                case MotionEvent.ACTION_DOWN:
                    // Set the starting point
                    DownPT.x = event.getX();
                    DownPT.y = event.getY();
                    StartPT = new PointF(v.getX(), v.getY());

                    // Reset movement totals
                    totalMovementX = 0;
                    totalMovementY = 0;
                    break;
                case MotionEvent.ACTION_UP:
                    // Movement over - set color of shape
                    int color = 0;
                    if (isValidShape(getPoints())) {
                        color = getResources().getColor(R.color.blue);
                    } else {
                        color = getResources().getColor(R.color.orange);
                    }
                    paint.setColor(color);
                    break;
                default:
                    break;
            }

            // Redraw polygonView
            polygonView.invalidate();

            // Check if zoom should be turned on
            checkZoom(event, v);

            return false;
        }
    }

    private class LongPressListenerImpl implements OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            Log.d("PolygonView", "View long click? Total movement = (" + totalMovementX + ", " + totalMovementY + ")");

            // Check if movement is small enough
            if (totalMovementX + totalMovementY < circleDiameter / 2) {
                Log.d("PolygonView", "View long clicked: " + v);

                // Create the popup menu
                final PopupMenu popup = new PopupMenu(context, v);
                popup.getMenuInflater().inflate(R.menu.circle_popup_menu, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(new PopupMenuClickListener(pointers.indexOf(v)));

                // Show the popup menu
                popup.show();

                return true;
            } else {
                // Movement too large - not registered as long click
                Log.d("PolygonView", "View long click: false");
                return false;
            }
        }
    }

    private class PopupMenuClickListener implements PopupMenu.OnMenuItemClickListener{
        int pointPosition;

        public PopupMenuClickListener(int pointPos) {
            pointPosition = pointPos;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int buttonId = item.getItemId();

            // See which button was clicked
            switch(buttonId) {
                case R.id.circlePopup_addCircle:
                    // Add a new point after the current one
                    addPointAfter(pointPosition);
                    break;
                case R.id.circlePopup_deleteCircle:
                    // Remove the current point
                    removePoint(pointPosition, true);
                    break;
                case R.id.circlePopup_dpad:
                    // Show the dpad
                    dpadPointer = pointPosition;
                    showDpad(dpadPointer);
                    break;
                default:
                    // No action
                    break;
            }

            return true;
        }
    }


    public float getCircleDiameter() {
        return circleDiameter;
    }

    private void removePoint (int id, boolean showWarning) {
        // Make sure removing point leaves a valid shape
        if (isOkToRemovePoint(showWarning)) {
            // Remove the point
            View v = pointers.get(id);
            pointers.remove(id);
            removeView(v);
        }
    }

    private void removePoint (View point, boolean showWarning) {
        removePoint(pointers.indexOf(point), showWarning);
    }

    private boolean isOkToRemovePoint(boolean showWarning) {
        // Always have at least 3 points
        if (pointers.size() > MINIMUM_NUM_POINTS) {
            return true;
        } else {
            if (showWarning) {
                Toast.makeText(context, R.string.warn_3_points, Toast.LENGTH_LONG).show();
            }
            return false;
        }
    }

    private void addPointAfter (int id) {
        // Add new point halfway between pointer at id and next one
        int newX = (int) ((pointers.get(id).getX() + pointers.get((id + 1) % pointers.size()).getX()) / 2);
        int newY = (int) ((pointers.get(id).getY() + pointers.get((id + 1) % pointers.size()).getY()) / 2);

        // Add the new pointer
        ImageView newView = getImageView(newX, newY);
        pointers.add(id + 1, newView);
        addView(newView);
    }

    private void addPointAfter (View point) {
        addPointAfter(pointers.indexOf(point));
    }

    private void calculateCenterPoint() {
        centerPoint = new PointF();
        int size = pointers.size();
        for (ImageView point : pointers) {
            centerPoint.x += point.getX() / size;
            centerPoint.y += point.getY() / size;
        }
    }

    public void setupDpad(RelativeLayout dpadView) {
        dpad = dpadView;
        hideDpad();
    }


    private void showDpad(int pointId) {
        // Get the dpad's width
        dpadDimension = dpad.getWidth();

        // Calculate the x and y coordinates
        dpad.setX(pointers.get(pointId).getX() + ((circleDiameter - dpadDimension) / 2));
        dpad.setY(pointers.get(pointId).getY() + ((circleDiameter - dpadDimension) / 2));

        // Show the dpad
        Log.d("PolygonView", "Show Dpad");
        dpad.setVisibility(View.VISIBLE);
        dpadShowing = true;

        // Turn on zooming
        zoomDpad();
    }

    private void hideDpad() {
        // Hide the Dpad
        Log.d("PolygonView", "Hide Dpad");
        dpad.setVisibility(View.GONE);
        dpadShowing = false;

        // Stop zooming
        ((TakePictureActivity) context).stopZooming();
    }


    public void moveFromDPad(int direction) {
        Log.d("PolygonView", "Dpad: Movement detected");
        ImageView point = pointers.get(dpadPointer);
        switch (direction) {
            // Move the pointer and the dpad accordingly
            case DPAD_UP:
                Log.d("PolygonView", "Dpad: Move Up");
                point.setY(point.getY() - DPAD_MOVEMENT_PIXELS);
                dpad.setY(dpad.getY() - DPAD_MOVEMENT_PIXELS);
                break;
            case DPAD_DOWN:
                Log.d("PolygonView", "Dpad: Move Down");
                point.setY(point.getY() + DPAD_MOVEMENT_PIXELS);
                dpad.setY(dpad.getY() + DPAD_MOVEMENT_PIXELS);
                break;
            case DPAD_LEFT:
                Log.d("PolygonView", "Dpad: Move Left");
                point.setX(point.getX() - DPAD_MOVEMENT_PIXELS);
                dpad.setX(dpad.getX() - DPAD_MOVEMENT_PIXELS);
                break;
            case DPAD_RIGHT:
                Log.d("PolygonView", "Dpad: Move Right");
                point.setX(point.getX() + DPAD_MOVEMENT_PIXELS);
                dpad.setX(dpad.getX() + DPAD_MOVEMENT_PIXELS);
                break;
            default:
                Log.d("PolygonView", "Dpad: Invalid direction");
                break;
        }

        // Redraw lines
        invalidate();

        // Zoom on the new location
        zoomDpad();
    }

    public void zoomDpad() {
        zoomOnPoint(dpadPointer);
    }

    private void checkZoom(MotionEvent event, View view) {
        int eid = event.getAction();

        // Turn on zooming if screen is being touched; off otherwise
        switch (eid) {
            case MotionEvent.ACTION_DOWN:
                if (dpadShowing) {
                    // Hide the dpad if it's showing
                    hideDpad();
                }
            case MotionEvent.ACTION_MOVE:
                // Zoom in on center of circle
                ((TakePictureActivity) context).zoomLocation(view.getX() + view.getWidth() / 2, view.getY() + view.getHeight() / 2, false);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dpadShowing) {
                    // Stop zooming as long as the dpad isn't showing
                    ((TakePictureActivity) context).stopZooming();
                }
                break;

            default:
                break;
        }
    }

    private void zoomOnPoint (int index) {
        // Zoom in on center of circle
        ImageView view = pointers.get(index);
        ((TakePictureActivity) context).zoomLocation(view.getX() + view.getWidth() / 2, view.getY() + view.getHeight() / 2, false);
    }
}
