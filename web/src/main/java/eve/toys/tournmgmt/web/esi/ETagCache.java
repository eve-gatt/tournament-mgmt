package eve.toys.tournmgmt.web.esi;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class ETagCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ETagCache.class.getName());

    private final WebClient esi;
    private final CircuitBreaker cb;

    private Map<String, ETagAndResult> etags = new HashMap<>();
    private int etagCacheHit;
    private int cacheMiss;
    private int cacheTotal;
    private int expiryCacheHit;
    private Queue<Tuple2<Promise<HttpResponse<Buffer>>, String>> requests = new LinkedBlockingQueue<>();
    private Set<Tuple2<Promise<HttpResponse<Buffer>>, String>> inflight = new HashSet<>();

    public ETagCache(WebClient esi, CircuitBreaker cb, Vertx vertx) {
        this.esi = esi;
        this.cb = cb;
        vertx.setPeriodic(100L, id -> processQueue());
    }

    private void processQueue() {
        while (requests.size() > 0 && inflight.size() < 10) {
            Tuple2<Promise<HttpResponse<Buffer>>, String> msg = requests.poll();
            if (msg != null) doWork(msg);
        }
    }

    public Future<HttpResponse<Buffer>> callApi(String url) {
        Promise<HttpResponse<Buffer>> promise = Promise.promise();
        requests.add(Tuple.of(promise, url));
        return promise.future();
    }

    public Future<HttpResponse<Buffer>> doWork(Tuple2<Promise<HttpResponse<Buffer>>, String> msg) {

        inflight.add(msg);
        LOGGER.info("inflight : " + inflight.size() + ", queued: " + requests.size());
        Promise<HttpResponse<Buffer>> promise = msg._1();
        String url = msg._2();

        cacheTotal++;

        ETagAndResult cachedResult = etags.getOrDefault(url, ETagAndResult.NONE);

        HttpRequest<Buffer> rq = esi.getAbs(url);
        if (cachedResult != ETagAndResult.NONE) {
            if (ZonedDateTime.now().isBefore(DateTimeFormatter.RFC_1123_DATE_TIME.parse(cachedResult.expiry, ZonedDateTime::from))) {
                expiryCacheHit++;
                promise.complete(cachedResult.response);
                inflight.remove(msg);
                return promise.future();
            } else {
                rq.putHeader("If-None-Match", cachedResult.eTag);
            }
        }
        cb.executeAndReport(promise, p -> {

            if (cacheTotal % 100 == 1) {
                LOGGER.info(String.format("ExpiryHit:ETagHit:Miss:Total:CacheSize - %d:%d:%d:%d:%d", expiryCacheHit, etagCacheHit, cacheMiss, cacheTotal, etags.size()));
            }

            rq.send(res -> {
                if (res.failed()) {
                    p.fail(res.cause());
                    return;
                }

                if (res.result().statusCode() >= 500 && res.result().statusCode() < 600) {
                    LOGGER.error(res.result().headers());
                    inflight.remove(msg);
                    p.fail(res.result().statusCode() + " " + res.result().statusMessage());
                    return;
                }

                String expiry = res.result().getHeader("expires");
                if (res.result().statusCode() == 304) {
                    etagCacheHit++;
                    cachedResult.updateExpiry(expiry);
                    inflight.remove(msg);
                    p.complete(cachedResult.response);
                } else {
                    cacheMiss++;
                    String etag = res.result().getHeader("Etag");
                    if (etag != null && etag.length() > 3) {
                        etags.put(url, new ETagAndResult(etag, res.result(), expiry));
                    }
                    inflight.remove(msg);
                    p.complete(res.result());
                }

            });
        });

        return promise.future();
    }

    private static class ETagAndResult {
        private static final ETagAndResult NONE = new ETagAndResult("none", null, null);
        private final String eTag;
        private final HttpResponse<Buffer> response;
        private String expiry;

        public ETagAndResult(String eTag, HttpResponse<Buffer> response, String expiry) {
            this.eTag = eTag;
            this.response = response;
            this.expiry = expiry;
        }

        public void updateExpiry(String expiry) {
            this.expiry = expiry;
        }
    }
}
