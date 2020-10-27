(function () {

    var eb = new EventBus("/ws");
    eb.enableReconnect(true);

    eb.onclose = function () {
    }

    eb.onopen = function () {
        eb.registerHandler('streamer.do-reload.' + streamerCode, function (err, msg) {
            window.location = msg.body.location;
        });
    }

})();

