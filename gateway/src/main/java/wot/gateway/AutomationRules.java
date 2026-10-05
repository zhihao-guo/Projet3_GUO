package wot.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** R1 and R2: the rules call the devices directly, through their registered URLs. */
@Component
public class AutomationRules {
    private static final Logger log = LoggerFactory.getLogger(AutomationRules.class);
    private static final double COMFORT_TARGET = 19.0;
    private final ThingsController registry;
    private final EventHub hub;
    private final RestClient rest;
    private final RestartableTimer lampOff;
    private final Duration delay;
    private long motionGeneration;

    public AutomationRules(ThingsController registry, EventHub hub, TaskScheduler scheduler,
            @Value("${rules.lamp-off-seconds:60}") long seconds) {
        if (seconds <= 0) throw new IllegalArgumentException("rules.lamp-off-seconds must be positive");
        this.registry = registry;
        this.hub = hub;
        this.delay = Duration.ofSeconds(seconds);
        this.lampOff = new RestartableTimer(scheduler);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public synchronized void handle(EventsController.ThingEvent event) {
        if ("motion".equals(event.thingId()) && "motion".equals(event.type())) {
            long generation = ++motionGeneration;
            apply("R1", () -> {
                write("lamp", "on", true); // Never toggle: repeated motion must leave the lamp on.
                lampOff.restart(delay, () -> turnLampOff(generation));
                announce("R1", "Motion: lamp on; off timer restarted");
            });
            apply("R2", () -> {
                Map<?, ?> state = properties("thermostat");
                if (((Number) state.get("temperature")).doubleValue() < COMFORT_TARGET) {
                    write("thermostat", "target", COMFORT_TARGET);
                    write("thermostat", "mode", "heat");
                    announce("R2", "Motion below 19 C: heating to 19 C");
                }
            });
        } else if ("thermostat".equals(event.thingId()) && "targetReached".equals(event.type())) {
            apply("R2", () -> {
                Map<?, ?> state = properties("thermostat");
                // Check current state, so a delayed event does not stop a new heating cycle early.
                if ("heat".equals(state.get("mode"))
                        && ((Number) state.get("temperature")).doubleValue()
                        >= ((Number) state.get("target")).doubleValue()) {
                    write("thermostat", "mode", "off");
                    announce("R2", "Target reached: heating off");
                }
            });
        }
    }

    private synchronized void turnLampOff(long generation) {
        if (generation != motionGeneration) return; // An old timer must not undo a newer motion.
        apply("R1", () -> {
            write("lamp", "on", false);
            announce("R1", "No motion for " + delay.toSeconds() + " seconds: lamp off");
        });
    }

    private Map<?, ?> properties(String id) {
        return rest.get().uri(url(id) + "/properties").retrieve().body(Map.class);
    }

    private void write(String id, String name, Object value) {
        rest.put().uri(url(id) + "/properties/" + name)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("value", value))
                .retrieve().toBodilessEntity();
    }

    private String url(String id) { return registry.find(id).baseUrl().replaceAll("/+$", ""); }

    private void apply(String rule, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException error) {
            log.warn("{} could not run: {}", rule, error.getMessage());
            announce(rule, "Rule could not run; check registered devices and gateway log");
        }
    }

    private void announce(String rule, String message) {
        log.info("{}: {}", rule, message);
        hub.broadcast("rule", Map.of("rule", rule, "message", message, "timestamp", Instant.now().toString()));
    }

    @PreDestroy
    public void shutdown() { lampOff.cancel(); }
}
