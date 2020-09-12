package eve.toys.tournmgmt.web.authn;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2RBAC;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.db.DbClient;

import java.util.EnumSet;

public class AppRBAC implements OAuth2RBAC {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppRBAC.class.getName());

    public static final EnumSet<Perm> ORGANISER_PERMS = EnumSet.of(
            Perm.canEdit,
            Perm.canSearchPilot,
            Perm.canManageTeams,
            Perm.canManageRoles
    );
    private final EventBus eventBus;

    public AppRBAC(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public static void addPermissionsToTournament(JsonObject tournament) {
        ORGANISER_PERMS.forEach(r -> tournament.put(r.name(), true));
        tournament.put(Perm.canManageTD.name(), tournament.getBoolean("practice_on_td") || tournament.getBoolean("play_on_td"));
    }

    public static Future<JsonObject> futureForTournamentPriv(User user, String authority, JsonObject tournament) {
        return Future.future(promise ->
                user.isAuthorised(authority + ":" + tournament.getString("uuid"),
                        ar -> {
                            if (ar.failed()) {
                                promise.fail(ar.cause());
                            } else {
                                tournament.put(authority, ar.result());
                                promise.complete(tournament);
                            }
                        }));
    }

    public static void hasTournamentRole(RoutingContext ctx, String role) {
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        ctx.user().isAuthorised(role + ":" + ctx.request().getParam("tournamentUuid"), ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                ctx.fail(ar.cause());
            } else {
                JsonObject tournament = (JsonObject) ctx.data().get("tournament");
                if (ar.result()
                        || isSuperuser(characterName)
                        || characterName.equals(tournament.getString("created_by"))) {
                    ctx.next();
                } else {
                    ctx.fail(403);
                }
            }
        });
    }

    public static boolean isSuperuser(String characterName) {
        return characterName.equals(System.getenv("SUPERUSER"));
    }

    public static void refreshIfNeeded(AccessToken user, Handler<Void> handler) {
        if (user.expired()) {
            LOGGER.info("Refreshing token");
            user.refresh(ar -> {
                if (ar.failed()) {
                    ar.cause().printStackTrace();
                } else {
                    handler.handle(null);
                }
            });
        } else {
            handler.handle(null);
        }
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
            String uuid = split[1];
            eventBus.request(DbClient.DB_HAS_ROLE,
                    new JsonObject()
                            .put("name", name)
                            .put("uuid", uuid)
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

    public enum Perm {
        canEdit,
        canSearchPilot,
        canManageTeams,
        canManageTD,
        canManageRoles
    }
}
