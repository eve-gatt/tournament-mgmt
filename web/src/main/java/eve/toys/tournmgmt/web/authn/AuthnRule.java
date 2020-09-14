package eve.toys.tournmgmt.web.authn;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuthnRule {

    private final String superuser;
    private List<Role> roles = new ArrayList<>();
    private boolean isSuperuser;
    private boolean isCaptain;
    private boolean isPilot;

    public AuthnRule() {
        this(null);
    }

    public AuthnRule(String superuser) {
        this.superuser = superuser == null ? "" : superuser;
    }

    public static AuthnRule create() {
        return new AuthnRule(System.getenv("SUPERUSER"));
    }

    public AuthnRule role(Role... roles) {
        this.roles.addAll(Arrays.asList(roles));
        return this;
    }

    public AuthnRule isSuperuser() {
        this.isSuperuser = true;
        return this;
    }

    public AuthnRule isCaptain() {
        this.isCaptain = true;
        return this;
    }

    public AuthnRule isPilot() {
        this.isPilot = true;
        return this;
    }

    public Future<Boolean> validate(JsonObject tournament, JsonObject team, String name) {
        return Future.future(promise ->
                validate(tournament, name)
                        .onFailure(promise::fail)
                        .onSuccess(tournamentLevel -> {
                            if (!team.containsKey("is_captain")) {
                                promise.fail("team is missing is_captain_field");
                            } else {
                                boolean checkCaptain = isCaptain && team.getBoolean("is_captain");
                                promise.complete(checkCaptain);
                            }
                        }));
    }

    public Future<Boolean> validate(JsonObject tournament, String name) {
        if (superuser.equals(name)) {
            return Future.future(promise -> promise.complete(true));
        }

        if (!tournament.containsKey("roles")) {
            return Future.future(promise -> promise.fail("missing roles field"));
        }
        String roles = tournament.getString("roles") == null ? "" : tournament.getString("roles");
        boolean checkRoles = !this.roles.isEmpty()
                && this.roles.stream().anyMatch(role -> roles.contains(role.name().toLowerCase()))
                || this.roles.contains(Role.ORGANISER) && tournament.getString("created_by").equals(name);

        if (!tournament.containsKey("is_captain")) {
            return Future.future(promise -> promise.fail("tournament is missing is_captain field"));
        }
        boolean checkCaptain = isCaptain && tournament.getBoolean("is_captain");

        if (!tournament.containsKey("is_pilot")) {
            return Future.future(promise -> promise.fail("missing is_pilot field"));
        }
        boolean checkPilot = isPilot && tournament.getBoolean("is_pilot");

        return Future.future(promise -> promise.complete(checkRoles || checkCaptain || checkPilot));
    }
}
