(function () {

    function actions(sel) {
        if ((locked && (isSuperuser || isOrganiser || isCreator))
            || (!locked && (isSuperuser || isOrganiser || isCreator || isTeamCaptain)))
            sel.append('a')
                .attr('href', d => '/auth/tournament/' + tournamentUuid + '/teams/' + teamUuid + '/kick/' + d.uuid)
                .text('Kick');
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

    function renderMatches(data) {
        let matches = d3.select('.matches').selectAll('.match').data(data, d => d.id);
        let entering = matches.enter().append('div').classed('match', true);
        entering.append('a')
            .attr('href', d => `/auth/tournament/${tournamentUuid}/match/${d.id}`)
            .text(d => d.created_at_formatted);
        entering.append('a')
            .attr('href', d => `/auth/tournament/${tournamentUuid}/match/${d.id}`)
            .text(d => `${d.red_team_name} vs ${d.blue_team_name}`);
    }

    d3.json('/auth/tournament/' + tournamentUuid + '/teams/' + teamUuid + '/members/data')
        .then(function (data) {
            renderTeamMembers(data);
        });

    d3.json('/auth/tournament/' + tournamentUuid + '/teams/' + teamUuid + '/matches/data')
        .then(function (data) {
            renderMatches(data);
        });

})();
