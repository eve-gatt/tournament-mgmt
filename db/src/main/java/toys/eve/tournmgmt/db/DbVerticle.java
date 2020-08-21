package toys.eve.tournmgmt.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import java.util.UUID;
import java.util.stream.Collector;

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

        startPromise.complete();
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

    private Collector<JsonObject, JsonArray, JsonArray> toJsonArray() {
        return Collector.of(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }
}
