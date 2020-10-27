(function () {

    var widgets = [];

    var eb = new EventBus("/ws");
    eb.enableReconnect(true);

    class Widget {
        constructor(selString, colour) {
            this.selector = d3.select(selString).append('div')
                .classed('widget-5 animate__animated', true);
            this.colour = colour;

            let title = this.selector.append('div').classed('title', true);
            title.append('img');
            title.append('h4');
            this.selector.append('div').classed('appearances', true);
        }

        render(match, history) {
            let wl = Array.from(d3.group(history, d => d.tournamentName),
                (([k, v]) => ({tournament: k, wl: v.map(m => m.Victor === match[this.colour + '_team_name'])})));

            this.selector.select('img').attr('src', match[this.colour + '_team_logo']);
            this.selector.select('h4').text(match[this.colour + '_team_name']);
            let appear = this.selector.select('.appearances').selectAll('.appear').data(wl, d => match.id + "/" + d.tournament);
            let appearEnter = appear.enter().append('div').classed('appear', true);
            appear.exit().remove();
            appearEnter.append('div').text(d => d.tournament);
            let winloss = appearEnter.append('div').classed('winloss', true);
            let icons = winloss.selectAll('i').data(d => d.wl);
            icons.enter().append('i')
                .classed('fas', true)
                .classed('fa-check', d => d)
                .classed('fa-times', d => !d);
            window.setTimeout(id => {
                this.selector.classed('animate__zoomIn', true)
                    .on('animationend', () => {
                        this.selector.classed('animate__zoomIn', false);
                    });
            }, 1);
        }

        fetchHistory(match) {
            if (!this.match) this.match = match;
            return d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/history/' + this.match[this.colour + '_team_name']);
        }

        switchColour(colour) {
            this.colour = colour;
            this.fetchHistory(this.match)
                .then(history => this.render(this.match, history));
        }

    }

    function init(sel, c) {
        widgets.push(new Widget(sel, c));
        d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
            .then(function (match) {
                widgets.forEach(w => {
                    w.fetchHistory(match).then(history => w.render(match, history));
                })
            });
    }

    eb.onopen = function () {
        eb.registerHandler('streamer.new-match', function (err, msg) {
            let match = JSON.parse(msg.body);
            widgets.forEach(w => {
                w.fetchHistory(match).then(history => w.render(match, history));
            });
        });

        eb.registerHandler('widget-request', function (err, msg) {
            let colour = msg.body.colour;
            if (widgets.length === 1) {
                widgets[0].switchColour(colour);
            }
        });
    }

    window.streamInit5 = init;
})();
