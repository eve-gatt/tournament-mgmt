package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class LoginRouter {
    public static final String CODE = "a56hfg2";
    private static final String ESI_CALLBACK_URL = System.getenv("ESI_CALLBACK_URL");
    private final Router router;
    private final RenderHelper render;
    private final OAuth2Auth oauth2;

    public LoginRouter(Vertx vertx, RenderHelper render, OAuth2Auth oauth2) {
        router = Router.router(vertx);
        this.render = render;
        this.oauth2 = oauth2;
        router.get("/start").handler(this::start);
        router.get("/callback").handler(this::callback);
        router.get("/logout").handler(this::logout);
    }

    private void start(RoutingContext ctx) {
        String authorizationUri = oauth2.authorizeURL(new JsonObject()
                .put("redirect_uri", ESI_CALLBACK_URL)
                .put("scope", "esi-skills.read_skills.v1")
                .put("state", CODE));
        RenderHelper.doRedirect(ctx.response(), authorizationUri);
    }

    private void callback(RoutingContext ctx) {
        JsonObject tokenConfig = new JsonObject()
                .put("code", ctx.request().getParam("code"))
                .put("redirect_uri", ESI_CALLBACK_URL);

        oauth2.authenticate(tokenConfig, res -> {
            if (res.failed()) {
                System.err.println("Access Token Error: " + res.cause().getMessage());
                res.cause().printStackTrace();
                RenderHelper.doRedirect(ctx.response(), "/");
                return;
            }

            User user = res.result();
            ctx.setUser(user);
            ctx.session().regenerateId();

            String returnURL = ctx.session().remove("return_url");
            RenderHelper.doRedirect(ctx.response(), returnURL == null ? "/" : returnURL);
        });
    }

    private void logout(RoutingContext ctx) {
        ctx.clearUser();
        ctx.session().destroy();
        RenderHelper.doRedirect(ctx.response(), "/");
    }

    public static Router routes(Vertx vertx, RenderHelper render, OAuth2Auth oauth2) {
        return new LoginRouter(vertx, render, oauth2).router();
    }

    private Router router() {
        return router;
    }

}
