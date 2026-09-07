package com.gotham.newsmediabrowser.web.bootstrap;

import com.gotham.newsmediabrowser.common.index.IndexBootstrapException;
import com.gotham.newsmediabrowser.common.index.IndexBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the idempotent index bootstrap once at application startup.
 *
 * <p>If Elasticsearch is unreachable or misconfigured (for example when only placeholder credentials
 * are present), the bootstrap failure is logged but the application <strong>still starts</strong>, so
 * the UI and its health legends remain available to report the problem. Operators fix connectivity
 * and restart; the next boot creates any missing index.
 */
@Component
public class IndexBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexBootstrapRunner.class);

    private final IndexBootstrapper bootstrapper;

    public IndexBootstrapRunner(IndexBootstrapper bootstrapper) {
        this.bootstrapper = bootstrapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var outcomes = bootstrapper.bootstrap();
            log.info("Elasticsearch index bootstrap complete: {}", outcomes);
        } catch (IndexBootstrapException e) {
            log.error("Elasticsearch index bootstrap failed — the application will keep running so the "
                    + "UI and health legends stay available. Fix Elasticsearch connectivity/config and "
                    + "restart to finish creating the indexes. Reason: {}", e.getMessage(), e);
        }
    }
}
