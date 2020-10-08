package eve.toys.tournmgmt.web.job;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import toys.eve.tournmgmt.db.DbClient;

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

    private void checkPilotsOnOneTeam(Message<String> msg) {
        run(msg, new PilotsCanOnlyBeOnOneTeamValidation(dbClient));
    }

    private void checkPilotsAllianceMembership(Message<String> msg) {
        run(msg, new PilotAllianceMembershipValidation(dbClient, esi));
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

}
