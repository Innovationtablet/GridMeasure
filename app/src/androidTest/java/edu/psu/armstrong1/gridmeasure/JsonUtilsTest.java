package edu.psu.armstrong1.gridmeasure;

import android.graphics.PointF;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;


/**
 * Created by hfs50 on 4/23/2017.
 */

@RunWith(AndroidJUnit4.class)
public class JsonUtilsTest {
    @Test
    public void testEmptyPoints() throws Exception {
        String out = JsonUtils.convertPoints(new ArrayList<PointF>());
        assertEquals("{\"perimeter\":[]}", out);
    }

    @Test
    public void testNonEmptyPoints() throws Exception {
        List<PointF> pts = new ArrayList<PointF>();
        pts.add(new PointF(0,0)); pts.add(new PointF(0,3.14f)); pts.add(new PointF(5.70f,3.13f)); pts.add(new PointF(5.71f,0.01f));
        String out = JsonUtils.convertPoints(pts);
        assertEquals("{\"perimeter\":[{\"x\":0.0,\"y\":0.0},{\"x\":0.0,\"y\":3.140000104904175},{\"x\":5.699999809265137,\"y\":3.130000114440918},{\"x\":5.710000038146973,\"y\":0.009999999776482582}]}", out);
    }
}
