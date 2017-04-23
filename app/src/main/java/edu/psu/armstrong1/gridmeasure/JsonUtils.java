package edu.psu.armstrong1.gridmeasure;

/**
 * Created by Edward on 4/12/2017.
 */
import android.graphics.PointF;
import android.util.JsonWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.List;

class JsonUtils {

    public static String convertPoints(List<PointF> points) throws IOException{
        StringWriter sb = new StringWriter();
        JsonWriter writer = new JsonWriter(sb);

        writer.beginObject();
        writer.name("perimeter");

        writer.beginArray();
        for (PointF point : points) {
            writer.beginObject();
            writer.name("x").value(point.x);
            writer.name("y").value(point.y);
            writer.endObject();
        }
        writer.endArray();

        writer.endObject();

        return sb.toString();
    }

}
