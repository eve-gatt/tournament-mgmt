package eve.toys.tournmgmt.web;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class HomeRouter {

    private final Router router;
    private final RenderHelper render;

    public HomeRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        router.get("/").handler(this::home);
    }

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/home", new JsonObject(ctx.data()));
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new HomeRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }
}
