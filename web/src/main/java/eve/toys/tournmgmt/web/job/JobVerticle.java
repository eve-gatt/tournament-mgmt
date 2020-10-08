package eve.toys.tournmgmt.web.job;

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
        vertx.eventBus().consumer(JobClient.JOB_CHECK_CAPTAIN_ALLIANCE_MEMBERSHIP, this::checkCaptainAllianceMembership);
        vertx.eventBus().consumer(JobClient.JOB_CHECK_PILOTS_ALLIANCE_MEMBERSHIP, this::checkPilotsAllianceMembership);
        vertx.eventBus().consumer(JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM, this::checkPilotsOnOneTeam);
        startPromise.complete();
    }

    private void checkCaptainAllianceMembership(Message<String> msg) {
        run(msg, new CaptainAllianceMembershipValidation(dbClient, esi));
    }

    private void checkPilotsAllianceMembership(Message<String> msg) {
        LOGGER.info("Checking pilot's alliance memberships");
        dbClient.callDb(DbClient.DB_ALL_PILOTS, new JsonObject())
                .onFailure(t -> msg.fail(1, t.getMessage()));
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
                            JsonArray pilotsWithTeams = ((Message<JsonArray>) f.resultAt(1)).body();
                            JsonArray pilots = new JsonArray(pilotsWithTeams.stream()
                                    .map(o -> (JsonObject) o)
                                    .map(p -> new JsonObject()
                                            .put("tournament_uuid", p.getString("tournament_uuid"))
                                            .put("name", p.getString("pilot_name")))
                                    .collect(Collectors.toList()));
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

    private void run(Message<String> msg, Validation validation) {
        LOGGER.info("Cleaning " + validation.getName());
        dbClient.callDb(DbClient.DB_CLEAR_PROBLEMS,
                new JsonObject()
                        .put("type", validation.getProblemType().name())
                        .put("name", validation.getName()))
                .compose(v -> {
                    LOGGER.info("Starting " + validation.getName());
                    return validation.run();
                })
                .onFailure(t -> {
                    LOGGER.error(validation.getName(), t);
                    msg.fail(1, validation.getName() + " - " + t.getMessage());
                })
                .onSuccess(v -> LOGGER.info("Completed " + validation.getName()));
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
