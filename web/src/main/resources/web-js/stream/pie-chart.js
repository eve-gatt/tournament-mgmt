(function () {

    function init(sel, url) {
        var margin = {top: 0, right: 0, bottom: 0, left: 0},
            width = 1000 - margin.left - margin.right,
            height = 600 - margin.top - margin.bottom;

        var svg = d3.select(sel).append("svg")
            .attr("viewBox", [-width / 2, -height / 2, width, height])
            .append("g")
            .attr("transform",
                "translate(" + margin.left + "," + margin.top + ")");

        var arc = d3.arc()
            .innerRadius(0)
            .outerRadius(Math.min(width, height) / 2 - 1);

        const radius = Math.min(width, height) / 2 * 0.8;
        var arcLabel = d3.arc().innerRadius(radius).outerRadius(radius);

        var pie = d3.pie()
            .sort(null)
            .value(d => d.value);

        d3.json(url).then(function (inData) {
            const data = Object.entries(inData.data)
                .map(([name, value]) => ({name, value}))
                .sort((a, b) => d3.descending(a.value, b.value))
                .slice(0, 10);

            var color = d3.scaleOrdinal()
                .domain(data.map(d => d.name))
                .range(d3.quantize(t => d3.interpolateSpectral(t * 0.8 + 0.1), data.length).reverse());

            const arcs = pie(data);

            svg.append("g")
                .attr("stroke", "white")
                .selectAll("path")
                .data(arcs)
                .join("path")
                .attr("fill", d => color(d.data.name))
                .attr("d", arc)
                .append("title")
                .text(d => `${d.data.name}: ${d.data.value.toLocaleString()}`);

            svg.append("g")
                .attr("font-size", 12)
                .attr("text-anchor", "middle")
                .selectAll("text")
                .data(arcs)
                .join("text")
                .attr("transform", d => `translate(${arcLabel.centroid(d)})`)
                .call(text => text.append("tspan")
                    .attr("y", "-0.4em")
                    .attr("font-weight", "bold")
                    .text(d => d.data.name))
                .call(text => text.filter(d => (d.endAngle - d.startAngle) > 0.25).append("tspan")
                    .attr("x", 0)
                    .attr("y", "0.7em")
                    .attr("fill-opacity", 0.7)
                    .text(d => d.data.value.toLocaleString()));

        });
    }

    window.streamInitPieChart = init;

})();
