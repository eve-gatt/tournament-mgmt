package eve.toys.tournmgmt.web.job;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.circuitbreaker.CircuitBreaker;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class JobVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobVerticle.class.getName());
    private WebClient webClient;
    private Esi esi;
    private DbClient dbClient;

    public void start(Promise<Void> startPromise) {
        LOGGER.info("Initialising jobs");
        this.webClient = WebClient.create(vertx, new WebClientOptions().setUserAgent(System.getProperty("http.agent")));
        this.esi = Esi.create(webClient, CircuitBreaker.create("esi-cb", vertx));
        this.dbClient = new DbClient(vertx.eventBus());
        vertx.eventBus().consumer(JobClient.JOB_CHECK_ALLIANCE_MEMBERSHIP, this::checkAllianceMembership);
        vertx.eventBus().consumer(JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM, this::checkPilotsOnOneTeam);
        startPromise.complete();
    }

    private void checkAllianceMembership(Message<String> msg) {
        LOGGER.info("Checking alliance memberships");
        dbClient.callDb(DbClient.DB_ALL_TEAMS, new JsonObject())
                .onFailure(t -> msg.fail(1, t.getMessage()))
                .onSuccess(result -> {
                    JsonArray body = (JsonArray) result.body();
                    CompositeFuture.all(body.stream()
                            .map(o -> (JsonObject) o)
                            .map(row -> esi.checkMembership(
                                    row.getString("uuid"),
                                    esi.lookupAlliance(row.getString("name")),
                                    esi.lookupCharacter(row.getString("captain"))))
                            .collect(Collectors.toList()))
                            .map(AppStreamHelpers::toJsonObjects)
                            .onFailure(Throwable::printStackTrace)
                            .onSuccess(teams ->
                                    teams.forEach(json -> {
                                        String uuid = json.getString("uuid");
                                        JsonArray expected = json.getJsonObject("expectedAlliance").getJsonArray("result");
                                        String error = "";
                                        if (expected != null && !expected.getInteger(0).equals(json.getInteger("actualAlliance"))) {
                                            error = json.getJsonObject("character").getString("character")
                                                    + " is not in "
                                                    + json.getJsonObject("expectedAlliance").getString("alliance");
                                        }
                                        dbClient.callDb(DbClient.DB_UPDATE_TEAM_MESSAGE,
                                                new JsonObject().put("message", error).put("uuid", uuid))
                                                .onFailure(Throwable::printStackTrace)
                                                .onSuccess(v -> {
                                                });
                                    }));
                    LOGGER.info("Checking alliance memberships completed");
                });
    }

    private void checkPilotsOnOneTeam(Message<String> msg) {
        LOGGER.info("Checking pilots are only playing for one team");
        dbClient.callDb(DbClient.DB_CLEAR_ALL_TOURNAMENT_MSGS, new JsonObject())
                .compose(v -> CompositeFuture.all(
                        dbClient.callDb(DbClient.DB_ALL_CAPTAINS, new JsonObject()),
                        dbClient.callDb(DbClient.DB_ALL_PILOTS, new JsonObject()))
                        .onFailure(t -> msg.fail(1, t.getMessage()))
                        .onSuccess(f -> {
                            JsonArray captains = ((Message<JsonArray>) f.resultAt(0)).body();
                            JsonArray pilots = ((Message<JsonArray>) f.resultAt(1)).body();
                            pilots.addAll(captains);
                            Set<JsonObject> duplicates = findDuplicates(pilots.getList());
                            CompositeFuture.all(duplicates.stream().map(problem -> dbClient.callDb(DbClient.DB_UPDATE_TOURNAMENT_MESSAGE,
                                    new JsonObject()
                                            .put("message", problem.getString("name") + " is in more than one team")
                                            .put("uuid", problem.getString("tournament_uuid"))))
                                    .collect(Collectors.toList()))
                                    .onFailure(Throwable::printStackTrace)
                                    .onSuccess(v2 -> LOGGER.info("Checking pilots are only playing for one team completed"));
                        }));
    }

    private <T> Set<T> findDuplicates(Collection<T> collection) {
        Set<T> duplicates = new LinkedHashSet<>();
        Set<T> uniques = new HashSet<>();

        for (T t : collection) {
            if (!uniques.add(t)) {
                duplicates.add(t);
            }
        }

        return duplicates;
    }

}
