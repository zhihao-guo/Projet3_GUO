package wot.thermostat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

/** Simulated temperature; all shared state is guarded by this controller's lock. */
@RestController
public class ThermostatController {
    private final Map<String, Object> state = new LinkedHashMap<>();
    private final GatewayClient gateway;
    private final double outside;
    private final double heatStep;
    private final double ecoStep;
    private final double coolingStep;

    public ThermostatController(GatewayClient gateway,
            @Value("${simulation.initial-temperature:17.5}") double initial,
            @Value("${simulation.outside-temperature:15}") double outside,
            @Value("${simulation.heat-step:0.2}") double heatStep,
            @Value("${simulation.eco-step:0.1}") double ecoStep,
            @Value("${simulation.cooling-step:0.05}") double coolingStep) {
        if (!Double.isFinite(initial) || !Double.isFinite(outside)
                || !Double.isFinite(heatStep) || heatStep <= 0
                || !Double.isFinite(ecoStep) || ecoStep <= 0
                || !Double.isFinite(coolingStep) || coolingStep <= 0) {
            throw new IllegalArgumentException("simulation temperatures must be finite and steps must be positive");
        }
        this.gateway = gateway;
        this.outside = outside;
        this.heatStep = heatStep;
        this.ecoStep = ecoStep;
        this.coolingStep = coolingStep;
        state.put("temperature", initial);
        state.put("target", 19.0);
        state.put("mode", "off");
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() { return ThermostatDescription.model(); }

    @GetMapping("/properties")
    public synchronized Map<String, Object> properties() { return new LinkedHashMap<>(state); }

    @GetMapping("/properties/{name}")
    public synchronized ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!state.containsKey(name)) return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        return ResponseEntity.ok(Map.of("name", name, "value", state.get(name)));
    }

    @PutMapping("/properties/{name}")
    public synchronized ResponseEntity<Map<String, Object>> write(@PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!state.containsKey(name)) return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        if (name.equals("temperature")) return error(HttpStatus.BAD_REQUEST, "temperature is read-only");
        Object value = body == null ? null : body.get("value");
        if (name.equals("mode")) {
            if (!(value instanceof String mode) || !Set.of("off", "heat", "eco").contains(mode)) {
                return error(HttpStatus.BAD_REQUEST, "mode must be off, heat or eco");
            }
        } else {
            Double target = validTarget(value);
            if (target == null) return error(HttpStatus.BAD_REQUEST, "target expects a finite number in degrees Celsius");
            value = target;
        }
        set(name, value);
        return ResponseEntity.ok(Map.of("name", name, "value", value));
    }

    @PostMapping("/actions/{name}")
    public synchronized ResponseEntity<Map<String, Object>> action(@PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!name.equals("setTarget")) return error(HttpStatus.NOT_FOUND, "unknown action " + name);
        Double target = validTarget(body == null ? null : body.get("value"));
        if (target == null) return error(HttpStatus.BAD_REQUEST, "setTarget expects a finite number in degrees Celsius");
        set("target", target);
        return ResponseEntity.ok(Map.of("action", name, "status", "completed", "properties", properties()));
    }

    @Scheduled(initialDelayString = "${simulation.tick-ms:1000}", fixedDelayString = "${simulation.tick-ms:1000}")
    public synchronized void tick() {
        double before = (Double) state.get("temperature");
        double target = (Double) state.get("target");
        String mode = (String) state.get("mode");
        double after;
        if (mode.equals("off")) {
            // Off: cool down to the configured outside temperature and hold there.
            after = before > outside ? Math.max(outside, before - coolingStep) : before;
        } else if (before < target) {
            double step = mode.equals("heat") ? heatStep : ecoStep;
            after = Math.min(target, before + step);
        } else {
            after = Math.max(target, before - coolingStep);
        }
        // Avoid tiny binary floating-point residues in displayed temperatures.
        after = Math.rint(after * 1_000_000_000.0) / 1_000_000_000.0;
        // Clamp again after rounding, so the final tick reaches the exact target.
        if (!mode.equals("off") && before < target && after >= target) after = target;
        set("temperature", after);
        if (!mode.equals("off") && before < target && after >= target) {
            gateway.emit("targetReached", Map.of("temperature", after, "target", target, "mode", mode));
        }
    }

    private static Double validTarget(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) return null;
        return number.doubleValue();
    }

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
