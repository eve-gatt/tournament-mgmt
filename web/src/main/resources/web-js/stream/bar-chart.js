(function () {

    function init(sel, url) {
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
                .range([0, height])
                .padding(0.3);

        d3.json(url).then(function (inData) {

            let data = Object.entries(inData.data)
                .map(([name, value]) => ({name, value}))
                .sort((a, b) => d3.descending(a.value, b.value))
                .slice(0, 10);

            x.domain([0, d3.max(data, function (d) {return d.value;})]).nice();
            y.domain(data.map(d => d.name));
            var color = d3.scaleOrdinal()
                .range(d3.quantize(t => d3.interpolateSpectral(t * 0.8 + 0.1), data.length).reverse())
                .domain(data.map(d => d.name))

            svg.append("g")
                .selectAll("rect")
                .data(data)
                .enter().append("rect")
                .attr("class", "bar")
                .attr("fill", function (d) { return color(d.name); })
                .attr("y", function (d) {return y(d.name);})
                .attr("height", y.bandwidth())
                .attr("x", 0)
                .attr("width", function (d) {
                    return x(d.value);
                })
                .append('title')
                .text(d => d.name + ' - ' + d.value);

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
                .text("count")
                .style('font-size', '2em')
                .attr("transform", "translate(" + (-width) + ",-40)");
        });
    }

    window.streamInitBarChart = init;

})();
