package eve.toys.tournmgmt.web.authn;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class AuthnTest {

    private static JsonObject TOURNAMENT1 = new JsonObject()
            .put("name", "TOURNAMENT1")
            .put("created_by", "Oliver Organiser")
            .put("roles", "organiser")  // current user is an organiser
            .put("is_captain", false)
            .put("is_pilot", true);

    private static JsonObject TOURNAMENT2 = new JsonObject()
            .put("name", "TOURNAMENT1")
            .put("created_by", "Oliver Organiser")
            .put("roles", "")
            .put("is_captain", true) // current user is a captain
            .put("is_pilot", false);


    @Test
    public void defineAuthnRules(TestContext context) {
        AuthnRule mustBeOrganiser = new AuthnRule().role(Role.ORGANISER);
        AuthnRule mustBeACaptain = new AuthnRule().isCaptain();
        AuthnRule mustBeACaptainOrAnOrganiser = new AuthnRule().role(Role.ORGANISER).isCaptain();
        AuthnRule mustBeAPilot = new AuthnRule().isPilot();
        AuthnRule mustBeAReferee = new AuthnRule().role(Role.REFEREE);
        AuthnRule mustBeAStaff = new AuthnRule().role(Role.STAFF);
    }

    @Test
    public void allRulesPassForSuperuser(TestContext context) {
        AuthnRule rule = new AuthnRule("Gatt2111");
        rule.validate(TOURNAMENT1, "Gatt2111")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
        rule.isCaptain().validate(TOURNAMENT1, "Gatt2111")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
        rule.validate(TOURNAMENT1, "Someone else")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
    }

    @Test
    public void canSpecificallyCheckSuperuser(TestContext context) {
        AuthnRule rule = new AuthnRule("Gatt2111").isSuperuser();
        rule.validate(TOURNAMENT1, "Gatt2111")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
        rule.validate(TOURNAMENT1, "Someone else")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
    }

    @Test
    public void tournamentCreatorIsAnOrganiser(TestContext context) {
        AuthnRule rule = new AuthnRule().role(Role.ORGANISER);
        rule.validate(TOURNAMENT1, "Oliver Organiser")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
    }

    @Test
    public void currentUserIsAnOrganiser(TestContext context) {
        AuthnRule rule = new AuthnRule().role(Role.ORGANISER);
        rule.validate(TOURNAMENT1, "Liam Loggedin")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
        rule.validate(TOURNAMENT2, "Liam Loggedin")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
    }

    @Test
    public void currentUserIsNotAReferee(TestContext context) {
        AuthnRule rule = new AuthnRule().role(Role.REFEREE);
        rule.validate(TOURNAMENT1, "Liam Loggedin")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
    }

    @Test
    public void captainOrOrganiser(TestContext context) {
        AuthnRule rule = new AuthnRule().role(Role.ORGANISER).isCaptain();
        rule.validate(TOURNAMENT1, "Liam Loggedin")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
        rule.validate(TOURNAMENT2, "Liam Loggedin")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
    }

    @Test
    public void currentUserIsAPilot(TestContext context) {
        AuthnRule rule = new AuthnRule().isPilot();
        rule.validate(TOURNAMENT1, "Peter Pilot")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
        rule.validate(TOURNAMENT2, "Richard Reserve")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
    }

}
