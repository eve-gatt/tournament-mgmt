package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class RefereeRouter {

    private final RenderHelper render;
    private final Router router;

    public RefereeRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        router.get("/:tournamentUuid/referee").handler(this::home);
    }

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/referee/home", new JsonObject());
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new RefereeRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }

}
