package toys.eve.tournmgmt.common.vertx;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

public final class GlobalOptions {

    public static MeterRegistry registry;

    public static void globalInit() {
        registry = BackendRegistries.getDefaultNow();
    }

    public static VertxOptions vertxOptions(int metricsPort) {
        MicrometerMetricsOptions micrometerMetricsOptions = new MicrometerMetricsOptions()
                .setJvmMetricsEnabled(true)
                .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true)
                        .setStartEmbeddedServer(true)
                        .setEmbeddedServerOptions(new HttpServerOptions().setPort(metricsPort))
                        .setEmbeddedServerEndpoint("/metrics"))
//                .setLabels(EnumSet.allOf(Label.class))
                .setEnabled(true);
        return new VertxOptions().setMetricsOptions(micrometerMetricsOptions);
    }

    public static WebClientOptions getWebClientOptions() {
        return new WebClientOptions()
                .setUserAgent("Gatt2111@Tweetfleet");
    }
}
