(function () {

    var eb = new EventBus("/ws");
    eb.enableReconnect(true);

    let fixture = d3.select('.fixture');

    function pilots(match, colour) {
        let json = JSON.parse(match[colour + 'json']);
        let pilots = fixture.select('.pilots.' + colour).selectAll('.pilot').data(json.comp, d => match.id + "/" + d.pilot);
        pilots.exit().remove();
        pilots.enter().append('div').classed('pilot', true).text(d => d.pilot);
    }

    function render(match) {
        let blue = fixture.select('.team.blue');
        blue.select('img').attr('src', match.blue_team_logo);
        blue.select('div').text(match.blue_team_name);
        let red = fixture.select('.team.red');
        red.select('img').attr('src', match.red_team_logo);
        red.select('div').text(match.red_team_name);
        pilots(match, 'blue');
        pilots(match, 'red');
    }

    d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
        .then(function (data) {
            render(data);
        });

    eb.onopen = function () {
        eb.registerHandler('streamer.new-match', function (err, msg) {
            render(JSON.parse(msg.body));
        });
    }

})();
