(function () {

    const team = {
        name: 'Blue Team',
        pilots: [
            {name: 'Levi Nineveh', char_id: 624049117, ship: 'Hecate', ship_id: 22456},
            {name: 'Rixx Javix', char_id: 95308308, ship: 'Rokh', ship_id: 11198},
            {name: 'Eargonall Kaundur', char_id: 90308296, ship: 'Loki', ship_id: 11198},
            {name: 'Xoorauch Destroyer', char_id: 90781655, ship: 'Megathron', ship_id: 11186},
            {name: 'Ithugor Wells', char_id: 1001131163, ship: 'Daredevil', ship_id: 587},
            {name: 'Stowesh', char_id: 2115931058, ship: 'Sleipnir', ship_id: 587},
            {name: 'TheLastSparton', char_id: 96552886, ship: 'Sleipnir', ship_id: 587},
            {name: 'Tyrion Hekki', char_id: 1783706639, ship: 'Sleipnir', ship_id: 587},
            {name: 'dexter xio', char_id: 1737852458, ship: 'Griffin', ship_id: 587},
            {name: 'Mira Chieve', char_id: 2117448845, ship: 'Griffin', ship_id: 587}
        ]
    }

    d3.select('.teamname').text(team.name);
    const pilots = d3.select('.pilots').selectAll('.pilot').data(team.pilots);
    const entering = pilots.enter().append('div').classed('pilot', true);
    entering.style('background-image', d => `url(https://images.evetech.net/types/${d.ship_id}/render?size=128)`);
    entering.append('h2').text(d => d.name);
    entering.append('img').attr('src', d => `https://images.evetech.net/characters/${d.char_id}/portrait?size=128`)

})();
