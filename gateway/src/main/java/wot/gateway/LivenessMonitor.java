package wot.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Probe each registered device periodically; keep unreachable devices in the registry. */
@Component
public class LivenessMonitor {
    private final ThingsController registry;
    private final RestClient rest;

    public LivenessMonitor(ThingsController registry,
            @Value("${gateway.liveness.timeout-ms:1000}") int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("liveness timeout must be positive");
        this.registry = registry;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    @Scheduled(initialDelayString = "${gateway.liveness.interval-ms:10000}",
            fixedDelayString = "${gateway.liveness.interval-ms:10000}")
    public void check() {
        for (ThingsController.Thing thing : registry.snapshot()) {
            String status = "online";
            try {
                rest.get().uri(thing.baseUrl().replaceAll("/+$", "") + "/model")
                        .retrieve().toBodilessEntity();
            } catch (RestClientException unavailable) {
                status = "offline";
            }
            registry.updateStatus(thing, status);
        }
    }
}
