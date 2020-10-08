package eve.toys.tournmgmt.web.job;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PilotsCanOnlyBeOnOneTeamValidation implements Validation {
    private final DbClient dbClient;

    public PilotsCanOnlyBeOnOneTeamValidation(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public Future<?> run() {
        return CompositeFuture.all(
                dbClient.callDb(DbClient.DB_ALL_CAPTAINS, new JsonObject()),
                dbClient.callDb(DbClient.DB_ALL_PILOTS, new JsonObject()))
                .map(f -> {
                    JsonArray captains = ((Message<JsonArray>) f.resultAt(0)).body();
                    JsonArray pilots = ((Message<JsonArray>) f.resultAt(1)).body();
                    pilots.addAll(captains);
                    return findDuplicates(pilots.getList());
                })
                .compose(duplicates ->
                        CompositeFuture.all(((Set<JsonObject>) duplicates).stream()
                                .map(problem -> dbClient.callDb(DbClient.DB_ADD_PROBLEM,
                                        new JsonObject()
                                                .put("type", getProblemType().name())
                                                .put("tournamentUuid", problem.getString("tournament_uuid"))
                                                .put("validationIdentifier", getName())
                                                .put("referencedEntity", problem.getString("tournament_uuid"))
                                                .put("message", problem.getString("name") + " is in more than one team")))
                                .collect(Collectors.toList())));
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

    @Override
    public ProblemType getProblemType() {
        return ProblemType.tournament;
    }

    @Override
    public String getName() {
        return "pilots-can-only-be-one-one-team";
    }
}
