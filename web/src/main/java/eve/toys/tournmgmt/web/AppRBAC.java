package eve.toys.tournmgmt.web;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2RBAC;

import java.util.EnumSet;

public class AppRBAC implements OAuth2RBAC {

    public static final EnumSet<Perm> ORGANISER_PERMS = EnumSet.of(
            Perm.canEdit,
            Perm.canSearchPilot,
            Perm.canManageTeams,
            Perm.canManageRoles,
            Perm.canManageBranding
    );

    @Override
    public void isAuthorized(AccessToken user, String authority, Handler<AsyncResult<Boolean>> handler) {
        // TODO: remember to user.clearCache() to have this re-evaluated if persisted perms changes
        JsonObject parsed = KeycloakHelper.parseToken(user.opaqueAccessToken());
        String name = parsed.getString("name");

        if (authority.equals("isSuperuser")) {
            handler.handle(Future.succeededFuture(isSuperuser(name)));
        } else {
            handler.handle(Future.succeededFuture(true));
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
