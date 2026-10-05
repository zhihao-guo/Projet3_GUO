package wot.motion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MotionController {
    private final GatewayClient gateway;
    private String lastMotion; // null means no motion has been simulated yet.

    public MotionController(GatewayClient gateway) { this.gateway = gateway; }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() { return MotionDescription.model(); }

    @GetMapping("/properties")
    public synchronized Map<String, Object> properties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("lastMotion", lastMotion);
        return properties;
    }

    @GetMapping("/properties/{name}")
    public synchronized ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!name.equals("lastMotion")) return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", name);
        property.put("value", lastMotion);
        return ResponseEntity.ok(property);
    }

    @PutMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name) {
        if (!name.equals("lastMotion")) return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        return error(HttpStatus.BAD_REQUEST, "lastMotion is read-only");
    }

    @PostMapping("/actions/{name}")
    public synchronized ResponseEntity<Map<String, Object>> action(@PathVariable String name) {
        if (!name.equals("simulateMotion")) return error(HttpStatus.NOT_FOUND, "unknown action " + name);
        lastMotion = Instant.now().toString();
        gateway.emit("motion", Map.of("lastMotion", lastMotion));
        return ResponseEntity.ok(Map.of("action", name, "status", "completed", "properties", properties()));
    }

    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
