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
import toys.eve.tournmgmt.db.HistoricalClient.Call;

import java.util.Objects;

public class HistoricalDbVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoricalDbVerticle.class.getName());
    private static final String URL = System.getenv("HISTORICAL_URL");
    private static final String USER = System.getenv("HISTORICAL_USER");
    private static final String PASSWORD = System.getenv("HISTORICAL_PASSWORD");
    private SQLClient sqlClient;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Objects.requireNonNull(URL, "Please supply HISTORICAL_URL");
        Objects.requireNonNull(USER, "Please supply HISTORICAL_USER");
        Objects.requireNonNull(PASSWORD, "Please supply HISTORICAL_PASSWORD");

        JsonObject config = new JsonObject()
                .put("driver_class", "org.mariadb.jdbc.Driver")
                .put("url", URL)
                .put("user", USER)
                .put("password", PASSWORD);
        sqlClient = JDBCClient.createShared(vertx, config, "historical");

        vertx.eventBus().consumer(Call.HISTORICAL_FETCH_MATCHES_BY_TEAM.name(), this::fetchMatchesByTeam);

        startPromise.complete();
    }

    private void fetchMatchesByTeam(Message<String> msg) {
        String teamName = msg.body();
        sqlClient.queryWithParams("select * " +
                                  "from matches " +
                                  "where RedTeam = ? " +
                                  "   or BlueTeam = ? " +
                                  "order by Tournament, MatchNo",
                new JsonArray().add(teamName).add(teamName),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        msg.fail(1, ar.cause().getMessage());
                    } else {
                        msg.reply(new JsonArray(ar.result().getRows()));
                    }
                });
    }

}
