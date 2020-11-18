(function () {

    var widgets = [];

    var eb = new EventBus("/ws");
    eb.enableReconnect(true);

    function hide(then) {
        d3.select('.overlay').classed('animate__zoomIn', false)
            .classed('animate__zoomOut', true)
            .on('animationend', then);
    }

    function unhide() {
        setTimeout(() => {
            d3.select('.overlay').classed('animate__zoomOut', false)
                .classed('animate__zoomIn', true)
                .on('animationend', () => {
                    d3.select('.overlay').classed('animate__zoomIn', false);
                });
        }, 200);
    }

    eb.onopen = function () {

        eb.registerHandler('streamer.widget-request', function (err, msg) {

            hide(() => {
                d3.select('.overlay').html(null);
                let widget = JSON.parse(msg.body);
                switch (widget.type) {
                    case "CLEAR":
                        break;
                    case "TEAM_HISTORY":
                        streamInit5('.overlay', widget.colour, `/stream/${widget.colour}TeamHistory/data`);
                        break;
                    case "SANKEY":
                        streamInitSankey('.overlay');
                        break;
                    case "LINE":
                        streamInitLineChart('.overlay', `/stream/${widget.name}/data`);
                        break;
                    case "LINE2":
                        streamInitLine2Chart('.overlay', `/stream/${widget.name}/data`);
                        break;
                    case "STACKED":
                        streamInitStackedChart('.overlay', `/stream/${widget.name}/data`, widget.grouper, widget.sublabel);
                        break;
                    case "PIE":
                        streamInitPieChart('.overlay', `/stream/${widget.name}/data`, widget.grouper, widget.sublabel);
                        break;
                    case "BAR":
                        streamInitBarChart('.overlay', `/stream/${widget.name}/data`);
                        break;
                    case "RED_VS_BLUE":
                        streamInitRedVsBlue('.overlay', `/stream/${widget.name}/data`);
                        break;
                }
                unhide();
            });

        });

        eb.registerHandler('streamer.widget-clear', function (err, msg) {

        });
    }

})();
