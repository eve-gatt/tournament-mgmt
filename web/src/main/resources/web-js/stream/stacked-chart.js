(function () {

    function init(sel, url, grouper, label) {
        var margin = {top: 60, right: 20, bottom: 20, left: 300},
            width = 1400 - margin.left - margin.right,
            height = 450 - margin.top - margin.bottom;

        var svg = d3.select(sel).append("svg")
            .attr("viewBox", "0 0 " + (width + margin.left + margin.right) + " " + (height + margin.top + margin.bottom))
            .append("g")
            .attr("transform",
                "translate(" + margin.left + "," + margin.top + ")");


        var x = d3.scaleLinear()
                .rangeRound([0, width]),
            y = d3.scaleBand()
                .rangeRound([0, height])
                .paddingInner(0.2)
                .align(0.1),
            z = d3.scaleOrdinal(d3.schemeSet3);

        d3.json(url).then(function (inData) {

            var data = Array.from(d3.group(inData.data, d => d[grouper]),
                ([grouper, value]) => ({grouper, value}));

            data.forEach(grouper => grouper.total = grouper.value.map(d => d.count).reduce((a, b) => a + b));

            var keys = [...new Set(data.map(t => t.value.map(v => v.tournamentName)).flat())];

            var value = (d, key) => {
                let wins = d.value.find(e => e.tournamentName === key)
                return wins ? wins.count : 0;
            };
            data = data.sort((a, b) => d3.descending(a.total, b.total))
                .slice(0, 10);

            x.domain([0, d3.max(data, function (d) {return d.total;})]).nice();
            y.domain(data.map(d => d.grouper));
            z.domain(keys);

            let series = d3.stack().keys(keys).value(value)(data)
                .map(d => (d.forEach(v => v.key = d.key), d));

            svg.append("g")
                .selectAll("g")
                .data(series)
                .enter().append("g")
                .attr("fill", function (d) { return z(d.key); })
                .selectAll("rect")
                .data(function (d) { return d; })
                .enter().append("rect")
                .attr("y", function (d) { return y(d.data.grouper); })
                .attr("x", function (d) { return x(d[0]); })
                .attr("width", function (d) { return x(d[1]) - x(d[0]); })
                .attr("height", y.bandwidth())
                .append('title')
                .text(d => d.key + ' - ' + (d[1] - d[0]) + ' wins');

            svg.append("g")
                .attr("class", "axis")
                .attr("transform", "translate(0,0)")
                .call(d3.axisLeft(y));

            svg.append("g")
                .attr("class", "axis")
                .attr("transform", "translate(0,0)")
                .call(d3.axisTop(x).ticks(null, "s"))
                .append("text")
                .attr("y", 2)
                .attr("x", x(x.ticks().pop()) + 0.5)
                .attr("dy", "0.32em")
                .attr("fill", "#ffffff")
                .attr("font-weight", "bold")
                .attr("text-anchor", "start")
                .text("Match " + label)
                .style('font-size', '2em')
                .attr("transform", "translate(" + (-width) + ",-40)");

            var legend = svg.append("g")
                .attr("font-size", "1.2em")
                .attr("text-anchor", "end")
                .selectAll("g")
                .data(keys.slice().reverse())
                .enter().append("g")
                //.attr("transform", function(d, i) { return "translate(0," + i * 20 + ")"; });
                .attr("transform", function (d, i) { return "translate(-50," + (height - 30 - i * 20) + ")"; });

            legend.append("rect")
                .attr("x", width - 19)
                .attr("width", 19)
                .attr("height", 19)
                .attr("fill", z);

            legend.append("text")
                .attr("x", width - 24)
                .attr("y", 9.5)
                .attr("dy", "0.32em")
                .attr("fill", "#ffffff")
                .text(function (d) { return d; });

        });
    }

    window.streamInitStackedChart = init;

})();
