(function () {

    function historyIcon(sel) {
        sel.append('i')
            .classed('far', true)
            .classed('fa-question-circle', d => d === -2)
            .classed('fa-times-circle', d => d === -1)
            .classed('fa-check-circle', d => d === 1)
            .classed('fa-dot-circle', d => d === 0);
    }

    function tournamentWinLoss(sel) {
        // sel.append('div').classed('t', true).text(d => d.tournament);
        let exists = sel.filter(d => d);
        exists.append('div').text(d => d.W);
        exists.append('div').classed('label', true).text('W');
        exists.append('div').text(d => d.L);
        exists.append('div').classed('label', true).text('L');
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

        let uniqueATs = [...new Set(team.comp.map(d => d.tournaments).flat().map(d => d.tournament))]
            .sort((a, b) => d3.ascending(+a.substring(2), +b.substring(2)));

        let roster = sel.select('.roster');

        let matchLabelRow = roster.append('div').classed('pilot', true).datum({pilot: 'match-title'});
        matchLabelRow.append('div');
        matchLabelRow.append('div');
        matchLabelRow.append('div').classed('label', true).text('AO Matches');
        matchLabelRow.append('div').classed('label', true).text('Previous ATs');

        let matchNumbersRow = roster.append('div').classed('pilot', true).datum({pilot: 'match-numbers'});
        matchNumbersRow.append('div');
        matchNumbersRow.append('div');
        let matchNumbers = matchNumbersRow
            .append('div').append('div').classed('match-numbers', true);
        for (let i = 1; i <= maxMatches; i++) {
            matchNumbers.append('div').text(i);
        }
        let tournaments = matchNumbersRow
            .append('div').append('div').classed('tournament-numbers', true);
        uniqueATs.forEach(t => tournaments.append('div').text(t));


        roster.append('div').classed('pilot', true).datum({pilot: 'captain-title'})
            .classed('label left-label', true)
            .text('Captain');
        let captain = roster.append('div').classed('pilot', true)
            .datum(team.comp.find(d => d.pilot === team.captain));
        captain.append('div').text(d => d.pilot);
        captain.append('div').text(d => d.ship);
        captain.append('div').append('div').classed('history', true).selectAll('h').data(d => d.history)
            .enter().append('div').call(historyIcon);
        captain.append('div').append('div').classed('tournaments', true).selectAll('.t-record')
            .data(d => uniqueATs.map(at => d.tournaments.find(t => t.tournament === at)))
            .enter().append('div').classed('t-record', true).call(tournamentWinLoss);

        roster.append('div').classed('pilot', true).datum({pilot: 'pilots-title'})
            .classed('label left-label', true)
            .text('Pilots');
        let pilots = roster.selectAll('.pilot').data(team.comp.filter(d => d.pilot !== team.captain), d => d.pilot);
        let pilot = pilots.enter().append('div').classed('pilot', true);
        pilot.append('div').text(d => d.pilot);
        pilot.append('div').text(d => d.ship);
        pilot.append('div').append('div').classed('history', true).selectAll('h').data(d => d.history)
            .enter().append('div').call(historyIcon);
        pilot.append('div').append('div').classed('tournaments', true).selectAll('.t-record')
            .data(d => uniqueATs.map(at => d.tournaments.find(t => t.tournament === at)))
            .enter().append('div').classed('t-record', true).call(tournamentWinLoss);
    }

    function init(sel, url) {

        let rvb = d3.select(sel).append('div').classed('redVsBlue', true);
        rvb.append('div').classed('red team', true);
        rvb.append('div').classed('blue team', true);
        setup(rvb.select('.red'));
        setup(rvb.select('.blue'));

        d3.json(url).then(function (inData) {
            render(rvb.select('.red'), inData.red);
            render(rvb.select('.blue'), inData.blue);
        });
    }

    window.streamInitRedVsBlue = init;

})
();
