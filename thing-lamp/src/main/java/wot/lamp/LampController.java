package wot.lamp;

import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/{name}. */
@RestController
public class LampController {

    private final Map<String, Object> state = new LinkedHashMap<>();
    private final GatewayClient gateway;

    public LampController(GatewayClient gateway) {
        this.gateway = gateway;
        state.put("on", false);
        state.put("brightness", 50);
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return LampDescription.model();
    }


    @GetMapping("/properties")
    public synchronized Map<String, Object> properties() {
        return new LinkedHashMap<>(state);
    }

    @GetMapping("/properties/{name}")
    public synchronized ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return ResponseEntity.ok(Map.of("name", name, "value", state.get(name)));
    }

    @PutMapping("/properties/{name}")
    public synchronized ResponseEntity<Map<String, Object>> write(@PathVariable String name, @RequestBody Map<String, Object> body) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        Object value = body == null ? null : body.get("value");
        if (name.equals("on")) {
            if (!(value instanceof Boolean)) {
                return error(HttpStatus.BAD_REQUEST, "property on expects a boolean value");
            }
        } else {
            Integer brightness = setBrightness(value);
            if (brightness == null) {
                return error(HttpStatus.BAD_REQUEST, "brightness expects an integer between 0 and 100");
            }
            value = brightness;
        }
        set(name, value);
        return ResponseEntity.ok(Map.of("name", name, "value", value));
    }

    // the rules must write the property on, not call toggle
    @PostMapping("/actions/{name}")
    public synchronized ResponseEntity<Map<String, Object>> action(
            @PathVariable String name, @RequestBody(required = false) Map<String, Object> body) {
        switch (name) {
            case "toggle" -> set("on", !(Boolean) state.get("on"));
            case "setBrightness" -> {
                Integer brightness = setBrightness(body == null ? null : body.get("value"));
                if (brightness == null) {
                    return error(HttpStatus.BAD_REQUEST, "setBrightness expects an integer between 0 and 100");
                }
                set("brightness", brightness);
            }
            default -> {
                return error(HttpStatus.NOT_FOUND, "unknown action " + name);
            }
        }
        return ResponseEntity.ok(Map.of("action", name, "status", "completed",
                "properties", new LinkedHashMap<>(state)));
    }

    // TODO: property brightness (0..100) and action setBrightness
    private static Integer setBrightness(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double brightness = number.doubleValue();
        if (!Double.isFinite(brightness) || brightness < 0 || brightness > 100
                || brightness != Math.rint(brightness)) {
            return null;
        }
        return (int) brightness;
    }


    // All callers hold the controller lock; events are sent asynchronously.
    private void set(String name, Object value) {
        if (!Objects.equals(state.get(name), value)) {
            state.put(name, value);
            gateway.emit("propertyChanged", Map.of("property", name, "value", value));
        }
    }

    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
