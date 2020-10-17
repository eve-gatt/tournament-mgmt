package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class StreamRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));

    private final RenderHelper render;
    private final DbClient dbClient;
    private final Esi esi;
    private final Router router;
    private final EventBus eventBus;

    public StreamRouter(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        this.esi = esi;
        this.eventBus = vertx.eventBus();
        router.get("/stream/:code/overlay").handler(this::overlay);
        router.get("/stream/:code/overlay/:number").handler(this::overlayNumber);
        router.get("/auth/stream/manage").handler(this::manage);
        router.post("/auth/stream/manage/:number").handler(this::switchTo);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        return new StreamRouter(vertx, render, dbClient, esi).router();
    }

    private void overlayNumber(RoutingContext ctx) {
        int number = Integer.parseInt(ctx.request().getParam("number"));
        render.renderPage(ctx, "/stream/" + number, new JsonObject());
    }

    private void switchTo(RoutingContext ctx) {
        int number = Integer.parseInt(ctx.request().getParam("number"));
        JsonObject character = (JsonObject) ctx.data().get("character");
        String characterName = character.getString("characterName");

        dbClient.callDb(DbClient.DB_FETCH_REFRESH_TOKEN, characterName)
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    String refreshToken = ((JsonObject) msg.body()).getString("refresh_token");
                    String encoded = Base64.getEncoder().encodeToString(refreshToken.getBytes(StandardCharsets.UTF_8));
                    eventBus.publish("do-reload",
                            new JsonObject()
                                    .put("location", "/stream/" + encoded + "/overlay/" + number));
                    System.out.println("Sent do-reload: " + number);
                    ctx.response().end("{}");
                });
    }

    private void manage(RoutingContext ctx) {
        render.renderPage(ctx, "/stream/manage", new JsonObject());
    }

    private void overlay(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        String refreshToken = new String(Base64.getDecoder().decode(code));
        render.renderPage(ctx, "/stream/1", new JsonObject());
    }

    private Router router() {
        return router;
    }

}
