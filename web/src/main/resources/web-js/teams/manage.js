(function () {

    function actions(sel) {
    }

    function renderTeams(data) {
        if (data.length > 0) {
            d3.select('.teams .no-teams').style('display', 'none');
            var teams = d3.select('.teams table tbody').selectAll('tr').data(data);
            var entering = teams.enter().append('tr');
            entering.append('td').text(d => d.name);
            entering.append('td').text(d => d.captain);
            entering.append('td').call(actions)
        } else {
            d3.select('.teams table').style('display', 'none');
        }
    }

    d3.json('/auth/tournament/' + uuid + '/teams/data').then(function (data) {
        renderTeams(data);
    });

})();
