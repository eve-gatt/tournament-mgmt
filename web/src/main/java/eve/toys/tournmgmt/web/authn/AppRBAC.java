package eve.toys.tournmgmt.web.authn;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2RBAC;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Objects;

public class AppRBAC implements OAuth2RBAC {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppRBAC.class.getName());
    private static final String SUPERUSER = System.getenv("SUPERUSER");

    private final EventBus eventBus;

    public AppRBAC(EventBus eventBus) {
        Objects.requireNonNull(SUPERUSER, "Please provide a SUPERUSER");

        this.eventBus = eventBus;
    }

    public static void refreshIfNeeded(AccessToken user, Handler<AsyncResult<Void>> handler) {
        if (user.opaqueAccessToken() == null || user.expired()) {
            LOGGER.info("Refreshing token");
            user.refresh(ar -> {
                if (ar.failed()) {
                    ar.cause().printStackTrace();
                    handler.handle(Future.failedFuture(user.opaqueRefreshToken() + " failed to refresh"));
                } else {
                    handler.handle(Future.succeededFuture());
                }
            });
        } else {
            handler.handle(Future.succeededFuture());
        }
    }

    public static Future<Boolean> tournamentAuthn(RoutingContext ctx, AuthnRule rule) {
        JsonObject tournament = (JsonObject) ctx.data().get("tournament");
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        return rule.validate(tournament, characterName)
                .onFailure(ctx::fail)
                .onSuccess(allowed -> {
                    if (allowed)
                        ctx.next();
                    else
                        ctx.fail(403);
                });
    }

    public static Future<Boolean> teamAuthn(RoutingContext ctx, AuthnRule rule) {
        JsonObject tournament = (JsonObject) ctx.data().get("tournament");
        JsonObject team = (JsonObject) ctx.data().get("team");
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        return rule.validate(tournament, team, characterName)
                .onFailure(ctx::fail)
                .onSuccess(allowed -> {
                    if (allowed)
                        ctx.next();
                    else
                        ctx.fail(403);
                });
    }

    public static boolean isSuperuser(String characterName) {
        return characterName.equals(SUPERUSER);
    }

    @Override
    public void isAuthorized(AccessToken user, String authority, Handler<AsyncResult<Boolean>> handler) {
        // TODO: remember to user.clearCache() to have this re-evaluated if persisted perms changes
        JsonObject parsed = KeycloakHelper.parseToken(user.opaqueAccessToken());
        String name = parsed.getString("name");
        if (authority.equals("isSuperuser")) {
            handler.handle(Future.succeededFuture(isSuperuser(name)));
        } else {
            String[] split = authority.split(":");
            String type = split[0];
            String tournamentUuid = split[1];
            eventBus.request(DbClient.DB_HAS_ROLE,
                    new JsonObject()
                            .put("name", name)
                            .put("uuid", tournamentUuid)
                            .put("type", type)
                    , ar -> {
                        if (ar.failed()) {
                            handler.handle(Future.failedFuture(ar.cause()));
                        } else {
                            boolean exists = (boolean) ar.result().body();
                            handler.handle(Future.succeededFuture(exists));
                        }
                    });
        }
    }

}
