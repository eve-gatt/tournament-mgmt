package eve.toys.tournmgmt.web.authn;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2RBAC;
import toys.eve.tournmgmt.db.DbClient;

import java.util.EnumSet;

public class AppRBAC implements OAuth2RBAC {

    public static final EnumSet<Perm> ORGANISER_PERMS = EnumSet.of(
            Perm.canEdit,
            Perm.canSearchPilot,
            Perm.canManageTeams,
            Perm.canManageRoles,
            Perm.canManageBranding
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
        return Future.future(promise -> {
            user.isAuthorised(authority + ":" + tournament.getString("uuid"),
                    ar -> {
                        if (ar.failed()) {
                            promise.fail(ar.cause());
                        } else {
                            tournament.put(authority, ar.result());
                            promise.complete(tournament);
                        }
                    });
        });
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

    public static boolean isSuperuser(String characterName) {
        return characterName.equals(System.getenv("SUPERUSER"));
    }

    public enum Perm {
        canEdit,
        canSearchPilot,
        canManageTeams,
        canManageTD,
        canManageRoles,
        canManageBranding
    }
}
