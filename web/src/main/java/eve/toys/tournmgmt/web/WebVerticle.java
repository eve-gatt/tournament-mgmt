package eve.toys.tournmgmt.web;

import eve.toys.tournmgmt.web.routes.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.*;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class WebVerticle extends AbstractVerticle {

    private static final boolean pseudoStaticCaching = false;
    private static final String ESI_CLIENT = System.getenv("ESI_CLIENT");
    private static final String ESI_SECRET = System.getenv("ESI_SECRET");


    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        SessionStore sessionStore = LocalSessionStore.create(vertx);

        OAuth2Auth oauth2 = OAuth2Auth.create(vertx, OAuth2FlowType.AUTH_CODE, new OAuth2ClientOptions()
                .setClientID(ESI_CLIENT)
                .setClientSecret(ESI_SECRET)
                .setSite("https://login.eveonline.com/v2/oauth")
                .setTokenPath("/token")
                .setAuthorizationPath("/authorize")
                .setUserAgent(System.getProperty("http.agent"))
        );

        SessionHandler sessionHandler = SessionHandler.create(sessionStore)
                .setAuthProvider(oauth2);

        Router router = Router.router(vertx);

        router.route().handler(FaviconHandler.create("assets/favicon/android-chrome-512x512.png", 60L));
        router.get().failureHandler(ctx -> {
            ctx.failure().printStackTrace();
            ctx.response().end("failure");
        });
        router.route().handler(sessionHandler);

        AuthHandler redirectAuthHandler = RedirectAuthHandler.create(oauth2, "/login/start");
        router.route("/auth/*").handler(redirectAuthHandler);

        router.get("/js/*").handler(StaticHandler.create("web-js").setCachingEnabled(pseudoStaticCaching));
        router.get("/css/*").handler(StaticHandler.create("web-css").setCachingEnabled(pseudoStaticCaching));
        router.get("/assets/*").handler(StaticHandler.create("assets").setCachingEnabled(pseudoStaticCaching));

        JadeTemplateEngine engine = JadeTemplateEngine.create(vertx);
        RenderHelper render = new RenderHelper(engine, "web-templates");

        router.route().handler(ctx -> {
            User user = ctx.user();
            if (user != null) {
                AccessToken token = (AccessToken) ctx.user();
                JsonObject parsed = KeycloakHelper.parseToken(token.opaqueAccessToken());
                JsonObject character = new JsonObject()
                        .put("characterName", parsed.getString("name"))
                        .put("characterId", Integer.parseInt(parsed.getString("sub").split(":")[2]));
                ctx.data().put("character", character);
            }
            ctx.next();
        });

        router.post().handler(BodyHandler.create());
        router.mountSubRouter("/", HomeRouter.routes(vertx, render));
        router.mountSubRouter("/login", LoginRouter.routes(vertx, render, oauth2));
        router.mountSubRouter("/auth/tournament", TournamentRouter.routes(vertx, render));
        router.mountSubRouter("/auth/profile", ProfileRouter.routes(vertx, render));
        router.mountSubRouter("/auth/tournament", TeamsRouter.routes(vertx, render));


        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, new SockJSHandlerOptions());
        sockJSHandler.bridge(new BridgeOptions()
                        .addInboundPermitted(new PermittedOptions().setAddressRegex(".*"))
                        .addOutboundPermitted(new PermittedOptions().setAddressRegex(".*")),
                this::handleEvent);
        router.route("/ws/*").handler(sockJSHandler);

        router.get("/ping").handler(ctx -> ctx.response().end("OK"));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(6070);
        startPromise.complete();
    }


    private void handleEvent(BridgeEvent be) {
//        if (be.type() == BridgeEventType.REGISTER) {
//            LOGGER.info("ws client registered");
//        }
        be.complete(true);
    }

}
