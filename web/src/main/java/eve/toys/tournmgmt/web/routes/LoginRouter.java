package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class LoginRouter {
    public static final String CODE = "a56hfg2";
    private static final String URI_OAUTH = "https://login.eveonline.com/v2/oauth";
    private static final String URI_AUTHENTICATION = URI_OAUTH + "/authorize";
    private static final String URI_ACCESS_TOKEN = URI_OAUTH + "/token";
    private static final String ESI_CLIENT = System.getenv("ESI_CLIENT");
    private static final String ESI_SECRET = System.getenv("ESI_SECRET");
    private static final String ESI_CALLBACK_URL = System.getenv("ESI_CALLBACK_URL");
    private final Router router;
    private final RenderHelper render;
    private final OAuth2Auth oauth2;

    public LoginRouter(Vertx vertx, RenderHelper render) {
        oauth2 = OAuth2Auth.create(vertx, OAuth2FlowType.AUTH_CODE, new OAuth2ClientOptions()
                .setClientID(ESI_CLIENT)
                .setClientSecret(ESI_SECRET)
                .setSite("https://login.eveonline.com/v2/oauth")
                .setTokenPath("/token")
                .setAuthorizationPath("/authorize")
        );

        router = Router.router(vertx);
        this.render = render;
        router.get("/start").handler(this::start);
        router.get("/callback").handler(this::callback);
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
            } else {
                // Get the access token object (the authorization code is given from the previous step).
                User user = res.result();
                System.out.println(user);
                AccessToken token = (AccessToken) res.result();
                System.out.println(token);
                System.out.println(token.principal().encodePrettily());


//                JsonObject decode = new JWT().decode(token.accessToken()opaqueAccessToken());
//                System.out.println(decode.encodePrettily());

                RenderHelper.doRedirect(ctx.response(), "/");
            }
        });
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new LoginRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }

}
