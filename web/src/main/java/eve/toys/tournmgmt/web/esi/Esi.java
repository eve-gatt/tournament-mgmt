package eve.toys.tournmgmt.web.esi;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.client.WebClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.stream.Collectors;

public class Esi {

    public static final String ESI_BASE = "https://esi.evetech.net/latest";
    private final ETagCache etagCache;

    private Esi(WebClient webClient, CircuitBreaker circuitBreaker, Vertx vertx) {
        this.etagCache = new ETagCache(webClient, circuitBreaker, vertx);
    }

    public static Esi create(WebClient webClient, CircuitBreaker circuitBreaker, Vertx vertx) {
        return new Esi(webClient, circuitBreaker, vertx);
    }

    private static String encode(String string) {
        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return string;
        }
    }

    public Future<JsonObject> lookupShip(String shipName) {
        return lookupByType(shipName, "inventory_type");
    }

    private Future<JsonObject> lookupByType(String name, String category) {
        return Future.future(promise -> {
            String url = "/search/?categories=" + category + "&strict=true&search=" + encode(name);
            etagCache.callApi(ESI_BASE + url)
                    .onFailure(promise::fail)
                    .onSuccess(result -> {
                        JsonObject json = result.body().toJsonObject();
                        promise.complete(new JsonObject()
                                .put("category", category)
                                .put(category, name)
                                .put("result", json.getJsonArray(category)));
                    });
        });
    }

    public Future<JsonObject> fetchType(Integer typeId) {
        return Future.future(promise -> {
            String url = "/universe/types/" + typeId;
            etagCache.callApi(ESI_BASE + url)
                    .onFailure(promise::fail)
                    .onSuccess(result -> {
                        JsonObject json = result.body().toJsonObject();
                        promise.complete(new JsonObject().put("result", json));
                    });
        });
    }

    public Future<JsonObject> fetchGroup(Integer groupId) {
        return Future.future(promise -> {
            String url = "/universe/groups/" + groupId;
            etagCache.callApi(ESI_BASE + url)
                    .onFailure(promise::fail)
                    .onSuccess(result -> {
                        JsonObject json = result.body().toJsonObject();
                        promise.complete(new JsonObject().put("result", json));
                    });
        });
    }

    public Future<JsonObject> fetchCharacterSkills(AccessToken user, int characterId) {
        return Future.future(promise -> {
            String url = "/characters/" + characterId + "/skills/";
            user.fetch(ESI_BASE + url, ar -> {
                if (ar.failed()) {
                    promise.fail(ar.cause());
                    return;
                }
                if (ar.result().statusCode() != 200) {
                    promise.fail(ar.result().statusCode()
                                 + "\n" + url);
                    return;
                }
                promise.complete(ar.result().jsonObject());
            });
        });
    }

    public Future<JsonObject> checkMembership(String teamUuid,
                                              String tournamentUuid,
                                              Future<JsonObject> checkAlliance,
                                              Future<JsonObject> checkCharacter) {
        return Future.future(promise ->
                CompositeFuture.all(checkAlliance, checkCharacter)
                        .onSuccess(f -> {
                            JsonObject alliance = f.resultAt(0);
                            JsonObject character = f.resultAt(1);

                            JsonObject out = new JsonObject()
                                    .put("uuid", teamUuid)
                                    .put("tournamentUuid", tournamentUuid)
                                    .put("expectedAlliance", alliance)
                                    .put("character", character);
                            if (alliance.getJsonArray("result") != null) {
                                Integer allianceId = character.getInteger("alliance_id");
                                out.put("actualAlliance", allianceId);
                                promise.complete(out);
                            } else {
                                promise.complete(out);
                            }
                        })
                        .onFailure(throwable -> {
                            System.err.println("failed esi.checkMembership()");
                            throwable.printStackTrace();
                        })
        );
    }

    public Future<JsonObject> lookupCharacter(String name) {
        return lookupByType(name, "character");
    }

    public Future<JsonObject> fetchExactMatchCharacter(String name) {
        return lookupByType(name, "character")
                .map(output -> output.getJsonArray("result"))
                .compose(arr -> {
                    if (arr.size() == 0) {
                        return Future.failedFuture("could not find character: " + name);
                    } else if (arr.size() == 1) {
                        return fetchCharacter(arr.getInteger(0));
                    } else {
                        return CompositeFuture.all(arr.stream()
                                .map(o -> (Integer) o)
                                .map(this::fetchCharacter)
                                .collect(Collectors.toList()))
                                .map(AppStreamHelpers::compositeFutureToJsonObjects)
                                .map(matchings -> matchings
                                        .filter(m -> m.getString("name").equals(name))
                                        .findFirst()
                                        .orElse(new JsonObject().put("error", "no exact match for " + name)));
                    }
                });
    }

    public Future<JsonObject> lookupAlliance(String alliance) {
        return lookupByType(alliance, "alliance")
                .compose(json -> {
                    if (json.getJsonArray("result") == null) {
                        return Future.succeededFuture(json);
                    } else {
                        return Future.future(promise -> fetchAlliance(json.getJsonArray("result").getInteger(0))
                                .onFailure(promise::fail)
                                .onSuccess(lookup -> {
                                    json.put("lookup", lookup);
                                    promise.complete(json);
                                }));
                    }
                });
    }

    public Future<JsonObject> fetchAlliance(int allianceId) {
        return Future.future(promise -> {
            String url = "/alliances/" + allianceId;
            etagCache.callApi(ESI_BASE + url)
                    .onFailure(promise::fail)
                    .onSuccess(result -> promise.complete(result.bodyAsJsonObject()));
        });
    }

    private Future<JsonObject> fetchCharacter(int characterId) {
        return Future.future(promise -> {
            String url = "/characters/" + characterId;
            etagCache.callApi(ESI_BASE + url)
                    .onFailure(promise::fail)
                    .onSuccess(result -> promise.complete(result.bodyAsJsonObject()));
        });
    }

}
