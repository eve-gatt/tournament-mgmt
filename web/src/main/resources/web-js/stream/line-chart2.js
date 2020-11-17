(function () {

    function init(sel, url) {
        var margin = {top: 40, right: 260, bottom: 40, left: 60},
            width = 1400 - margin.left - margin.right,
            height = 900 - margin.top - margin.bottom;

        var svg = d3.select(sel).append("svg")
            .attr("viewBox", "0 0 " + (width + margin.left + margin.right) + " " + (height + margin.top + margin.bottom))
            .append("g")
            .attr("transform",
                "translate(" + margin.left + "," + margin.top + ")");

        var x = d3.scaleLinear().range([0, width]),
            y = d3.scaleLinear().range([height, 0]),
            z = d3.scaleOrdinal(d3.schemeSet2);

        var line1 = d3.line()
            .curve(d3.curveCatmullRom)
            .x(function (d) {return x(d.dayIndex);})
            .y(function (d) {return y(d.cum);});

        d3.json(url).then(function (data) {

            var allDays = [...new Set(data.data.map(d => d.day))]
                .map(d => new Date(d));

            data.data.forEach(d => d.day = new Date(d.day));

            var series = Array.from(d3.group(data.data, d => d.winner),
                ([team, days]) => ({
                    team: team,
                    days: days.map((cur, idx, arr) => {
                        cur.dayIndex = 1 + allDays.findIndex(e => e.getTime() === cur.day.getTime());
                        cur.cum = arr.slice(0, idx + 1).reduce((acc, cur) => cur.count + acc, 0);
                        return cur;
                    })
                }));

            x.domain([1, allDays.length]);
            y.domain([0,
                d3.max(series, c => d3.max(c.days, function (d) {return d.cum;}))
            ]);

            svg.append("g")
                .attr("class", "axis axis--x")
                .attr("transform", "translate(0," + height + ")")
                .call(d3.axisBottom(x)
                    .ticks(Math.max(1, allDays.length - 1))
                    .tickFormat(d => "Day " + d3.format("d")(d)));

            svg.append("g")
                .attr("class", "axis axis--y")
                .call(d3.axisLeft(y)
                    .ticks(y.domain()[1])
                    .tickFormat(d3.format("d")))
                .append("text")
                .attr("transform", "rotate(-90)")
                .attr("y", 6)
                .attr("dy", "0.71em")
                .attr("fill", "#ffffff")
                .style('font-size', '1.6em')
                .text("wins");

            var team = svg.selectAll(".team")
                .data(series)
                .enter().append("g")
                .attr("class", "team");

            team.append('g').classed('dots', true)
                .selectAll('dot')
                .data(d => d.days)
                .enter()
                .append('circle')
                .attr('class', 'dot')
                .attr('cx', d => x(d.dayIndex))
                .attr('cy', d => y(d.cum))
                .attr('r', 8)
                .style("fill", d => z(d.winner));

            team.append("path")
                .attr("class", "line")
                .attr('fill', 'none')
                .attr('stroke', 'steelblue')
                .attr('stroke-width', '6px')
                .attr("d", d => line1(d.days))
                .style("stroke", d => z(d.team));

            team.append("text")
                .datum(d => ({id: d.team, value: d.days[d.days.length - 1]}))
                .attr("transform", d => "translate(" + (8 + x(d.value.dayIndex)) + "," + y(d.value.cum) + ")")
                .attr("x", 3)
                .attr("dy", "0.35em")
                .attr("fill", d => z(d.id))
                .style('font-size', '1.6em')
                .text(d => d.id);
        });
    }

    window.streamInitLine2Chart = init;

})();
