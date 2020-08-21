(function () {

    function actions(sel) {
        sel.filter(d => d.canEdit).append('a').attr('href', '#').text('Edit details');
        sel.filter(d => d.canDelete).append('a').attr('href', '#').text('Delete');
        sel.filter(d => d.canSearchPilot).append('a').attr('href', '#').text('Search for pilot');
        sel.filter(d => d.canManageTeams).append('a').attr('href', '#').text('Manage teams');
        sel.filter(d => d.canManageTD).append('a').attr('href', '#').text('Manage Thunderdome');
        sel.filter(d => d.canManageRoles).append('a').attr('href', '#').text('Manage Roles');
        sel.filter(d => d.canManageBranding).append('a').attr('href', '#').text('Manage Branding');
    }

    function renderTournaments(data) {
        if (data.length > 0) {
            d3.select('.tournaments .no-tournaments').style('display', 'none');
            var tournaments = d3.select('.tournaments table tbody').selectAll('tr').data(data);
            var entering = tournaments.enter().append('tr');
            entering.append('td').text(d => d.name);
            entering.append('td').call(actions)
        } else {
            d3.select('.tournaments table').style('display', 'none');
        }
    }

    d3.json("/auth/profile/tournaments").then(function (data) {
        renderTournaments(data);
    });

})();
