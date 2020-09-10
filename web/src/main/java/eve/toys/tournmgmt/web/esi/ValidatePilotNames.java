package eve.toys.tournmgmt.web.esi;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValidatePilotNames {

    private final WebClient webClient;
    private final Esi esi;

    public ValidatePilotNames(WebClient webClient, Esi esi) {
        this.webClient = webClient;
        this.esi = esi;
    }

    public void validate(TSV tsv, Handler<AsyncResult<String>> replyHandler) {
        CompositeFuture.all(tsv.stream()
                .map(this::lookupCharacter)
                .collect(Collectors.toList()))
                .map(AppStreamHelpers::toJsonObjects)
                .map(this::filterForInvalidNames)
                .map(this::joinErrors)
                .onSuccess(msg -> replyHandler.handle(Future.succeededFuture(msg)))
                .onFailure(t -> replyHandler.handle(Future.failedFuture(t)));
    }

    private Future<JsonObject> lookupCharacter(TSV.Row row) {
        return esi.lookupCharacter(webClient, row.getCol(0));
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
