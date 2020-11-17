package eve.toys.tournmgmt.web.match;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(VertxUnitRunner.class)
public class CompRuleTest {

    private static final JsonObject ABSOLUTION = new JsonObject()
            .put("ship", "Absolution")
            .put("class", "Battlecruiser")
            .put("exact_type", "Command Ship")
            .put("overlay", "Command Ship");
    private static final JsonObject GUARDIAN = new JsonObject()
            .put("ship", "Guardian")
            .put("class", "Cruiser")
            .put("exact_type", "Logistics Cruiser")
            .put("overlay", "Logistics Cruiser");
    private static final JsonObject ETANA = new JsonObject()
            .put("ship", "Etana")
            .put("class", "Cruiser")
            .put("exact_type", "Logistics Cruiser")
            .put("overlay", "Wildcard Logistics Cruiser");
    private static final JsonObject RABISU = new JsonObject()
            .put("ship", "Rabisu")
            .put("class", "Cruiser")
            .put("exact_type", "Logistics Cruiser")
            .put("overlay", "Wildcard Logistics Cruiser");
    private static final JsonObject STORK = new JsonObject()
            .put("ship", "Stork")
            .put("class", "Destroyer")
            .put("exact_type", "Command Destroyer")
            .put("overlay", "Command Destroyer");
    private static final JsonObject SVIPUL = new JsonObject()
            .put("ship", "Svipul")
            .put("class", "Destroyer")
            .put("exact_type", "Tactical Destroyer")
            .put("overlay", "Tactical Destroyer");
    private static final JsonObject BURST = new JsonObject()
            .put("ship", "Burst")
            .put("class", "Frigate")
            .put("exact_type", "Tech 1 Support Frigate")
            .put("overlay", "T1 Support Frigate");
    private static final JsonObject DEACON = new JsonObject()
            .put("ship", "Deacon")
            .put("class", "Frigate")
            .put("exact_type", "Logistics Frigate")
            .put("overlay", "Logistics Frigate");

    private CompRule rule;

    @Before
    public void setUp() throws Exception {
        rule = new CompRule();
    }

    @Test
    public void valid(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                ABSOLUTION,
                ABSOLUTION
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr, null)));
    }

    @Test
    public void maxTwoLogiCruisers(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                GUARDIAN,
                GUARDIAN
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Logistics Cruiser allocation reached")));
    }

    @Test
    public void maxTwoLogiCruisersIncludingWildcard(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                ETANA,
                GUARDIAN
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Logistics Cruiser allocation reached")));
    }

    @Test
    public void maxTwoLogiCruisersIncludingTwoWildcards(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                ETANA,
                RABISU
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Logistics Cruiser allocation reached")));
    }

    @Test
    public void maxFourOfANormalShipClass(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                STORK,
                SVIPUL,
                SVIPUL,
                SVIPUL
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Destroyer allocation reached")));
    }

    @Test
    public void exactlyTwoLogisticsFrigateIsOk(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                BURST,
                BURST
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                null)));
    }

    @Test
    public void maxTwoLogisticsFrigates(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                BURST,
                BURST,
                BURST
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Logistics Frigate allocation reached")));
    }

    @Test
    public void maxTwoT2LogisticsFrigates(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                DEACON,
                DEACON,
                DEACON
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Logistics Frigate allocation reached")));
    }

    @Test
    public void maxTwoMixedLogisticsFrigates(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                BURST,
                BURST,
                DEACON
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Logistics Frigate allocation reached")));
    }

    @Test
    public void ifLogiCruiserThenNoLogiFrigate(TestContext ctx) {
        JsonArray input = new JsonArray(Arrays.asList(
                GUARDIAN,
                DEACON
        ));
        rule.checkCompRules(input).onComplete(ctx.asyncAssertSuccess(arr -> assertValidComp(ctx, arr,
                "Maximum Logistics allocation reached")));
    }

    private TestContext assertValidComp(TestContext ctx, JsonArray arr, String matches) {
        return ctx.assertTrue(arr.stream().map(o -> (JsonObject) o)
                .map(o -> o.getString("validComp"))
                .allMatch(validComp -> matches == null ? validComp == null : validComp.equals(matches)));
    }

}
