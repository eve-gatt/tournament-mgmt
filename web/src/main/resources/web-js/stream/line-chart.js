(function () {

    function init(sel, url) {
        var margin = {top: 40, right: 240, bottom: 40, left: 60},
            width = 1400 - margin.left - margin.right,
            height = 900 - margin.top - margin.bottom;

        var svg = d3.select(sel).append("svg")
            .attr("viewBox", "0 0 " + (width + margin.left + margin.right) + " " + (height + margin.top + margin.bottom))
            .append("g")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

        var x = d3.scalePoint().range([0, width]),
            y = d3.scaleLinear().range([height, 0]),
            z = d3.scaleOrdinal(d3.schemeSet2);

        var line1 = d3.line()
            .curve(d3.curveCatmullRom)
            .x(function (d) {return x(d.tournament);})
            .y(function (d) {return y(d.used);});

        d3.json(url).then(function (data) {

            var series = data.data;

            x.domain([...new Set(series.map(c => c.values.map(t => t.tournament)).flat(1))]);
            y.domain([0,
                d3.max(series, c => d3.max(c.values, function (d) {return d.used;}))
            ]);

            svg.append("g")
                .attr("class", "axis axis--x")
                .attr("transform", "translate(0," + height + ")")
                .call(d3.axisBottom(x));

            svg.append("g")
                .attr("class", "axis axis--y")
                .call(d3.axisLeft(y))
                .append("text")
                .attr("transform", "rotate(-90)")
                .attr("y", 6)
                .attr("dy", "0.71em")
                .attr("fill", "#ffffff")
                .style('font-size', '1.6em')
                .text("fielded");

            var shipClass = svg.selectAll(".shipClass")
                .data(series)
                .enter().append("g")
                .attr("class", "shipClass");

            shipClass.append("path")
                .attr("class", "line")
                .attr('fill', 'none')
                .attr('stroke', 'steelblue')
                .attr('stroke-width', '6px')
                .attr("d", d => line1(d.values))
                .style("stroke", d => z(d.id));

            shipClass.append("text")
                .datum(d => ({id: d.id, value: d.values[d.values.length - 1]}))
                .attr("transform", d => "translate(" + (4 + x(d.value.tournament)) + "," + y(d.value.used) + ")")
                .attr("x", 3)
                .attr("dy", "0.35em")
                .attr("fill", d => z(d.id))
                .style('font-size', '1.6em')
                .text(d => d.id);
        });
    }

    window.streamInitLineChart = init;

})();
