import {tournamentActions} from '../modules/actions.js'

(function () {

    function renderTournaments(data) {
        if (data.length > 0) {
            d3.select('.tournaments .no-tournaments').style('display', 'none');
            var tournaments = d3.select('.tournaments table tbody').selectAll('tr').data(data);
            var entering = tournaments.enter().append('tr');
            entering.append('td').text(d => d.name);
            entering.append('td').text(d => d.created_by);
            entering.append('td').text(d => d.team_count);
            entering.append('td').text(d => d.teams_locked);
            entering.append('td').call(tournamentActions)
        } else {
            d3.select('.tournaments table').style('display', 'none');
        }
    }

    d3.json("/auth/profile/tournaments").then(function (data) {
        renderTournaments(data);
    });

})();
