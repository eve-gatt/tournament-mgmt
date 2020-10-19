package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.stream.Collectors;

public class MatchRouter {

    private final RenderHelper render;
    private final DbClient dbClient;
    private final Esi esi;
    private final Router router;

    public MatchRouter(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        this.esi = esi;

        router.get("/:tournamentUuid/teams/:teamUuid/matches/data").handler(this::matchesByTeam);
        router.get("/:tournamentUuid/match/:matchid").handler(this::viewMatch);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        return new MatchRouter(vertx, render, dbClient, esi).router();
    }

    private void viewMatch(RoutingContext ctx) {
        int matchid = Integer.parseInt(ctx.request().getParam("matchid"));
        dbClient.callDb(DbClient.DB_MATCH_BY_ID, matchid)
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    JsonObject json = (JsonObject) msg.body();
                    json.put("bluejson", new JsonObject(json.getString("bluejson")));
                    json.put("redjson", new JsonObject(json.getString("redjson")));
                    render.renderPage(ctx,
                            "/match/view",
                            new JsonObject().put("match", json));
                });
    }

    private void matchesByTeam(RoutingContext ctx) {
        String teamUuid = ctx.request().getParam("teamUuid");
        dbClient.callDb(DbClient.DB_MATCHES_FOR_TEAM, teamUuid)
                .map(msg -> (JsonArray) msg.body())
                .map(arr -> new JsonArray(arr.stream()
                        .map(o -> (JsonObject) o)
                        .map(RenderHelper::formatCreatedAt)
                        .collect(Collectors.toList())))
                .onFailure(ctx::fail)
                .onSuccess(arr -> ctx.response().end(arr.encode()));
    }


    private Router router() {
        return router;
    }
}
