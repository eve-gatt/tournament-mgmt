package eve.toys.tournmgmt.web.job;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import toys.eve.tournmgmt.db.DbClient;

import java.util.concurrent.TimeUnit;
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

        delayThenEveryHour(10, JobClient.JOB_CHECK_CAPTAIN_ALLIANCE_MEMBERSHIP);
        delayThenEveryHour(20, JobClient.JOB_CHECK_PILOTS_ALLIANCE_MEMBERSHIP);
        delayThenEveryHour(30, JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM);

        startPromise.complete();
    }

    private void checkCaptainAllianceMembership(Message<String> msg) {
        run(msg, new CaptainAllianceMembershipValidation(dbClient, esi));
    }

    private void checkPilotsAllianceMembership(Message<String> msg) {
        run(msg, new PilotAllianceMembershipValidation(dbClient, esi));
    }

    private void checkPilotsOnOneTeam(Message<String> msg) {
        run(msg, new PilotsCanOnlyBeOnOneTeamValidation(dbClient));
    }

    private void delayThenEveryHour(int minutes, String job) {
        vertx.setTimer(TimeUnit.MINUTES.toMillis(minutes),
                id -> {
                    vertx.eventBus().send(job, null);
                    vertx.setPeriodic(TimeUnit.HOURS.toMillis(1),
                            id2 -> vertx.eventBus().send(job, null));
                });
    }

    private void run(Message<String> msg, Validation validation) {
        LOGGER.info("Running " + validation.getName());
        validation.run()
                .compose(records -> {
                    LOGGER.info("Cleaning " + validation.getName());
                    return doClean(validation).map(v -> records);
                })
                .compose(records -> {
                    LOGGER.info("Writing problems to db for " + validation.getName());
                    return CompositeFuture.all(records.stream()
                            .map(record -> dbClient.callDb(DbClient.DB_ADD_PROBLEM, record))
                            .collect(Collectors.toList()));
                })
                .onSuccess(v -> LOGGER.info("Completed " + validation.getName()))
                .onFailure(t -> {
                    LOGGER.error(validation.getName(), t);
                    msg.fail(1, validation.getName() + " - " + t.getMessage());
                });
    }

    private Future<Message<Object>> doClean(Validation validation) {
        return dbClient.callDb(DbClient.DB_CLEAR_PROBLEMS,
                new JsonObject()
                        .put("type", validation.getProblemType().name())
                        .put("name", validation.getName()));
    }
}
