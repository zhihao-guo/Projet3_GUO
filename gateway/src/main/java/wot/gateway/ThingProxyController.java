package wot.gateway;

import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Routes authenticated gateway requests to the registered thing. */
@RestController
@RequestMapping("/things/{id}")
public class ThingProxyController {
    private final ThingsController registry;
    private final RestClient rest;

    public ThingProxyController(ThingsController registry) {
        this.registry = registry;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        rest = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping("/properties")
    public ResponseEntity<byte[]> properties(@PathVariable String id) {
        return forward(id, HttpMethod.GET, "/properties", null);
    }

    @GetMapping("/properties/{name}")
    public ResponseEntity<byte[]> read(@PathVariable String id, @PathVariable String name) {
        return forward(id, HttpMethod.GET, "/properties/" + name, null);
    }

    @PutMapping("/properties/{name}")
    public ResponseEntity<byte[]> write(@PathVariable String id, @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body) {
        return forward(id, HttpMethod.PUT, "/properties/" + name, body);
    }

    @PostMapping("/actions/{name}")
    public ResponseEntity<byte[]> action(@PathVariable String id, @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body) {
        return forward(id, HttpMethod.POST, "/actions/" + name, body);
    }

    private ResponseEntity<byte[]> forward(String id, HttpMethod method, String path, Object body) {
        ThingsController.Thing thing = registry.find(id);
        String baseUrl = thing.baseUrl().replaceAll("/+$", "");
        try {
            RestClient.RequestBodySpec request = rest.method(method)
                    .uri(baseUrl + path).accept(MediaType.APPLICATION_JSON);
            if (method == HttpMethod.POST || method == HttpMethod.PUT) {
                request.contentType(MediaType.APPLICATION_JSON);
            }
            if (body != null) {
                request.body(body);
            }
            // exchange preserves the thing's 400/404 responses instead of throwing them.
            return request.exchange((outgoing, incoming) -> {
                MediaType type = incoming.getHeaders().getContentType();
                return ResponseEntity.status(incoming.getStatusCode())
                        .contentType(type == null ? MediaType.APPLICATION_JSON : type)
                        .body(incoming.getBody().readAllBytes());
            });
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "thing " + id + " is not reachable");
        }
    }
}
