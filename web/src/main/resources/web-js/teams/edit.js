(function () {

    function actions(sel) {
        if (!locked)
            sel.append('a').attr('href', '#').text('Kick');
        sel.append('a').attr('href', '#').text('Name in use on TD');
    }

    function renderTeamMembers(data) {
        if (data.length > 0) {
            d3.select('.teammembers .no-teammembers').style('display', 'none');
            var teammembers = d3.select('.teammembers table tbody').selectAll('tr').data(data);
            var entering = teammembers.enter().append('tr');
            entering.append('td').text(d => d.name);
            entering.append('td').call(actions)
        } else {
            d3.select('.teammembers table').style('display', 'none');
        }
    }

    d3.json('/auth/tournament/' + tournamentUuid + '/teams/' + teamUuid + '/members/data').then(function (data) {
        renderTeamMembers(data);
    });

})();
