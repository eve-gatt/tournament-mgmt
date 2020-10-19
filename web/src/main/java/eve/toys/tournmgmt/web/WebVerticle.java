package eve.toys.tournmgmt.web;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.job.JobClient;
import eve.toys.tournmgmt.web.routes.*;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class WebVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebVerticle.class.getName());

    private static final boolean IS_DEV = Boolean.parseBoolean(System.getProperty("isDev", "false"));

    private static final boolean pseudoStaticCaching = false;
    private static final String ESI_CLIENT = System.getenv("ESI_CLIENT");
    private static final String ESI_SECRET = System.getenv("ESI_SECRET");
    private WebClient webClient;
    private DbClient dbClient;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        Objects.requireNonNull(ESI_CLIENT, "Please supply ESI_CLIENT");
        Objects.requireNonNull(ESI_SECRET, "Please supply ESI_SECRET");
        Objects.requireNonNull(IS_DEV, "Please supply an isDev property");

        this.webClient = WebClient.create(vertx, new WebClientOptions().setUserAgent(System.getProperty("http.agent")));
        Esi esi = Esi.create(webClient, CircuitBreaker.create("esi-cb", vertx));
        dbClient = new DbClient(vertx.eventBus());
        JobClient jobClient = new JobClient(vertx.eventBus());

        SessionStore sessionStore = LocalSessionStore.create(vertx);

        OAuth2Auth oauth2 = OAuth2Auth.create(vertx, OAuth2FlowType.AUTH_CODE, new OAuth2ClientOptions()
                .setClientID(ESI_CLIENT)
                .setClientSecret(ESI_SECRET)
                .setSite("https://login.eveonline.com/v2/oauth")
                .setTokenPath("/token")
                .setAuthorizationPath("/authorize")
                .setUserAgent(System.getProperty("http.agent")))
                .rbacHandler(new AppRBAC(vertx.eventBus()));

        SessionHandler sessionHandler = SessionHandler.create(sessionStore)
                .setSessionTimeout(TimeUnit.HOURS.toMillis(1L))
                .setAuthProvider(oauth2);

        Router router = Router.router(vertx);

        router.route().handler(FaviconHandler.create("assets/favicon/android-chrome-512x512.png", 60L));
        router.get().failureHandler(ctx -> {
            if (ctx.statusCode() == 403) {
                ctx.response().setStatusCode(403).end("Forbidden");
                return;
            }
            ctx.failure().printStackTrace();
            ctx.response().end("failure");
        });
        router.route().handler(sessionHandler);

        AuthHandler redirectAuthHandler = RedirectAuthHandler.create(oauth2, "/login/start");

        router.route("/auth/*").handler(redirectAuthHandler);

        router.route()
                .handler(BodyHandler.create())
                .pathRegex("^(?!/js/|/css/|/assets/).+")
                .handler(ctx -> addCharacterInfoToContext(ctx)
                        .compose(v -> CompositeFuture.all(
                                addIsDev(ctx),
                                addPilotsTeamsToContext(ctx),
                                addThunderdomeDeetsToContext(ctx),
                                addTournamentInfoToContext(ctx)))
                        .onSuccess(f -> ctx.next())
                        .onFailure(throwable -> {
                            throwable.printStackTrace();
                            ctx.clearUser();
                            ctx.session().destroy();
                        }));

        router.get("/js/*").handler(StaticHandler.create("web-js").setCachingEnabled(pseudoStaticCaching));
        router.get("/css/*").handler(StaticHandler.create("web-css").setCachingEnabled(pseudoStaticCaching));
        router.get("/assets/*").handler(StaticHandler.create("assets").setCachingEnabled(pseudoStaticCaching));

        JadeTemplateEngine engine = JadeTemplateEngine.create(vertx);
        RenderHelper render = new RenderHelper(engine, "web-templates");

        router.mountSubRouter("/", HomeRouter.routes(vertx, render));
        router.mountSubRouter("/login", LoginRouter.routes(vertx, render, oauth2, dbClient));
        router.mountSubRouter("/auth/tournament", TournamentRouter.routes(vertx, render, webClient, esi, dbClient, jobClient));
        router.mountSubRouter("/auth/profile", ProfileRouter.routes(vertx, render, esi, dbClient));
        router.mountSubRouter("/auth/tournament", TeamsRouter.routes(vertx, render, esi, dbClient, jobClient));
        router.mountSubRouter("/auth/tournament", RefereeRouter.routes(vertx, render, dbClient, esi, oauth2));
        router.mountSubRouter("/auth/tournament", MatchRouter.routes(vertx, render, dbClient, esi));
        router.mountSubRouter("/auth/tournament", ThunderdomeRouter.routes(vertx, render, dbClient));
        router.route("/auth/superuser/*")
                .handler(RedirectAuthHandler.create(oauth2, "/login/start")
                        .addAuthority("isSuperuser"));
        router.mountSubRouter("/auth/superuser", SuperuserRouter.routes(vertx, render, jobClient, dbClient, esi));
        router.mountSubRouter("/auth/ccp", CCPRouter.routes(vertx, render, dbClient));
        router.mountSubRouter("/", StreamRouter.routes(vertx, render, dbClient, esi));

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

    private Future<Void> addIsDev(RoutingContext ctx) {
        ctx.data().put("isDev", IS_DEV);
        return Future.succeededFuture();
    }

    private Future<Void> addCharacterInfoToContext(RoutingContext ctx) {
        return Future.future(promise -> {
            if (ctx.user() != null) {
                AppRBAC.refreshIfNeeded((AccessToken) ctx.user(), v -> {
                    JsonObject parsed = KeycloakHelper.parseToken(((AccessToken) ctx.user()).opaqueAccessToken());
                    JsonObject character = new JsonObject()
                            .put("characterName", parsed.getString("name"))
                            .put("characterId", Integer.parseInt(parsed.getString("sub").split(":")[2]));
                    ctx.data().put("character", character);

                    if (parsed.getString("name").startsWith("CCP ")) {
                        character.put("isCCP", true);
                    }
                    ctx.user().isAuthorised("isSuperuser", ar -> {
                        if (ar.failed()) {
                            ar.cause().printStackTrace();
                            promise.fail(ar.cause());
                        } else {
                            character.put("isSuperuser", ar.result());
                            promise.complete();
                        }
                    });
                });
            } else {
                promise.complete();
            }
        });
    }

    private Future<Void> addPilotsTeamsToContext(RoutingContext ctx) {
        return Future.future(promise -> {
            if (ctx.data().containsKey("character")) {
                String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
                dbClient.callDb(DbClient.DB_TEAMS_BY_PILOT, characterName)
                        .onFailure(promise::fail)
                        .onSuccess(results -> {
                            JsonArray teams = (JsonArray) results.body();
                            ctx.data().put("pilotsTeams", teams);
                            promise.complete();
                        });
            } else {
                promise.complete();
            }
        });
    }

    private Future<Void> addThunderdomeDeetsToContext(RoutingContext ctx) {
        return Future.future(promise -> {
            if (ctx.user() != null) {
                String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
                dbClient.callDb(DbClient.DB_SELECT_TD_BY_PILOT, characterName)
                        .onFailure(ctx::fail)
                        .onSuccess(results -> {
                            ctx.data().put("thunderdome", results.body());
                            promise.complete();
                        });
            } else {
                promise.complete();
            }
        });
    }

    private Future<Void> addTournamentInfoToContext(RoutingContext ctx) {
        return Future.future(promise -> {
            if (ctx.data().containsKey("character")) {
                String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
                dbClient.callDb(DbClient.DB_TOURNAMENTS_CHARACTER_CAN_VIEW, characterName)
                        .onFailure(promise::fail)
                        .onSuccess(results -> {
                            JsonArray tournaments = (JsonArray) results.body();
                            ctx.data().put("tournaments", tournaments);
                            promise.complete();
                        });
            } else {
                promise.complete();
            }
        });
    }

    private void handleEvent(BridgeEvent be) {
//        if (be.type() == BridgeEventType.REGISTER) {
//            LOGGER.info("ws client registered");
//        }
        be.complete(true);
    }

}
