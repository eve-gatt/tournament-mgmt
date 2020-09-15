package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class HomeRouter {

    private final Router router;
    private final RenderHelper render;

    public HomeRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        router.get("/").handler(this::home);
        router.get("/wip").handler(this::wip);
    }

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/home", new JsonObject());
    }

    private void wip(RoutingContext ctx) {
        render.renderPage(ctx, "/wip", new JsonObject());
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new HomeRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }
}
