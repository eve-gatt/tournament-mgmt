package toys.eve.tournmgmt.common.util;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class RenderHelper {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderHelper.class.getName());
    private final JadeTemplateEngine engine;
    private final String pathPrefix;

    public RenderHelper(JadeTemplateEngine engine, String pathPrefix) {
        this.engine = engine;
        this.pathPrefix = pathPrefix;
    }

    public static void doRedirect(HttpServerResponse response, String url) {
        response.putHeader("location", url).setStatusCode(302).end();
    }

    public ZonedDateTime parseLandBotTimestamp(Double timestamp) {
        return Instant.ofEpochMilli(1000 * timestamp.longValue()).atZone(ZoneOffset.UTC);
    }

    public void renderPage(RoutingContext ctx, String path, JsonObject data) {
        JsonObject combined = new JsonObject(ctx.data()).mergeIn(data);
        handle(ctx, Future.future(promise -> engine.render(combined, pathPrefix + path, promise)));
    }

    private void handle(RoutingContext ctx, Future<Buffer> future) {
        future.onFailure(t -> {
            LOGGER.error("Logging from RenderHelper", t);
            ctx.fail(t);
        }).onSuccess(r -> {
            ctx.response().end(r);
        });
    }

    public void renderPage(RoutingContext ctx, String path, Map<String, Object> data) {
        HashMap<String, Object> combined = new HashMap<>();
        combined.putAll(data);
        combined.putAll(ctx.data());
        handle(ctx, Future.future(promise -> engine.render(combined, pathPrefix + path, promise)));
    }
}
