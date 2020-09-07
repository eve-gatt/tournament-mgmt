package eve.toys.tournmgmt.web;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.routes.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.*;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

public class WebVerticle extends AbstractVerticle {

    private static final boolean pseudoStaticCaching = false;
    private static final String ESI_CLIENT = System.getenv("ESI_CLIENT");
    private static final String ESI_SECRET = System.getenv("ESI_SECRET");
    private WebClient webClient;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        this.webClient = WebClient.create(vertx, new WebClientOptions().setUserAgent(System.getProperty("http.agent")));

        SessionStore sessionStore = LocalSessionStore.create(vertx);

        OAuth2Auth oauth2 = OAuth2Auth.create(vertx, OAuth2FlowType.AUTH_CODE, new OAuth2ClientOptions()
                .setClientID(ESI_CLIENT)
                .setClientSecret(ESI_SECRET)
                .setSite("https://login.eveonline.com/v2/oauth")
                .setTokenPath("/token")
                .setAuthorizationPath("/authorize")
                .setUserAgent(System.getProperty("http.agent")))
                .rbacHandler(new AppRBAC());

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

        router.route()
                .handler(BodyHandler.create())
                .pathRegex("^(?!/js/|/css/|/assets/).+")
                .handler(this::preRouting);

        router.get("/js/*").handler(StaticHandler.create("web-js").setCachingEnabled(pseudoStaticCaching));
        router.get("/css/*").handler(StaticHandler.create("web-css").setCachingEnabled(pseudoStaticCaching));
        router.get("/assets/*").handler(StaticHandler.create("assets").setCachingEnabled(pseudoStaticCaching));

        JadeTemplateEngine engine = JadeTemplateEngine.create(vertx);
        RenderHelper render = new RenderHelper(engine, "web-templates");

        router.mountSubRouter("/", HomeRouter.routes(vertx, render));
        router.mountSubRouter("/login", LoginRouter.routes(vertx, render, oauth2));
        router.mountSubRouter("/auth/tournament", TournamentRouter.routes(vertx, render, webClient));
        router.mountSubRouter("/auth/profile", ProfileRouter.routes(vertx, render));
        router.mountSubRouter("/auth/tournament", TeamsRouter.routes(vertx, render, webClient));
        router.mountSubRouter("/auth/referee", RefereeRouter.routes(vertx, render));
        router.route("/auth/superuser/*")
                .handler(RedirectAuthHandler.create(oauth2, "/login/start")
                        .addAuthority("isSuperuser"));
        router.mountSubRouter("/auth/superuser", SuperuserRouter.routes(vertx, render));

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

    private void preRouting(RoutingContext ctx) {
        User user = ctx.user();
        Future<?> characterInfo = Future.future(promise -> {
            if (user != null) {
                AccessToken token = (AccessToken) ctx.user();
                JsonObject parsed = KeycloakHelper.parseToken(token.opaqueAccessToken());
                JsonObject character = new JsonObject()
                        .put("characterName", parsed.getString("name"))
                        .put("characterId", Integer.parseInt(parsed.getString("sub").split(":")[2]));
                ctx.data().put("character", character);

                user.isAuthorised("isSuperuser", ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        promise.fail(ar.cause());
                    } else {
                        character.put("isSuperuser", ar.result());
                        promise.complete();
                    }
                });
            } else {
                promise.complete();
            }
        });
        Future<?> tournamentInfo = Future.future(promise -> {
            if (ctx.user() != null) {
                String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
                vertx.eventBus().request(DbClient.DB_FETCH_TOURNAMENTS,
                        new JsonObject(),
                        ar -> {
                            if (ar.failed()) {
                                ar.cause().printStackTrace();
                                promise.fail(ar.cause());
                            } else {
                                JsonArray tournaments = ((JsonArray) ar.result().body()).stream()
                                        .map(o -> (JsonObject) o)
                                        .filter(t -> AppRBAC.isSuperuser(characterName) || characterName.equals(t.getString("created_by")))
                                        .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
                                ctx.data().put("tournaments", tournaments);
                                promise.complete();
                            }
                        });
            } else {
                promise.complete();
            }
        });

        characterInfo.compose(v -> tournamentInfo)
                .onSuccess(f -> ctx.next())
                .onFailure(Throwable::printStackTrace);
    }


    private void handleEvent(BridgeEvent be) {
//        if (be.type() == BridgeEventType.REGISTER) {
//            LOGGER.info("ws client registered");
//        }
        be.complete(true);
    }

}
