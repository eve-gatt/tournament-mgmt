export function tournamentActions(sel) {
    sel.filter(d => d.canEdit).append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/edit').text('Edit details');
    sel.filter(d => d.canDelete).append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/delete').text('Delete');
    sel.filter(d => d.canSearchPilot).append('a').attr('href', '#').text('Search for pilot');
    sel.filter(d => d.canManageTeams).append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/teams').text('Manage teams');
    sel.filter(d => d.canManageTD).append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/thunderdome').text('Manage Thunderdome');
    sel.filter(d => d.canManageRoles).append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/roles').text('Manage roles')
    sel.filter(d => d.canReferee).append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/referee').text('Referee')
}
