package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class LoginRouter {
    public static final String CODE = "a56hfg2";
    public static final String COOKIE_NAME = "tmrt";
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

    public static Router routes(Vertx vertx, RenderHelper render, OAuth2Auth oauth2) {
        return new LoginRouter(vertx, render, oauth2).router();
    }

    private void start(RoutingContext ctx) {
        String authorizationUri = oauth2.authorizeURL(new JsonObject()
                .put("redirect_uri", ESI_CALLBACK_URL)
                .put("scope", "esi-skills.read_skills.v1")
                .put("state", CODE));

        Cookie cookie = ctx.getCookie(COOKIE_NAME);
        if (cookie != null) {
            OAuth2TokenImpl token = new OAuth2TokenImpl(oauth2,
                    new JsonObject().put("refresh_token", cookie.getValue()));
            token.refresh(ar -> {
                if (ar.failed()) {
                    ar.cause().printStackTrace();
                    RenderHelper.doRedirect(ctx.response(), authorizationUri);
                    return;
                }
                doSuccess(ctx, token);
            });
        } else {
            RenderHelper.doRedirect(ctx.response(), authorizationUri);
        }
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
            AccessToken token = (AccessToken) user;
            fetchSkills(token);
            ctx.addCookie(Cookie.cookie(COOKIE_NAME, token.opaqueRefreshToken()).setHttpOnly(true));
            doSuccess(ctx, user);
        });
    }

    private void fetchSkills(AccessToken token) {
        JsonObject parsed = KeycloakHelper.parseToken(token.opaqueAccessToken());
        String characterName = parsed.getString("name");
        int characterId = Integer.parseInt(parsed.getString("sub").split(":")[2]);
        token.fetch("https://esi.evetech.net/latest/characters/" + characterId + "/skills/",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        return;
                    }
                    System.out.println("Succesfully fetched skills for " + characterName);
                });
    }

    private void doSuccess(RoutingContext ctx, User user) {
        ctx.setUser(user);
        ctx.session().regenerateId();
        String returnURL = ctx.session().remove("return_url");
        render.renderPage(ctx,
                "/login/redirect",
                new JsonObject().put("return_url", returnURL == null ? "/" : returnURL));
    }

    private void logout(RoutingContext ctx) {
        ctx.clearUser();
        ctx.session().destroy();
        ctx.removeCookie(COOKIE_NAME);
        render.renderPage(ctx,
                "/login/redirect",
                new JsonObject().put("return_url", "/"));
    }

    private Router router() {
        return router;
    }

}
