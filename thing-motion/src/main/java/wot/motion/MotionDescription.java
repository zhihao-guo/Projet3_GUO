package wot.motion;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MotionDescription {
    public static final String ID = "motion";
    private MotionDescription() {}

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", ID);
        model.put("name", "Motion Sensor");
        model.put("description", "Virtual motion sensor triggered by an action");
        model.put("properties", Map.of("lastMotion", Map.of("type", "string", "format", "date-time", "readOnly", true)));
        model.put("actions", Map.of("simulateMotion", Map.of("description", "Simulate a motion event")));
        model.put("events", Map.of("motion", Map.of("description", "Motion detected, with lastMotion timestamp")));
        model.put("links", Map.of("self", "/model", "properties", "/properties",
                "lastMotion", "/properties/lastMotion", "simulateMotion", "/actions/simulateMotion"));
        return model;
    }
}
