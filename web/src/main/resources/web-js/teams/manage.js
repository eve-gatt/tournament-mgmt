(function () {

    function actions(sel) {
        sel.append('a').attr('href', '#').text('View');
        sel.append('a')
            .attr('href', d => '/auth/tournament/' + uuid + '/teams/' + d.uuid + '/edit')
            .text('Edit (Captain\'s link)');
        sel.append('a')
            .attr('href', d => '/auth/tournament/' + uuid + '/teams/' + d.uuid + '/remove')
            .text('Remove');
    }

    function renderTeams(data) {
        if (data.length > 0) {
            d3.select('.teams .no-teams').style('display', 'none');
            data.sort((a, b) => d3.ascending(a.name, b.name));
            var teams = d3.select('.teams table tbody').selectAll('tr').data(data);
            var entering = teams.enter().append('tr');
            entering.append('td').text(d => d.name);
            entering.append('td').text(d => d.captain);
            entering.append('td').text(d => d.locked ? 'yes' : 'no');
            entering.append('td').text(d => d.member_count > 0 ? d.member_count : '-');
            entering.append('td').call(actions)
        } else {
            d3.select('.teams table').style('display', 'none');
        }
    }

    d3.json('/auth/tournament/' + uuid + '/teams/data').then(function (data) {
        renderTeams(data);
    });

})();
