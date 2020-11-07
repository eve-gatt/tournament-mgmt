(function () {

    var widgets = [];

    var eb = new EventBus("/ws");
    eb.enableReconnect(true);

    function hide() {
        this.selector.classed('animate__zoomIn', false)
            .classed('animate__zoomOut', true)
            .on('animationend', () => {

            });
    }

    function unhide() {
        this.selector.classed('animate__zoomOut', false)
            .classed('animate__zoomIn', true)
            .on('animationend', () => {
                this.selector.classed('animate__zoomIn', false);
            });
    }

    eb.onopen = function () {

        eb.registerHandler('streamer.widget-request', function (err, msg) {

        });

        eb.registerHandler('streamer.widget-clear', function (err, msg) {

        });
    }

})();
