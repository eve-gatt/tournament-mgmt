package eve.toys.tournmgmt.web.job;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import toys.eve.tournmgmt.db.DbClient;

import java.util.stream.Collectors;

public class JobVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobVerticle.class.getName());
    private WebClient webClient;
    private Esi esi;
    private DbClient dbClient;

    public void start(Promise<Void> startPromise) throws Exception {
        LOGGER.info("Initialising jobs");
        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setUserAgent(System.getProperty("http.agent")));
        this.esi = Esi.create();
        this.dbClient = new DbClient(vertx.eventBus());
        vertx.eventBus().consumer(JobClient.JOB_CHECK_ALLIANCE_MEMBERSHIP, this::checkAllianceMembership);
        startPromise.complete();
    }

    private void checkAllianceMembership(Message<JsonObject> msg) {
        LOGGER.info("Checking alliance memberships");
        dbClient.callDb(DbClient.DB_ALL_TEAMS, new JsonObject())
                .onFailure(t -> msg.fail(1, t.getMessage()))
                .onSuccess(result -> {
                    JsonArray body = (JsonArray) result.body();
                    CompositeFuture.all(body.stream()
                            .map(o -> (JsonObject) o)
                            .map(row -> esi.checkMembership(webClient,
                                    row.getString("uuid"),
                                    esi.lookupAlliance(webClient, row.getString("name")),
                                    esi.lookupCharacter(webClient, row.getString("captain"))))
                            .collect(Collectors.toList()))
                            .onFailure(Throwable::printStackTrace)
                            .map(AppStreamHelpers::toJsonObjects)
                            .onSuccess(results ->
                                    results.forEach(json -> {
                                        String uuid = json.getString("uuid");
                                        JsonArray expected = json.getJsonObject("expectedAlliance").getJsonArray("result");
                                        String error = "";
                                        if (expected != null && !expected.getInteger(0).equals(json.getInteger("actualAlliance"))) {
                                            error = json.getJsonObject("character").getString("character")
                                                    + " is not in "
                                                    + json.getJsonObject("expectedAlliance").getString("alliance");
                                        }
                                        vertx.eventBus().send(DbClient.DB_UPDATE_TEAM_MESSAGE,
                                                new JsonObject().put("message", error).put("uuid", uuid));
                                    }));
                    LOGGER.info("Checking alliance memberships completed");
                });
    }
}
