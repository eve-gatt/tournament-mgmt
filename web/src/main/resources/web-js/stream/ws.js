(function () {

    var eb = new EventBus("/ws");
    eb.enableReconnect(true);

    eb.onclose = function () {
        console.log('eb.onclose()');
    }

    eb.onopen = function () {
        eb.registerHandler('do-reload', function (err, msg) {
            let location = msg.body.location;
            console.log("Navigating to", location);
            window.location = location;
        });
    }

})();
