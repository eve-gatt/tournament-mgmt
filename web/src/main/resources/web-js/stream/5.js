(function () {

    var eb = new EventBus("/ws");
    eb.enableReconnect(true);

    function render(match, history) {
        let wl = Array.from(d3.group(history, d => d.tournamentName),
            (([k, v]) => ({tournament: k, wl: v.map(m => m.Victor === match.blue_team_name)})));

        d3.select('.history img').attr('src', match.blue_team_logo);
        d3.select('.history h4').text(match.blue_team_name);
        let appear = d3.select('.history .appearances').selectAll('.appear').data(wl, d => match.id + "/" + d.tournament);
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
            d3.select('.history').classed('animate__zoomIn', true)
                .on('animationend', () => {
                    d3.select('.history').classed('animate__zoomIn', false);
                });
        }, 1);
    }

    function fetchHistory(match) {
        return d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/history/' + match.blue_team_name)
    }

    d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
        .then(function (match) {
            fetchHistory(match).then(history => render(match, history));
        });

    eb.onopen = function () {
        eb.registerHandler('streamer.new-match', function (err, msg) {
            let match = JSON.parse(msg.body);
            fetchHistory(match).then(history => render(match, history));
        });
    }

})();
