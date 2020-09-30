package eve.toys.tournmgmt.web.esi;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import eve.toys.tournmgmt.web.tsv.TSV;
import eve.toys.tournmgmt.web.tsv.TSVException;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValidatePilotNames {

    private final Esi esi;

    public ValidatePilotNames(Esi esi) {
        this.esi = esi;
    }

    public void validate(TSV tsv, String captain, Handler<AsyncResult<String>> replyHandler) {
        boolean tryingToImportCaptain = tsv.stream().anyMatch(row -> {
            try {
                return row.getCol(0).equalsIgnoreCase(captain);
            } catch (TSVException e) {
                e.printStackTrace();
                return false;
            }
        });
        CompositeFuture.all(tsv.stream()
                .map(this::lookupCharacter)
                .collect(Collectors.toList()))
                .map(AppStreamHelpers::toJsonObjects)
                .map(this::filterForInvalidNames)
                .map(this::joinErrors)
                .map(msg -> {
                    if (tryingToImportCaptain) {
                        return captain + " is captain of this team\n" + msg;
                    } else {
                        return msg;
                    }
                })
                .onSuccess(msg -> replyHandler.handle(Future.succeededFuture(msg)))
                .onFailure(t -> replyHandler.handle(Future.failedFuture(t)));
    }

    private Future<JsonObject> lookupCharacter(TSV.Row row) {
        try {
            String name = row.getCol(0);
            return esi.lookupCharacter(name);
        } catch (TSVException e) {
            return Future.failedFuture(e);
        }
    }

    private Stream<String> filterForInvalidNames(Stream<JsonObject> results) {
        return results
                .filter(r -> r.getJsonArray("result") == null)
                .map(r -> r.getString("character") + " is not a valid character name");
    }

    private String joinErrors(Stream<String> errors) {
        return errors.collect(Collectors.joining("\n")).trim();
    }
}
