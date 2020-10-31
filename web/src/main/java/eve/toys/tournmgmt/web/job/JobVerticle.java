package eve.toys.tournmgmt.web.job;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.circuitbreaker.CircuitBreaker;
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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JobVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobVerticle.class.getName());
    private static final String WEBHOOK = System.getenv("DISCORD_WEBHOOK");
    private static final boolean IS_DEV = Boolean.parseBoolean(System.getProperty("isDev", "false"));
    private WebClient webClient;
    private Esi esi;
    private DbClient dbClient;

    public void start(Promise<Void> startPromise) {

        Objects.requireNonNull(IS_DEV);
        Objects.requireNonNull(WEBHOOK);

        LOGGER.info("Initialising jobs");
        this.webClient = WebClient.create(vertx, new WebClientOptions().setUserAgent(System.getProperty("http.agent")));
        this.esi = Esi.create(webClient, CircuitBreaker.create("esi-cb", vertx), vertx);
        this.dbClient = new DbClient(vertx.eventBus());
        vertx.eventBus().consumer(JobClient.JOB_CHECK_CAPTAIN_ALLIANCE_MEMBERSHIP, this::checkCaptainAllianceMembership);
        vertx.eventBus().consumer(JobClient.JOB_CHECK_PILOTS_ALLIANCE_MEMBERSHIP, this::checkPilotsAllianceMembership);
        vertx.eventBus().consumer(JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM, this::checkPilotsOnOneTeam);
        vertx.eventBus().consumer(JobClient.JOB_PING_DISCORD_RENAME_REQUESTS, this::pingDiscordRenameRequests);

        if (!IS_DEV) {
            delayNMinutesThenEveryMHours(10, 1, JobClient.JOB_CHECK_CAPTAIN_ALLIANCE_MEMBERSHIP);
            delayNMinutesThenEveryMHours(20, 1, JobClient.JOB_CHECK_PILOTS_ALLIANCE_MEMBERSHIP);
            delayNMinutesThenEveryMHours(30, 1, JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM);
            delayNMinutesThenEveryMHours(40, 6, JobClient.JOB_PING_DISCORD_RENAME_REQUESTS);
        } else {
            LOGGER.info("Not running batch job due to dev mode");
        }

        startPromise.complete();
    }

    private void pingDiscordRenameRequests(Message<String> msg) {
        dbClient.callDb(DbClient.DB_PILOT_NAMES_IN_USE, new JsonObject())
                .onFailure(t -> msg.fail(1, t.getMessage()))
                .onSuccess(msg2 -> {
                    JsonArray outstanding = new JsonArray(((JsonArray) msg2.body()).stream()
                            .map(o -> (JsonObject) o)
                            .filter(r -> !r.getBoolean("resolved"))
                            .collect(Collectors.toList()));
                    if (outstanding.size() > 0) {
                        String content = "There are outstanding requests for pilot name changes on Thunderdome:\n\n" +
                                         "```" +
                                         outstanding.stream().map(o -> (JsonObject) o).map(r -> r.getString("name") + " at " + r.getString("reported_at")).collect(Collectors.joining("\n")) +
                                         "```" +
                                         "\nhttps://tournmgmt.eve.toys/auth/ccp/home";
                        JsonObject payload = new JsonObject()
                                .put("content", content);
                        webClient.postAbs(WEBHOOK)
                                .sendJson(payload, ar -> {
                                    if (ar.failed()) {
                                        msg.fail(2, ar.cause().getMessage());
                                    } else {
                                        System.out.println("Successfully pinged discord");
                                    }
                                });
                    }
                });
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

    private void delayNMinutesThenEveryMHours(int minutes, int hours, String job) {
        vertx.setTimer(TimeUnit.MINUTES.toMillis(minutes),
                id -> {
                    vertx.eventBus().send(job, null);
                    vertx.setPeriodic(TimeUnit.HOURS.toMillis(hours),
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
