package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class MatchRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM K:mm a").withZone(ZoneId.of("UTC"));

    private final DbClient dbClient;
    private final Esi esi;
    private final Router router;

    public MatchRouter(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        router = Router.router(vertx);
        this.dbClient = dbClient;
        this.esi = esi;

        router.get("/:tournamentUuid/teams/:teamUuid/matches/data").handler(this::matchesByTeam);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        return new MatchRouter(vertx, render, dbClient, esi).router();
    }

    private static JsonObject formatCreatedAt(JsonObject m) {
        return m.put("created_at_formatted", DATE_FORMAT.format(m.getInstant("created_at")));
    }

    private void matchesByTeam(RoutingContext ctx) {
        String teamUuid = ctx.request().getParam("teamUuid");
        dbClient.callDb(DbClient.DB_MATCHES_FOR_TEAM, teamUuid)
                .map(msg -> (JsonArray) msg.body())
                .map(arr -> new JsonArray(arr.stream()
                        .map(o -> (JsonObject) o)
                        .map(MatchRouter::formatCreatedAt)
                        .collect(Collectors.toList())))
                .onFailure(ctx::fail)
                .onSuccess(arr -> ctx.response().end(arr.encode()));
    }


    private Router router() {
        return router;
    }
}
