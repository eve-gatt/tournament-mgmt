package eve.toys.tournmgmt.web.authn;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class AuthnTest {

    private static final JsonObject TEAM1 = new JsonObject()
            .put("name", "TEAM1")
            .put("is_captain", true);
    private static JsonObject TOURNAMENT1 = new JsonObject()
            .put("name", "TOURNAMENT1")
            .put("created_by", "Oliver Organiser")
            .put("roles", "organiser")  // current user is an organiser
            .put("is_captain", false)
            .put("is_pilot", true)
            .put("is_creator", false);
    private static JsonObject TOURNAMENT2 = new JsonObject()
            .put("name", "TOURNAMENT2")
            .put("created_by", "Oliver Organiser")
            .put("roles", "")
            .put("is_captain", true) // current user is a captain
            .put("is_pilot", false)
            .put("is_creator", false);
    private static JsonObject TOURNAMENT3 = new JsonObject()
            .put("name", "TOURNAMENT3")
            .put("created_by", "Oliver Organiser")
            .put("roles", "referee")
            .put("is_captain", false)
            .put("is_pilot", false)
            .put("is_creator", false);
    private static JsonObject TOURNAMENT4 = new JsonObject()
            .put("name", "TOURNAMENT1")
            .put("created_by", "Oliver Organiser")
            .put("roles", "")  // current user is an organiser
            .put("is_captain", false)
            .put("is_pilot", false)
            .put("is_creator", true);


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
    public void tournamentCreatorShouldBeAnOrganiser(TestContext context) {
        AuthnRule rule = new AuthnRule().role(Role.ORGANISER);
        rule.validate(TOURNAMENT4, "Oliver Organiser")
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
    public void currentUserIsAnOrganiserOrReferee(TestContext context) {
        AuthnRule rule = new AuthnRule().role(Role.ORGANISER, Role.REFEREE);
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

    @Test
    public void currentUserOnlyAReferee(TestContext context) {
        AuthnRule refereeRole = new AuthnRule().role(Role.REFEREE);
        AuthnRule captainRole = new AuthnRule().isCaptain();
        AuthnRule organiserRole = new AuthnRule().role(Role.ORGANISER);
        refereeRole.validate(TOURNAMENT3, "Rich Referee")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
        captainRole.validate(TOURNAMENT3, "Rich Referee")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
        organiserRole.validate(TOURNAMENT3, "Rich Referee")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
    }

    @Test
    public void currentUserIsSpecificTeamCaptain(TestContext context) {
        AuthnRule rule = new AuthnRule().isCaptain();
        rule.validate(TOURNAMENT2, TEAM1, "Cap'n")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
    }

    @Test
    public void superuserIsAllowedOnTeamSpecific(TestContext context) {
        AuthnRule rule = new AuthnRule("superman").role(Role.ORGANISER).isCaptain();
        rule.validate(TOURNAMENT2, TEAM1, "superman")
                .onComplete(context.asyncAssertSuccess(context::assertTrue));
    }

    @Test
    public void usingTeamSpecificCheckWithNonTeamSpecificRuleWillApplyProperly(TestContext context) {
        AuthnRule rule = new AuthnRule().role(Role.REFEREE);
        rule.validate(TOURNAMENT2, TEAM1, "Cap'n")
                .onComplete(context.asyncAssertSuccess(context::assertFalse));
    }

}
