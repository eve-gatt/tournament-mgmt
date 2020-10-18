(function () {

    let fixture = d3.select('.fixture');

    function pilots(match, colour) {
        let json = JSON.parse(match[colour + 'json']);
        let pilots = fixture.select('.pilots.' + colour).selectAll('.pilot').data(json.comp);
        pilots.enter().append('div').classed('pilot', true).text(d => d.pilot);
    }

    function render(match) {
        let blue = fixture.select('.team.blue');
        blue.append('img').attr('src', match.blue_team_logo);
        blue.append('div').text(match.blue_team_name);
        let red = fixture.select('.team.red');
        red.append('img').attr('src', match.red_team_logo);
        red.append('div').text(match.red_team_name);
        pilots(match, 'blue');
        pilots(match, 'red');
    }

    d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
        .then(function (data) {
            render(data);
        });

})();
