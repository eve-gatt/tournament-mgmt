package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.Branding;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

public class TeamsRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private Router router;

    public TeamsRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        router.get("/:tournamentUuid/*").handler(this::loadTournament);
        router.get("/:tournamentUuid/teams").handler(this::manage);
        router.get("/:tournamentUuid/teams/data").handler(this::teamsData);
        router.get("/:tournamentUuid/teams/import").handler(this::importTeams);
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new TeamsRouter(vertx, render).router();
    }

    private void teamsData(RoutingContext ctx) {
        ctx.response().end("[]");
    }

    private void loadTournament(RoutingContext ctx) {
        eventBus.request(DbClient.DB_TOURNAMENT_BY_UUID,
                ctx.request().getParam("tournamentUuid"),
                ar -> {
                    if (ar.failed()) {
                        ctx.fail(ar.cause());
                        return;
                    }
                    ctx.data().put("tournament", ar.result().body());
                    ctx.data().put("tournament_styles", Branding.EVE_NT_STYLES);

                    ctx.next();
                });
    }

    private void manage(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/manage", new JsonObject());
    }

    private void importTeams(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/import", new JsonObject());
    }

    private Router router() {
        return router;
    }
}
