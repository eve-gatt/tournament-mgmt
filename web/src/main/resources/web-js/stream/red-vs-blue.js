(function () {

    function historyIcon(sel) {
        sel.append('i')
            .classed('far', true)
            .classed('fa-question-circle', d => d === -2)
            .classed('fa-times-circle', d => d === -1)
            .classed('fa-check-circle', d => d === 1)
            .classed('fa-dot-circle', d => d === 0);
    }

    function setup(sel) {
        let title = sel.append('div').classed('title', true);
        title.append('div').classed('logo', true).append('img');
        title.append('div').classed('name', true);

        let roster = sel.append('div').classed('roster', true);

    }

    function render(sel, team) {
        let title = sel.select('.title');
        title.select('.logo img').attr('src', team.logo + "?size=128");
        title.select('.name').text(team.name);

        let maxMatches = d3.max(team.comp, d => d.history.length);

        let roster = sel.select('.roster');

        let matchLabelRow = roster.append('div').classed('pilot', true).datum({pilot: 'match-title'});
        matchLabelRow.append('div');
        matchLabelRow.append('div');
        matchLabelRow.append('div').classed('label', true).text('Matches');

        let matchNumbersRow = roster.append('div').classed('pilot', true).datum({pilot: 'match-numbers'});
        matchNumbersRow.append('div');
        matchNumbersRow.append('div');
        let matchNumbers = matchNumbersRow
            .append('div').append('div').classed('match-numbers', true);

        for (let i = 1; i <= maxMatches; i++) {
            matchNumbers.append('div').text(i);
        }

        roster.append('div').classed('pilot', true).datum({pilot: 'captain-title'})
            .classed('label left-label', true)
            .text('Captain');
        let captain = roster.append('div').classed('pilot', true)
            .datum(team.comp.find(d => d.pilot === team.captain));
        captain.append('div').text(d => d.pilot);
        captain.append('div').text(d => d.ship);
        captain.append('div').append('div').classed('history', true).selectAll('h').data(d => d.history)
            .enter().append('div').call(historyIcon);

        roster.append('div').classed('pilot', true).datum({pilot: 'pilots-title'})
            .classed('label left-label', true)
            .text('Pilots');
        let pilots = roster.selectAll('.pilot').data(team.comp.filter(d => d.pilot !== team.captain), d => d.pilot);
        let pilot = pilots.enter().append('div').classed('pilot', true);
        pilot.append('div').text(d => d.pilot);
        pilot.append('div').text(d => d.ship);
        pilot.append('div').append('div').classed('history', true).selectAll('h').data(d => d.history)
            .enter().append('div').call(historyIcon);

    }

    function init(sel, url) {

        let rvb = d3.select(sel).append('div').classed('redVsBlue', true);
        rvb.append('div').classed('red team', true);
        rvb.append('div').classed('middle', true);
        rvb.append('div').classed('blue team', true);
        setup(rvb.select('.red'));
        setup(rvb.select('.blue'));

        d3.json(url).then(function (inData) {
            console.log(inData);
            render(rvb.select('.red'), inData.red);
            render(rvb.select('.blue'), inData.blue);
        });
    }

    window.streamInitRedVsBlue = init;

})();
