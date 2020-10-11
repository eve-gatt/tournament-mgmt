package eve.toys.tournmgmt.web.match;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Arrays;
import java.util.UUID;

@RunWith(VertxUnitRunner.class)
public class RefToolInputTest {
    private static final String VALID1 = "Amelia Duskspace\tJackdaw\tRakapas V - Home Guard Assembly Plant\t6\t\n" +
            "Auraus Porcaleus\tVagabond\tRakapas V - Home Guard Assembly Plant\t11\t\n" +
            "Casper24\tCambion\tRakapas V - Home Guard Assembly Plant\t4\t\n" +
            "dexter xio\tSleipnir\tRakapas V - Home Guard Assembly Plant\t18\t\n" +
            "Faith Evingod\tCambion\tRakapas V - Home Guard Assembly Plant\t4\t\n" +
            "Jane Domar\tSleipnir\tRakapas V - Home Guard Assembly Plant\t18\tToo many of ship type Sleipnir\n" +
            "Lucas Quaan\tScimitar\tRakapas V - Home Guard Assembly Plant\t17\t\n" +
            "Mark Bridges\tJackdaw\tRakapas V - Home Guard Assembly Plant\t6\tToo many of ship type Jackdaw - Team over points\n" +
            "Neuromancer X\tJackdaw\tRakapas V - Home Guard Assembly Plant\t6\tToo many of ship type Jackdaw - Team over points\n" +
            "Soldier Forrester\tSleipnir\tRakapas V - Home Guard Assembly Plant\t18\t";
    private static final String TOURNAMENT_UUID = UUID.randomUUID().toString();

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();
    private Vertx vertx;
    private DbClient dbClient;

    @Before
    public void setUp() throws Exception {
        vertx = rule.vertx();
        dbClient = new DbClient(vertx.eventBus());
    }

    @After
    public void tearDown() throws Exception {
        vertx.close();
    }

    @Test
    public void teamMembersAreAllOnTheSameTeam(TestContext context) {
        Async async = context.async();
        vertx.eventBus().consumer(DbClient.DB_TEAMS_FOR_PILOT_LIST, msg -> {
            msg.reply(new JsonArray(Arrays.asList(
                    new JsonObject().put("pilot_name", "John").put("team_name", "Team1"),
                    new JsonObject().put("pilot_name", "Jack").put("team_name", "Team1")
            )));
            async.complete();
        });

        new RefToolInput(dbClient).validateTeamMembership(TOURNAMENT_UUID, "John\nJack\n")
                .onComplete(context.asyncAssertSuccess(response ->
                        context.assertEquals("All pilots are on the same team", response)));
    }

    @Test
    public void splitTeamMembers(TestContext context) {
        Async async = context.async();
        vertx.eventBus().consumer(DbClient.DB_TEAMS_FOR_PILOT_LIST, msg -> {
            msg.reply(new JsonArray(Arrays.asList(
                    new JsonObject().put("pilot_name", "John").put("team_name", "Team1"),
                    new JsonObject().put("pilot_name", "Jack").put("team_name", "Team2")
            )));
            async.complete();
        });

        new RefToolInput(dbClient).validateTeamMembership(TOURNAMENT_UUID, "John\nJack\n")
                .onComplete(context.asyncAssertSuccess(response ->
                        context.assertTrue(response.contains("Pilots come from more than one team"))));
    }

    @Test
    public void teamMembersThatAreNotOnAnyTeam(TestContext context) {
        Async async = context.async();
        vertx.eventBus().consumer(DbClient.DB_TEAMS_FOR_PILOT_LIST, msg -> {
            msg.reply(new JsonArray(Arrays.asList(
                    new JsonObject().put("pilot_name", "John").put("team_name", "Team1")
            )));
            async.complete();
        });

        new RefToolInput(dbClient).validateTeamMembership(TOURNAMENT_UUID, "John\nJack\n")
                .onComplete(context.asyncAssertSuccess(response ->
                        context.assertTrue(response.contains("Some pilots aren't on any team in this tournament"))));
    }
}
