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
            let wl = Array.from(d3.group(history, d => d.tournamentName, d => d.MatchNo),
                ([k, matches]) => ({
                    tournament: k,
                    matches: Array.from(matches, ([matchId, ms]) => ({
                        matchId: matchId,
                        wl: ms.map(m => m.Victor === match[this.colour + '_team_name'])
                    }))
                }));

            this.selector.select('img').attr('src', match[this.colour + '_team_logo']);
            this.selector.select('h4').text(match[this.colour + '_team_name']);
            let appearance = this.selector.select('.appearances').selectAll('.appear').data(wl, d => match.id + "/" + d.tournament);
            let appearEnter = appearance.enter().append('div').classed('appear', true);
            appearance.exit().remove();

            appearEnter.append('div').text(d => d.tournament);
            let matches = appearEnter.append('div').classed('matches', true);
            let ms = matches.selectAll('m').data(d => d.matches);
            let msAppear = ms.enter().append('div').classed('m', true);

            let winloss = msAppear.append('div').classed('winloss', true);
            let icons = winloss.selectAll('i').data(d => d.wl);
            icons.enter().append('i')
                .classed('fas', true)
                .classed('fa-check', d => d)
                .classed('fa-times', d => !d);
            window.setTimeout(id => this.unhide(), 1);
        }

        fetchHistory(match) {
            return d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/history/' + match[this.colour + '_team_name']);
        }

        switchColour(colour) {
            this.unhide();
            this.colour = colour;
            this.fetchHistory(this.latestMatch)
                .then(history => this.render(this.latestMatch, history));
        }

        start(match) {
            this.unhide();
            if (match) {
                this.latestMatch = match;
                this.fetchHistory(match).then(history => this.render(match, history));
            } else {
                d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
                    .then(match => {
                        this.latestMatch = match;
                        this.fetchHistory(match).then(history => this.render(match, history));
                    });
            }
        }

        hide() {
            this.selector.classed('animate__zoomIn', false)
                .classed('animate__zoomOut', true)
                .on('animationend', () => {

                });
        }

        unhide() {
            this.selector.classed('animate__zoomOut', false)
                .classed('animate__zoomIn', true)
                .on('animationend', () => {
                    this.selector.classed('animate__zoomIn', false);
                });
        }

    }

    function init(sel, c) {
        let w = new Widget(sel, c);
        widgets.push(w);
        w.start();
    }

    eb.onopen = function () {
        eb.registerHandler('streamer.new-match', function (err, msg) {
            let match = JSON.parse(msg.body);
            widgets.forEach(w => {
                w.start(match);
            });
        });

        eb.registerHandler('streamer.widget-request', function (err, msg) {
            let colour = msg.body.colour;
            if (widgets.length === 1) {
                widgets[0].switchColour(colour);
            }
        });

        eb.registerHandler('streamer.widget-clear', function (err, msg) {
            if (widgets.length === 1) {
                widgets[0].hide();
            }
        });
    }

    window.streamInit5 = init;
})();
