(function () {

    let colorScheme = d3.scaleOrdinal(d3.schemeCategory10);
    let overlay = d3.select('.overlay');

    function pilots(match, colour) {
        let json = JSON.parse(match[colour + 'json']);
        let pilots = overlay.select('.pilots.' + colour).selectAll('.pilot').data(json.comp);
        let enteringPilot = pilots.enter().append('div').classed('pilot', true);
        enteringPilot.append('div')
            .text(d => d.class);
        enteringPilot.append('div')
            .classed('colour-overlay', true)
            .style('background-color', d => colorScheme(d.class));
    }

    function render(match) {
        pilots(match, 'red');
        pilots(match, 'blue');
    }

    d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
        .then(function (data) {
            render(data);
        });

})();
