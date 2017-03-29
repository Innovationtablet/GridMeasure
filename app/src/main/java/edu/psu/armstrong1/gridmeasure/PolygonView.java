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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jhansi on 28/03/15.
 */
public class PolygonView extends FrameLayout {
    public static final int STARTING_NUM_POINTS = 4;    // Number of points to start with

    protected Context context;
    private Paint paint;                                // Paint object describing how to draw lines
    private ArrayList<ImageView> pointers;              // list of points of the bounding box
    private PolygonView polygonView;                    // reference to this
    private float circleDiameter = 0;                   // diameter of each circle point
    PointF centerPoint;                                 // center point of the bounding box
    float totalMovementX;                               // total movement when moving points around
    float totalMovementY;                               //   (used for detecting long-presses)

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

    public void setNumberOfPoints(int numberOfPoints) {
        setStartingPointsBox(new Point(0,0), new Point(this.getWidth(), this.getHeight()), numberOfPoints);
    }

    public void setStartingPointsBox(Point topLeft, Point bottomRight, int numberOfPoints) {
        // Remove old pointers if applicable
        if (pointers != null) {
            for (ImageView point : pointers) {
                removeView(point);
            }
        }

        // Initialize pointers
        pointers = new ArrayList<ImageView>(numberOfPoints);

        // Add points evenly around the border in a square
        int pointsToDistrib = numberOfPoints - 4;
        int pointsPerSide[] = new int[4];
        int leftOverPoints = pointsToDistrib % 4;
        int boxWidth = bottomRight.x - topLeft.x;
        int boxHeight = bottomRight.y - topLeft.y;

        // Set the points per side
        Arrays.fill(pointsPerSide, 0, leftOverPoints, (int) ((pointsToDistrib / 4) + 1));
        Arrays.fill(pointsPerSide, leftOverPoints, 4, (int) (pointsToDistrib / 4));

        // Add points to the corners
        pointers.add(getImageView(topLeft.x, topLeft.y));
        pointers.add(getImageView(bottomRight.x, topLeft.y));
        pointers.add(getImageView(bottomRight.x, bottomRight.y));
        pointers.add(getImageView(topLeft.x, bottomRight.y));

        // Add points along top side
        for (int i = 1; i <= pointsPerSide[0]; i++) {
            pointers.add(getImageView(topLeft.x + i * boxWidth / (pointsPerSide[0] + 1), topLeft.y));
        }

        // Add points along right side
        for (int i = 1; i <= pointsPerSide[1]; i++) {
            pointers.add(getImageView(bottomRight.x, topLeft.y + i * boxHeight / (pointsPerSide[1] + 1)));
        }

        // Add points along bottom side
        for (int i = 1; i <= pointsPerSide[2]; i++) {
            pointers.add(getImageView(topLeft.x + i * boxWidth / (pointsPerSide[2] + 1), bottomRight.y));
        }

        // Add points along left side
        for (int i = 1; i <= pointsPerSide[3]; i++) {
            pointers.add(getImageView(topLeft.x, topLeft.y + i * boxHeight / (pointsPerSide[3] + 1)));
        }

        // Sort the points in CCW order
        sortPoints();

        // Add points to the view
        for (ImageView view : pointers) {
            addView(view);
        }
    }

    public Map<Integer, PointF> getPoints() {

        Map<Integer, PointF> points = new HashMap<>();
        for (int i = 0; i < pointers.size(); i++) {
            points.put(i, new PointF(pointers.get(i).getX(), pointers.get(i).getY()));
        }

        return points;
    }

    public Map<Integer, PointF> getOrderedPoints() {
        // Sort the points in CCW order
        sortPoints();

        // Put points in a map so that keys 0 - size are in CCW order
        Map<Integer, PointF> orderedPoints = new HashMap<>();
        for (int i = 0; i < pointers.size(); i++) {
            orderedPoints.put(i, new PointF(pointers.get(i).getX(), pointers.get(i).getY()));
        }

        return orderedPoints;
    }

    public void setPoints(Map<Integer, PointF> pointFMap) {
        if (pointFMap.size() >= 4) {
            setPointsCoordinates(pointFMap);
        }
    }

    private void setPointsCoordinates(Map<Integer, PointF> pointFMap) {
        int i = 0;

        // Set the points to the locations in pointFMap
        // Note: This will set as many points as possible (if pointFMap.size != pointers.size)
        while (i < pointers.size() && i < pointFMap.size()) {
            pointers.get(i).setX(pointFMap.get(i).x);
            pointers.get(i).setY(pointFMap.get(i).y);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    public boolean isValidShape(Map<Integer, PointF> pointFMap) {
        return pointFMap.size() >= 4;
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
        // Always have at least 4 points
        if (pointers.size() > 4) {
            return true;
        } else {
            if (showWarning) {
                Toast.makeText(context, R.string.warn_4_points, Toast.LENGTH_LONG).show();
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

    private void sortPoints() {
        // Get the center point
        calculateCenterPoint();

        // Sort points counter-clockwise around center point
        // Note: if points are on the same line radiating from center point, farther one is first
        Collections.sort(pointers, new Comparator<ImageView>() {
            public int compare(ImageView p1, ImageView p2) {
                PointF a = new PointF(p1.getX(), p1.getY());
                PointF b = new PointF(p2.getX(), p2.getY());

                // Check if points are in different quadrants wrt the center point
                if (a.x - centerPoint.x >= 0 && b.x - centerPoint.x < 0) {
                    return 1;
                } else if (a.x - centerPoint.x < 0 && b.x - centerPoint.x >= 0) {
                    return -1;
                } else if (a.x - centerPoint.x == 0 && b.x - centerPoint.x == 0) {
                    if (a.y - centerPoint.y >= 0 || b.y - centerPoint.y >= 0) {
                        return (int) (a.y - b.y);
                    }
                    return (int) (b.y - a.y);
                }

                // Compute the cross product of vectors (center -> a) x (center -> b)
                float det = (a.x - centerPoint.x) * (b.y - centerPoint.y) - (b.x - centerPoint.x) * (a.y - centerPoint.y);
                if (det < 0) {
                    return 1;
                } else if (det > 0) {
                    return -1;
                }

                // Points a and b are on the same line from the center
                // Check which point is closer to the center
                float d1 = (a.x - centerPoint.x) * (a.x - centerPoint.x) + (a.y - centerPoint.y) * (a.y - centerPoint.y);
                float d2 = (b.x - centerPoint.x) * (b.x - centerPoint.x) + (b.y - centerPoint.y) * (b.y - centerPoint.y);
                return (int) (d1 - d2);
            }
        });
    }

    private void checkZoom(MotionEvent event, View view) {
        int eid = event.getAction();

        // Turn on zooming if screen is being touched; off otherwise
        switch (eid) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // Zoom in on center of circle
                ((TakePictureActivity) context).zoomLocation(view.getX() + view.getWidth() / 2, view.getY() + view.getHeight() / 2, false);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                ((TakePictureActivity) context).stopZooming();
                break;

            default:
                break;
        }
    }
}
