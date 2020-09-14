function hasRole(d, role) {
    return isSuperuser
        || (role === 'organiser' && d.is_creator)
        || (d.roles && d.roles.includes(role));
}

export function tournamentActions(sel) {
    sel.append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/home').text('Home');
    sel.filter(d => hasRole(d, 'organiser'))
        .append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/edit').text('Edit details');
    sel.filter(d => hasRole(d, 'organiser'))
        .append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/delete').text('Delete');

    sel.append('a').attr('href', '#').text('Search for pilot');
    sel.append('a').attr('href', d => '/auth/tournament/' + d.uuid + '/teams').text('Manage teams');
    sel.append('a').attr('href', d => '/auth/tournament/' + d.uuid + '/thunderdome').text('Manage Thunderdome');

    sel.filter(d => hasRole(d, 'organiser'))
        .append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/roles').text('Manage roles')

    sel.filter(d => hasRole(d, 'organiser') || hasRole(d, 'referee'))
        .append('a')
        .attr('href', d => '/auth/tournament/' + d.uuid + '/referee').text('Referee')
}
