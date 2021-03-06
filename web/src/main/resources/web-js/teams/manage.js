(function () {

    let isRed = d => d.member_count === 0;
    let isYellow = d => (d.member_count > 0 && !d.locked) || (d.locked && d.message);
    let isGreen = d => d.locked && !d.message;

    function actions(sel) {
        sel.append('a')
            .attr('href', d => '/auth/tournament/' + uuid + '/teams/' + d.uuid + '/edit')
            .text('View');
        if (canRemove) {
            sel.append('a')
                .attr('href', d => '/auth/tournament/' + uuid + '/teams/' + d.uuid + '/remove')
                .text('Remove');
        }
    }

    function renderTeams(data) {
        if (data.length > 0) {

            d3.select('.red .count').text(data.filter(isRed).length);
            d3.select('.yellow .count').text(data.filter(isYellow).length);
            d3.select('.green .count').text(data.filter(isGreen).length);

            d3.select('.teams .no-teams').style('display', 'none');
            data.sort((a, b) => d3.ascending(a.name, b.name));
            var teams = d3.select('.teams table tbody').selectAll('tr').data(data);
            var entering = teams.enter().append('tr');
            entering.classed('red', isRed);
            entering.classed('yellow', isYellow);
            entering.classed('green', isGreen);
            var teamCell = entering.append('td').append('div').classed('team-name', true);
            teamCell.append('img').attr('src', d => d.logo + "?size=64");
            teamCell.append('span').text(d => d.name.length > 22 ? d.name.substring(0, 20) + '...' : d.name);
            entering.append('td').text(d => d.captain);
            entering.append('td').text(d => d.locked ? 'yes' : 'no');
            entering.append('td').text(d => d.member_count > 0 ? d.member_count : '-');
            entering.append('td').classed('actions', true).call(actions)
            entering.append('td').text(d => d.message);
        } else {
            d3.select('.teams table').style('display', 'none');
        }
    }

    d3.json('/auth/tournament/' + uuid + '/teams/data').then(function (data) {
        renderTeams(data);
    });

})();
