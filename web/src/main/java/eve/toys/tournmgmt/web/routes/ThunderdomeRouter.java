package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

public class ThunderdomeRouter {

    private final DbClient dbClient;
    private final RenderHelper render;
    private final Router router;

    public ThunderdomeRouter(Vertx vertx, RenderHelper render, DbClient dbClient) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        router.get("/:tournamentUuid/thunderdome").handler(this::home);
    }

    private void home(RoutingContext ctx) {
        String uuid = ctx.request().getParam("tournamentUuid");
        dbClient.callDb(DbClient.DB_TD_SUMMARY_BY_TOURNAMENT, uuid)
                .onFailure(ctx::fail)
                .onSuccess(result -> {
                    JsonObject td = (JsonObject) result.body();
                    render.renderPage(ctx, "/thunderdome/home", td);
                });
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient) {
        return new ThunderdomeRouter(vertx, render, dbClient).router();
    }

    private Router router() {
        return router;
    }

}
