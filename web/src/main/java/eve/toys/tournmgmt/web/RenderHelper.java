package eve.toys.tournmgmt.web;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

import java.util.Map;

public class RenderHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderHelper.class.getName());
    private final JadeTemplateEngine engine;

    public RenderHelper(JadeTemplateEngine engine) {
        this.engine = engine;
    }

    public static void doRedirect(HttpServerResponse response, String url) {
        response.putHeader("location", url).setStatusCode(302).end();
    }

    public void renderPage(RoutingContext ctx, String path, JsonObject data) {
        handle(ctx, Future.future(promise -> engine.render(data, "web-templates" + path, promise)));
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
        handle(ctx, Future.future(promise -> engine.render(data, "web-templates" + path, promise)));
    }

    public String getBaseUrl() {
        return System.getenv().getOrDefault("BASE_URL", "");
    }
}
