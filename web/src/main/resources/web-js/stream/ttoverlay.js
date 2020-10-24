(function () {

    // let colorScheme = d3.scaleOrdinal(d3.schemeCategory10);
    let overlay = d3.select('.overlay');

    const SCHEME = {
        "Assault Frigate": "yellow",
        "Attack Battlecruiser": "blue",
        "Black Ops Battleship": "green",
        "Combat Battlecruiser": "blue",
        "Command Destroyer": "orange",
        "Command Ship": "blue",
        "Corvette": "grey",
        "Covert Ops Ship": "yellow",
        "Electronic Attack Ship": "yellow",
        "Heavy Assault Cruiser": "purple",
        "Heavy Interdictor": "purple",
        "Industrial": "grey",
        "Interceptor": "yellow",
        "Logistics Cruiser": "red",
        "Logistics Frigate": "yellow",
        "Marauder": "green",
        "Mining Frigate": "yellow",
        "Navy Battlecruiser": "blue",
        "Navy Battleship": "green",
        "Navy Cruiser": "purple",
        "Navy EWAR Frigate": "yellow",
        "Navy Frigate": "yellow",
        "Pirate Battleship": "green",
        "Pirate Corvette": "grey",
        "Pirate Cruiser": "purple",
        "Pirate Frigate": "yellow",
        "Precursor Battlecruiser": "blue",
        "Precursor Battleship": "green",
        "Precursor Command Destroyer": "orange",
        "Precursor Cruiser": "purple",
        "Precursor Frigate": "yellow",
        "Precursor HAC": "purple",
        "Precursor Logistics Cruiser": "red",
        "Precursor Support Cruiser": "red",
        "Recon Ship": "purple",
        "Stealth Bomber": "yellow",
        "Strategic Cruiser": "purple",
        "T1 Battleship": "green",
        "T1 Cruiser": "purple",
        "T1 Destroyer": "orange",
        "T1 EWAR Cruiser": "purple",
        "T1 EWAR Frigate": "yellow",
        "T1 Frigate": "yellow",
        "T1 Support Cruiser": "red",
        "T1 Support Frigate": "red",
        "Tactical Destroyer": "orange",
        "Wildcard Cov Ops": "yellow",
        "Wildcard Cruiser": "purple",
        "Wildcard Frigate": "yellow",
        "Wildcard HAC": "purple",
        "Wildcard Interceptor": "yellow",
        "Wildcard Logistics Cruiser": "red",
        "Wildcard Recon Ship": "purple",
        "Wildcard Stealth Bomber": "yellow",
    }

    function sizeName(name) {
        return name.length > 25 ? name.substring(0, 23) + '...' : name;
    }

    function pilots(match, colour) {
        let json = JSON.parse(match[colour + 'json']);
        let pilots = overlay.select('.pilots.' + colour).selectAll('.pilot').data(json.comp);
        let enteringPilot = pilots.enter().append('div').classed('pilot', true);
        enteringPilot.append('div')
            .text(d => d.overlay);
        enteringPilot.append('div')
            .classed('colour-overlay', true)
            .style('background-color', d => SCHEME[d.overlay]);
    }

    function render(match) {
        overlay.select('.redname').text(sizeName("420 MLG TWINTURBO 3000 EMPIRE ALLIANCE RELOADED"));
        overlay.select('.bluename').text(sizeName(match.blue_team_name.toUpperCase()));
        pilots(match, 'red');
        pilots(match, 'blue');
    }

    d3.json('/stream/93e30c88-c769-48a8-a547-7d464bab5d14/matches/latest-match/data')
        .then(function (data) {
            render(data);
        });

})();
