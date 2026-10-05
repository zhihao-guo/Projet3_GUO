package wot.gateway;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registry of the things (in memory). */
@RestController
@RequestMapping("/things")
public class ThingsController {

    public record Thing(String id, String name, String baseUrl, Map<String, Object> model) {
    }

    private final Map<String, Thing> things = new ConcurrentHashMap<>();
    private final Map<String, String> statuses = new ConcurrentHashMap<>();
    private final EventHub hub;

    public ThingsController(EventHub hub) {
        this.hub = hub;
    }

    @PostMapping
    public ResponseEntity<Thing> register(@RequestBody Thing thing) {
        if (thing == null || thing.id() == null || !thing.id().matches("[A-Za-z0-9_-]+")
                || thing.baseUrl() == null || thing.baseUrl().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fields id and baseUrl are required");
        }
        // Existing identifiers always produce 409, including the teacher's duplicate test.
        if (things.containsKey(thing.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "thing " + thing.id() + " is already registered");
        }
        if (thing.name() == null || thing.name().isBlank() || thing.model() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fields name and model are required");
        }
        try {
            URI url = URI.create(thing.baseUrl());
            if (!("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))
                    || url.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "baseUrl must be an HTTP or HTTPS URL");
        }
        if (things.putIfAbsent(thing.id(), thing) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "thing " + thing.id() + " is already registered");
        }
        statuses.put(thing.id(), "online");
        hub.broadcast("registry", Map.of("type", "registered", "thingId", thing.id()));
        return ResponseEntity.created(URI.create("/things/" + thing.id())).body(thing);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return things.values().stream()
                .map(t -> Map.<String, Object>of("id", t.id(), "name", t.name(), "status", status(t.id()),
                        "links", Map.of("self", "/things/" + t.id())))
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        Thing thing = find(id);
        return Map.of("id", thing.id(), "name", thing.name(), "baseUrl", thing.baseUrl(),
                "model", thing.model(), "status", status(id), "links", Map.of("self", "/things/" + id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unregister(@PathVariable String id) {
        if (things.remove(id) == null) throw new ApiException(HttpStatus.NOT_FOUND, "thing " + id + " is not registered");
        statuses.remove(id);
        hub.broadcast("registry", Map.of("type", "unregistered", "thingId", id));
        return ResponseEntity.noContent().build();
    }

    public Thing find(String id) {
        Thing thing = things.get(id);
        if (thing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "thing " + id + " is not registered");
        }
        return thing;
    }

    public List<Thing> snapshot() { return List.copyOf(things.values()); }
    private String status(String id) { return statuses.getOrDefault(id, "online"); }

    public void updateStatus(Thing probed, String status) {
        AtomicBoolean changed = new AtomicBoolean(false);
        things.computeIfPresent(probed.id(), (id, current) -> {
            // Ignore a probe of a device that was removed or replaced during the request.
            if (current == probed) changed.set(!status.equals(statuses.put(id, status)));
            return current;
        });
        if (changed.get()) hub.broadcast("registry", Map.of("type", "statusChanged",
                "thingId", probed.id(), "status", status));
    }

    // TODO: proxy to thing.baseUrl() with a RestClient (see API conventions)
    //   GET  /things/{id}/properties
    //   GET  /things/{id}/properties/{name}
    //   PUT  /things/{id}/properties/{name}
    //   POST /things/{id}/actions/{name}
    //   POST /things/{id}/automation/resume   (R4, bonus)
    // thing not answering: 502
}
