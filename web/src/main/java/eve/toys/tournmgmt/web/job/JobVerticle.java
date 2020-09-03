package eve.toys.tournmgmt.web.job;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import toys.eve.tournmgmt.db.DbClient;

import java.util.List;
import java.util.stream.Collectors;

public class JobVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobVerticle.class.getName());
    private WebClient webClient;

    public void start(Promise<Void> startPromise) throws Exception {
        LOGGER.info("Initialising jobs");
        this.webClient = WebClient.create(vertx, new WebClientOptions().setUserAgent(System.getProperty("http.agent")));
        vertx.eventBus().consumer(JobClient.JOB_CHECK_ALLIANCE_MEMBERSHIP, this::checkAllianceMembership);
        startPromise.complete();
    }

    private void checkAllianceMembership(Message<JsonObject> msg) {
        LOGGER.info("Checking alliance memberships");
        // check team captains are in alliance named after team
        vertx.eventBus().request(DbClient.DB_ALL_TEAMS, new JsonObject(), ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                msg.fail(1, ar.cause().getMessage());
                return;
            }
            JsonArray body = (JsonArray) ar.result().body();
            List<Future> checks = body.stream()
                    .map(o -> (JsonObject) o)
                    .map(row -> Esi.checkMembership(webClient,
                            row.getString("uuid"),
                            Esi.lookupAlliance(webClient, row.getString("name")),
                            Esi.checkCharacter(webClient, row.getString("captain"))))
                    .collect(Collectors.toList());
            CompositeFuture.all(checks).onFailure(Throwable::printStackTrace)
                    .onSuccess(f -> {
                        f.list().stream()
                                .map(o -> (JsonObject) o)
                                .forEach(result -> {
                                    String uuid = result.getString("uuid");
                                    JsonArray expected = result.getJsonObject("expectedAlliance").getJsonArray("result");
                                    if (expected != null && !expected.getInteger(0).equals(result.getInteger("actualAlliance"))) {
                                        String error = result.getJsonObject("character").getString("name")
                                                + " is not in "
                                                + result.getJsonObject("expectedAlliance").getString("name");
                                        vertx.eventBus().send(DbClient.DB_UPDATE_TEAM_MESSAGE,
                                                new JsonObject()
                                                        .put("message", error)
                                                        .put("uuid", uuid));
                                    }
                                });

                    });
        });

    }
}
