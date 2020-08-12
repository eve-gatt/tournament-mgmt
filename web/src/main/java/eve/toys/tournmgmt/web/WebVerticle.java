package eve.toys.tournmgmt.web;

import eve.toys.tournmgmt.web.routes.HomeRouter;
import eve.toys.tournmgmt.web.routes.TournamentRouter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
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

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        SessionStore sessionStore = LocalSessionStore.create(vertx);
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
//                .setAuthProvider(authProvider);

        Router router = Router.router(vertx);
        router.route().handler(FaviconHandler.create("assets/favicon/android-chrome-512x512.png", 60L));
        router.get().failureHandler(ctx -> {
            ctx.failure().printStackTrace();
            ctx.response().end("failure");
        });
        router.route().handler(sessionHandler);

//        AuthHandler redirectAuthHandler = RedirectAuthHandler.create(authProvider, "/login/start");
//        router.route("/auth/*").handler(redirectAuthHandler);

        router.get("/js/*").handler(StaticHandler.create("web-js").setCachingEnabled(pseudoStaticCaching));
        router.get("/css/*").handler(StaticHandler.create("web-css").setCachingEnabled(pseudoStaticCaching));
        router.get("/assets/*").handler(StaticHandler.create("assets").setCachingEnabled(pseudoStaticCaching));

        JadeTemplateEngine engine = JadeTemplateEngine.create(vertx);
        RenderHelper render = new RenderHelper(engine, "web-templates");

        router.post().handler(BodyHandler.create());
        router.mountSubRouter("/", HomeRouter.routes(vertx, render));
        router.mountSubRouter("/tournament", TournamentRouter.routes(vertx, render));

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
