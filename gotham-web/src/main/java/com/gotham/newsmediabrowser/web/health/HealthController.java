package com.gotham.newsmediabrowser.web.health;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same-origin health bridges consumed by the header legends (chrome.js probes these).
 *
 * <p>Each endpoint returns HTTP 200 with {@code {"status":"UP"}} when the dependency answers,
 * or HTTP 503 with {@code {"status":"DOWN"}} otherwise, so a simple {@code response.ok} check
 * flips the legend.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ElasticsearchHealthChecker elasticsearch;
    private final ImageBindHealthChecker imagebind;

    public HealthController(ElasticsearchHealthChecker elasticsearch, ImageBindHealthChecker imagebind) {
        this.elasticsearch = elasticsearch;
        this.imagebind = imagebind;
    }

    @GetMapping("/elasticsearch")
    public ResponseEntity<Map<String, String>> elasticsearch() {
        return statusResponse("elasticsearch", elasticsearch.isUp());
    }

    @GetMapping("/imagebind")
    public ResponseEntity<Map<String, String>> imagebind() {
        return statusResponse("imagebind", imagebind.isUp());
    }

    private ResponseEntity<Map<String, String>> statusResponse(String service, boolean up) {
        HttpStatus status = up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of("service", service, "status", up ? "UP" : "DOWN"));
    }
}
