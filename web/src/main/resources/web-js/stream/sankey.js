(function () {

    function init(sel) {

        var margin = {top: 10, right: 10, bottom: 10, left: 10},
            width = 1400 - margin.left - margin.right,
            height = 900 - margin.top - margin.bottom;

        var formatNumber = d3.format(",.0f"), // zero decimal places
            format = function (d) {
                return formatNumber(d);
            },
            color = d3.scaleOrdinal(d3.schemeCategory10);

        var svg = d3.select(sel).append("svg")
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.top + margin.bottom)
            .append("g")
            .attr("transform",
                "translate(" + margin.left + "," + margin.top + ")");

        var sankey = d3.sankey()
            .nodeSort((a, b) => {
                if (a.id.startsWith('AT') && b.id.startsWith('AT'))
                    return null;
                else
                    return a.height - b.height;
            })
            // .nodeAlign(d3.sankeyLeft)
            .nodeWidth(36)
            .nodePadding(2)
            .size([width, height]);

        d3.json("/stream/sankey/data").then(function (graph) {
            sankey(graph);

            svg.append("g")
                .attr("fill", "none")
                .attr("stroke-opacity", 0.6)
                .selectAll("path")
                .data(graph.links)
                .join("path")
                .attr("d", d3.sankeyLinkHorizontal())
                .attr("stroke-width", function (d) {
                    return d.width;
                })
                .attr('stroke', d => color(d.source.id));

            var node = svg.append('g')
                .selectAll('.node')
                .data(graph.nodes)
                .join('g')
                .attr('class', 'node')
                .attr("transform", function (d) {
                    return "translate(" + d.x0 + "," + d.y0 + ")";
                });

            node.append('rect')
                .attr("height", function (d) {
                    return d.y1 - d.y0;
                })
                .attr("width", sankey.nodeWidth())
                .attr("fill", function (d) {
                    return color(d.id);
                })
                .append("title")
                .text(function (d) {
                    return d.id + "\n" + d.value;
                });

            node.append("text")
                .attr('fill', 'white')
                .style('font-size', '1em')
                .attr("x", 4)
                .attr("y", function (d) {
                    return (d.y1 - d.y0) / 2;
                })
                .attr("dy", "0.35em")
                .attr("text-anchor", "start")
                .text(function (d) {
                    return d.id;
                    // + ": " + d.value;
                })
                .filter(function (d) {
                    return d.x0 > width / 2;
                })
                .attr("x", sankey.nodeWidth() - 4)
                .attr("text-anchor", "end");

        });

    }

    window.streamInitSankey = init;

})();
