package eve.toys.tournmgmt.web.esi;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.client.WebClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Esi {

    public static final String ESI_BASE = "https://esi.evetech.net/latest";
    private final ETagCache etagCache;

    private Esi(WebClient webClient, CircuitBreaker circuitBreaker) {
        this.etagCache = new ETagCache(webClient, circuitBreaker);
    }

    public static Esi create(WebClient webClient, CircuitBreaker circuitBreaker) {
        return new Esi(webClient, circuitBreaker);
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

    private static String encode(String string) {
        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return string;
        }
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

    public Future<JsonObject> fetchCharacterSkills(AccessToken user, int characterId) {
        return Future.future(promise -> {
            AppRBAC.refreshIfNeeded(user, v -> {
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
                            if (alliance.getJsonArray("result") != null && character.getJsonArray("result") != null) {
                                String url = "/characters/" + character.getJsonArray("result").getInteger(0);
                                etagCache.callApi(ESI_BASE + url)
                                        .onFailure(promise::fail)
                                        .onSuccess(result -> {
                                            Integer allianceId = result.body().toJsonObject().getInteger("alliance_id");
                                            out.put("actualAlliance", allianceId);
                                            promise.complete(out);
                                        });
                            } else {
                                promise.complete(out);
                            }
                        })
                        .onFailure(Throwable::printStackTrace)
        );
    }

    public Future<JsonObject> lookupCharacter(String character) {
        return lookupByType(character, "character");
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

    public Future<JsonObject> fetchCharacter(int characterId) {
        return Future.future(promise -> {
            String url = "/characters/" + characterId;
            etagCache.callApi(ESI_BASE + url)
                    .onFailure(promise::fail)
                    .onSuccess(result -> promise.complete(result.bodyAsJsonObject()));
        });
    }
}
