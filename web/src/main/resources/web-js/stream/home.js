(function () {
    d3.select('.teamname').text(team.name);
    const pilots = d3.select('.pilots').selectAll('.pilot').data(team.pilots);
    const entering = pilots.enter().append('div').classed('pilot', true);
    entering.style('background-image', d => `url(https://images.evetech.net/types/${d.ship_id}/render?size=1024)`);
    entering.append('h2').classed('ship', true).text(d => d.ship);
    entering.append('h4').classed('char', true).text(d => d.name);
    entering.append('img').attr('src', d => `https://images.evetech.net/characters/${d.char_id}/portrait?size=256`)

    d3.selectAll('.pilot').each(function (d, i) {
        setTimeout(id => {
            d3.select(this).classed('zoomed', true);
        }, (i + 1) * 5000)
    });
    d3.selectAll('.pilot').each(function (d, i) {
        setTimeout(id => {
            d3.select(this).classed('zoomed', false);
        }, (i + 1) * 5000 + 2000)
    });
})();
