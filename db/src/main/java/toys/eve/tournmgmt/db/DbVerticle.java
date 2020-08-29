package toys.eve.tournmgmt.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.StringEscapeUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class DbVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbVerticle.class.getName());
    private static final String URL = System.getenv("POSTGRES_URL");
    private static final String USER = System.getenv("POSTGRES_USER");
    private static final String PASSWORD = System.getenv("POSTGRES_PASSWORD");
    private SQLClient sqlClient;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        LOGGER.info("Initialising Flyway");

        try {
            Flyway flyway = Flyway.configure().dataSource(URL, USER, PASSWORD).load();
            if (Boolean.parseBoolean(System.getProperty("db_do_clean", "false"))) {
                LOGGER.info("Flyway cleaning");
                flyway.clean();
            }
            LOGGER.info("Flyway migration");
            flyway.migrate();
        } catch (FlywayException e) {
            startPromise.fail(e);
            return;
        }

        LOGGER.info("Successfully migrated database schema");

        JsonObject config = new JsonObject()
                .put("driver_class", "org.postgresql.Driver")
                .put("url", URL)
                .put("user", USER)
                .put("password", PASSWORD);
        sqlClient = JDBCClient.createShared(vertx, config);

        vertx.eventBus().consumer(DbClient.DB_CREATE_TOURNAMENT, this::createTournament);
        vertx.eventBus().consumer(DbClient.DB_FETCH_TOURNAMENTS, this::fetchOrganisedTournaments);
        vertx.eventBus().consumer(DbClient.DB_TOURNAMENT_BY_UUID, this::tournamentByUuid);
        vertx.eventBus().consumer(DbClient.DB_TEAM_BY_UUID, this::teamByUuid);
        vertx.eventBus().consumer(DbClient.DB_WRITE_TEAM_TSV, this::writeTeamTsv);
        vertx.eventBus().consumer(DbClient.DB_TEAMS_BY_TOURNAMENT, this::teamsByTournament);
        vertx.eventBus().consumer(DbClient.DB_DELETE_TEAM_BY_UUID, this::deleteTeamByUuid);

        startPromise.complete();
    }

    private void createTournament(Message<JsonObject> msg) {
        JsonObject input = msg.body();
        sqlClient.updateWithParams("insert into tournament(uuid, name, practice_on_td, play_on_td, created_by) " +
                        "values ('" + UUID.randomUUID().toString() + "', ?, ?, ?, ?)",
                new JsonArray()
                        .add(input.getString("name").trim())
                        .add(input.getBoolean("practiceOnTd"))
                        .add(input.getBoolean("playOnTd"))
                        .add(input.getString("createdBy")),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(input);
                    }
                });
    }

    private void fetchOrganisedTournaments(Message<JsonObject> msg) {
        String organiser = msg.body().getString("organiser");
        sqlClient.query(
                "select uuid, name, created_by, practice_on_td, play_on_td, teams_locked " +
                        "from tournament",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(ar.result().getRows().stream()
                            .collect(toJsonArray()));
                }
        );
    }

    private void tournamentByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select name, uuid " +
                        "from tournament " +
                        "where uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    if (ar.result().getRows().size() != 1) {
                        msg.fail(2, "No tournament found for uuid: " + uuid);
                        return;
                    }
                    msg.reply(ar.result().getRows().get(0));
                });
    }

    private void teamByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.query("select name, captain, uuid " +
                        "from team " +
                        "where uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    if (ar.result().getRows().size() != 1) {
                        msg.fail(2, "No team found for uuid: " + uuid);
                        return;
                    }
                    msg.reply(ar.result().getRows().get(0));
                });
    }

    private void writeTeamTsv(Message<JsonObject> msg) {
        String tsv = msg.body().getString("tsv");
        String createdBy = msg.body().getString("createdBy");
        String uuid = msg.body().getString("uuid");
        String[] rows = tsv.trim().split("[\\r\\n]+");
        String values = Arrays.stream(rows)
                .map(String::trim)
                .filter(row -> row.split("[\\t,]+").length == 2)
                .map(row -> {
                    String[] cols = row.split("[\\t,]");
                    try {
                        String alliance = StringEscapeUtils.escapeJavaScript(cols[0].trim());
                        String character = StringEscapeUtils.escapeJavaScript(cols[1].trim());
                        return "(" +
                                "'" + UUID.randomUUID().toString() + "'" +
                                ", " +
                                "'" + uuid + "'" +
                                ", " +
                                "'" + alliance + "'" +
                                ", " +
                                "'" + character + "'" +
                                ", " +
                                "'" + createdBy + "'" +
                                ")";
                    } catch (Exception e) {
                        e.printStackTrace();
                        msg.fail(2, e.getMessage());
                        return null;
                    }
                })
                .collect(Collectors.joining(","));
        sqlClient.update("insert into team (uuid, tournament_uuid, name, captain, created_by) " +
                        "values " + values,
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(null);
                });
    }

    private void teamsByTournament(Message<JsonObject> msg) {
        String uuid = msg.body().getString("uuid");
        sqlClient.query("select name, uuid, captain " +
                        "from team " +
                        "where tournament_uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(new JsonArray(ar.result().getRows()));
                });
    }

    private void deleteTeamByUuid(Message<String> msg) {
        String uuid = msg.body();
        sqlClient.update("delete from team where uuid = '" + uuid + "'",
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                        return;
                    }
                    msg.reply(null);
                });
    }

    private Collector<JsonObject, JsonArray, JsonArray> toJsonArray() {
        return Collector.of(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }
}
