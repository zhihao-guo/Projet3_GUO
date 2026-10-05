package wot.thermostat;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ThermostatDescription {
    public static final String ID = "thermostat";
    private ThermostatDescription() {}

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", ID);
        model.put("name", "Thermostat");
        model.put("description", "Virtual thermostat with configurable temperature simulation");
        model.put("properties", Map.of(
                "temperature", Map.of("type", "number", "unit", "degree Celsius", "readOnly", true),
                "target", Map.of("type", "number", "unit", "degree Celsius", "readOnly", false),
                "mode", Map.of("type", "string", "enum", new String[]{"off", "heat", "eco"}, "readOnly", false)));
        model.put("actions", Map.of("setTarget", Map.of("description", "Set the target temperature", "input", Map.of("type", "number"))));
        model.put("events", Map.of(
                "propertyChanged", Map.of("description", "A property changed"),
                "targetReached", Map.of("description", "Temperature reached its target from below")));
        model.put("links", Map.of("self", "/model", "properties", "/properties",
                "temperature", "/properties/temperature", "target", "/properties/target",
                "mode", "/properties/mode", "setTarget", "/actions/setTarget"));
        return model;
    }
}
