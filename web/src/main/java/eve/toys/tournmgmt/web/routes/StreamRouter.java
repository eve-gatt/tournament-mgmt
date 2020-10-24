package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

public class StreamRouter {

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

        router.get("/stream/:code/overlay")
                .handler(this::checkCode)
                .handler(this::defaultOverlay);
        router.get("/stream/:code/overlay/:number")
                .handler(this::checkCode)
                .handler(this::overlayNumber);

        router.get("/auth/stream/manage").handler(this::manage);
        router.post("/auth/stream/manage/:number").handler(this::switchTo);

        router.get("/stream/:tournamentUuid/matches/latest-match/data").handler(this::latestMatch);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        return new StreamRouter(vertx, render, dbClient, esi).router();
    }

    private void latestMatch(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_LATEST_MATCH, null)
                .map(msg -> (JsonObject) msg.body())
                .map(RenderHelper::formatCreatedAt)
                .onFailure(ctx::fail)
                .onSuccess(match -> ctx.response().end(match.encode()));
    }

    private void overlayNumber(RoutingContext ctx) {
        int number = Integer.parseInt(ctx.request().getParam("number"));
        render.renderPage(ctx, "/stream/" + number, new JsonObject());
    }

    private void switchTo(RoutingContext ctx) {
        int number = Integer.parseInt(ctx.request().getParam("number"));
        JsonObject character = (JsonObject) ctx.data().get("character");
        String characterName = character.getString("characterName");

        dbClient.callDb(DbClient.DB_FETCH_STREAMER_TOKEN, characterName)
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    String uuid = ((JsonObject) msg.body()).getString("uuid");
                    eventBus.publish("streamer.do-reload." + uuid,
                            new JsonObject()
                                    .put("location", "/stream/" + uuid + "/overlay/" + number));
                    ctx.response().end("{}");
                });
    }

    private void manage(RoutingContext ctx) {
        render.renderPage(ctx, "/stream/manage", new JsonObject());
    }

    private void checkCode(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        dbClient.callDb(DbClient.DB_STREAMER_BY_CODE, code)
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    JsonArray results = (JsonArray) msg.body();
                    if (results.size() == 1) {
                        ctx.data().put("streamerCode", code);
                        ctx.data().put("streamerName", results.getJsonObject(0).getString("name"));
                        ctx.next();
                    } else {
                        ctx.fail(403);
                    }
                });
    }

    private void defaultOverlay(RoutingContext ctx) {
        render.renderPage(ctx, "/stream/1", new JsonObject());
    }

    private Router router() {
        return router;
    }

}
