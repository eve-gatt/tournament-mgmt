package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class ThunderdomeRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private final Router router;

    public ThunderdomeRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        router.get("/:tournamentUuid/thunderdome").handler(this::home);
    }

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/thunderdome/home", new JsonObject());
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new ThunderdomeRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }

}
