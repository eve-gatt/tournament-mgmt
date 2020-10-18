(function () {

    let overlay = d3.select('.overlay');

    function pilots(match, colour) {
        let json = JSON.parse(match[colour + 'json']);
        let pilots = overlay.select('.pilots.' + colour).selectAll('.pilot').data(json.comp);
        pilots.enter().append('div').classed('pilot', true).text(d => d.class);
    }

    function render(match) {
        pilots(match, 'red')
        pilots(match, 'blue')
    }

    d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
        .then(function (data) {
            render(data);
        });

})();
