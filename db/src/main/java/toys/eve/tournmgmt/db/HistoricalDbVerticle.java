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
        vertx.eventBus().consumer(Call.HISTORICAL_WIN_LOSS_BY_TOURNAMENT_AND_SHIP.name(), this::winlossByTournamentAndShip);

        startPromise.complete();
    }

    private void fetchMatchesByTeam(Message<String> msg) {
        String teamName = msg.body();
        sqlClient.queryWithParams("select * " +
                                  "from matches " +
                                  "where RedTeam = ? " +
                                  "   or BlueTeam = ? " +
                                  "order by Tournament, MatchNo, SeriesNo",
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

    private void winlossByTournamentAndShip(Message<Void> msg) {
        sqlClient.query("select Tournament,\n" +
                        "       Ship,\n" +
                        "       Class,\n" +
                        "       winloss,\n" +
                        "       sum(count) as fielded,\n" +
                        "       sum(used)  as used\n" +
                        "from (\n" +
                        "         select m.Tournament,\n" +
                        "                p.Team,\n" +
                        "                p.Ship,\n" +
                        "                s.Class,\n" +
                        "                t.Tag,\n" +
                        "                st.SuperTag,\n" +
                        "                case when p.Team = m.Victor then 'W' else 'L' end as winloss,\n" +
                        "                count(*)                                          as count,\n" +
                        "                least(count(*), 1)                                as used\n" +
                        "         from players p\n" +
                        "                  inner join matches m\n" +
                        "                             on p.Tournament = m.Tournament and\n" +
                        "                                p.MatchNo = m.MatchNo and\n" +
                        "                                p.SeriesNo = m.SeriesNo\n" +
                        "                  inner join ships s on p.Ship = s.Ship\n" +
                        "                  inner join tags t on s.Ship = t.Ship\n" +
                        "                  inner join supertags st on t.Tag = st.Tag\n" +
                        "         group by m.Tournament,\n" +
                        "                  m.MatchNo,\n" +
                        "                  m.SeriesNo,\n" +
                        "                  p.Team,\n" +
                        "                  p.Ship,\n" +
                        "                  s.Class,\n" +
                        "                  winloss\n" +
                        "     ) as source\n" +
                        "group by Tournament, Ship, Class, winloss\n",
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
