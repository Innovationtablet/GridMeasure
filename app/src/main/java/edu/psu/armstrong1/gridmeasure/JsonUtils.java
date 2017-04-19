package edu.psu.armstrong1.gridmeasure;

/**
 * Created by Edward on 4/12/2017.
 */
import android.graphics.PointF;
import android.util.JsonWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

class JsonUtils {

    public static void ConvertPoints(OutputStream out, List<PointF> points) throws IOException{
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.beginObject();
        writer.name("perimeter");

        //writer.beginObject();
        //writer.name("points");
        writer.beginArray();

        for(PointF TempPoint: points){
            writer.name("x").value(TempPoint.x);
            writer.name("y").value(TempPoint.y);
        }

        writer.endArray();
        //writer.endObject();
        writer.endObject();
    }

}
