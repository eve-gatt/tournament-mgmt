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
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StreamRouter {

    private final RenderHelper render;
    private final DbClient dbClient;
    private final HistoricalClient historical;
    private final Esi esi;
    private final Router router;
    private final EventBus eventBus;
    private final Map<Integer, String> tournaments = new HashMap<>();

    public StreamRouter(Vertx vertx, RenderHelper render, DbClient dbClient, HistoricalClient historical, Esi esi) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        this.historical = historical;
        this.esi = esi;
        this.eventBus = vertx.eventBus();

        initialiseTournaments();

        router.get("/stream/:code/overlay")
                .handler(this::checkCode)
                .handler(this::defaultOverlay);
        router.get("/stream/:code/overlay/:number")
                .handler(this::checkCode)
                .handler(this::overlayNumber);

        router.get("/auth/stream/manage").handler(this::manage);
        router.post("/auth/stream/manage/:number").handler(this::switchTo);

        router.get("/stream/:tournamentUuid/matches/latest-match/data").handler(this::latestMatch);
        router.get("/stream/:tournamentUuid/history/:name").handler(this::historicalDataForTeam);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, HistoricalClient historical, Esi esi) {
        return new StreamRouter(vertx, render, dbClient, historical, esi).router();
    }

    private void initialiseTournaments() {
        tournaments.put(0, "AT10");
        tournaments.put(1, "AT11");
        tournaments.put(4, "AT12");
        tournaments.put(6, "AT13");
        tournaments.put(15, "AT14");
        tournaments.put(16, "AT15");
        tournaments.put(17, "AT16");
        tournaments.put(-2, "AT8");
    }

    private void historicalDataForTeam(RoutingContext ctx) {
        historical.callDb(HistoricalClient.Call.HISTORICAL_FETCH_MATCHES_BY_TEAM, ctx.request().getParam("name"))
                .map(msg -> (JsonArray) msg.body())
                .map(data -> new JsonArray(data.stream()
                        .map(o -> (JsonObject) o)
                        .map(match -> matchWithTournamentName(match))
                        .collect(Collectors.toList()))
                )
                .onFailure(ctx::fail)
                .onSuccess(data -> ctx.response().end(data.encode()));
    }

    private JsonObject matchWithTournamentName(JsonObject match) {
        int tournamentId = match.getInteger("Tournament");
        String tournamentName = tournaments.getOrDefault(tournamentId, "unknown");
        return match.put("tournamentName", tournamentName);
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

        try {
            UUID.fromString(code);
        } catch (IllegalArgumentException e) {
            ctx.fail(403);
            return;
        }
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
