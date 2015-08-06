function LineChart(divID, line_functions, data) {
	var self = this;
	var vis = d3.select(divID);
	var margin = {
			top: 20,
			right: 20,
			bottom: 20,
			left: 50
	};
	var width = 800 - margin.left - margin.right;
	var height = 250 - margin.top - margin.bottom;
	var x = d3.scale.linear().range([0, width]);
	var y = d3.scale.linear().range([height, 0]);
	var xAxis = d3.svg.axis()
		.scale(x)
		.orient("bottom");
	var yAxis = d3.svg.axis()
		.scale(y)
		.orient("left");

	var lines = $.map(line_functions, function(line_function) {
		return d3.svg.line()
			.x(function (d) {
				return x(line_function.x(d));
			})
			.y(function (d) {
				return y(line_function.y(d));
			})
	});

	var svg = d3.select(divID).append("svg")
		.attr("width", width + margin.left + margin.right)
		.attr("height", height + margin.top + margin.bottom)
		.append("g")
		.attr("transform", "translate(" + margin.left + "," + margin.top + ")");

	svg.append("g")
		.attr("class", "x axis")
		.attr("transform", "translate(0," + height + ")")
		.call(xAxis);
	svg.append("g")
		.attr("class", "y axis")
		.call(yAxis)
		.append("text")
		.attr("transform", "rotate(-90)")
		.attr("y", 6)
		.attr("dy", ".71em")
		.style("text-anchor", "end");

	$.each(lines, function(index) {
		svg.append("path")
			.attr("class", "line line" + (index + 1))
			.attr("d", lines[index](data))
			.attr("data-index", index);
	});


	var hoverLineGroup = svg.append("svg:g")
							.attr("class", "hover-line");
	var hoverLine = hoverLineGroup
						.append("svg:line")
						.attr("x1", 10).attr("x2", 10)
						.attr("y1", 0).attr("y2", height);
	var container = document.querySelector(divID);

	var hoverLineXOffset, hoverLineYOffset;

	this.on_hover_change = function(index) {};
	this.set_hover = function(index) {
		var posX = x(index + line_functions[0].x(data[0]));
		hoverLine.attr("x1", posX).attr("x2", posX);
	};

	$(container).mousemove(function(event) {
		var mouseX = event.pageX - hoverLineXOffset;
		var mouseY = event.pageY - hoverLineYOffset;
		if(mouseX >= 0 && mouseX <= width && mouseY >= 0 && mouseY <= height) {
			hoverLine.attr("x1", mouseX).attr("x2", mouseX);
			var index = self.getIndexFromPosition(mouseX);
			self.on_hover_change(index);
		}
	});


	this.on_resize = function() {
		hoverLineXOffset = margin.left + $(container).offset().left;
		hoverLineYOffset = margin.top + $(container).offset().top;
		width = $(divID).width() - margin.left - margin.right;
		height = $(divID).height() - margin.top - margin.bottom;
		d3.select(divID + " svg").attr("width", width + margin.left + margin.right)
								 .attr("height", height + margin.top + margin.bottom)
		hoverLine.attr("y2", height);
		x = d3.scale.linear().range([0, width]);
		y = d3.scale.linear().range([height, 0]);
		self.set_axis_domain();
		xAxis = d3.svg.axis()
			.scale(x)
			.orient("bottom");
		yAxis = d3.svg.axis()
			.scale(y)
			.orient("left");
		svg.selectAll("g .x.axis").call(xAxis)
			.attr("transform", "translate(0," + height + ")");
		svg.selectAll("g .y.axis").call(yAxis);
		self.update_chart();
	};

	this.set_axis_domain = function() {
		var xvars = d3.extent(data, line_functions[0].x);
		var yvars = d3.extent(data, line_functions[0].y);
		$.map(line_functions, function(line_function) {
			var xvars_ = d3.extent(data, line_function.x);
			var yvars_ = d3.extent(data, line_function.y);
			xvars[0] = Math.min(xvars_[0], xvars[0]);
			xvars[1] = Math.max(xvars_[1], xvars[1]);
			yvars[0] = Math.min(yvars_[0], yvars[0]);
			yvars[1] = Math.max(yvars_[1], yvars[1]);
		})
		x.domain(xvars);
		y.domain(yvars);
	}

	this.update_chart = function() {
		self.set_axis_domain();
		var svg = d3.select(divID).transition();
		svg.selectAll("g .line")
			.duration(750)
			.attr("d", function() {
				return lines[$(this).attr("data-index")](data);
			});
		svg.select(".x.axis")
			.duration(750)
			.call(xAxis);
		svg.select(".y.axis")
			.duration(750)
			.call(yAxis);
	};

	this.getIndexFromPosition = function(xPosition) {

		// get the date on x-axis for the current location
		var xValue = x.invert(xPosition);

		// Calculate the value from this date by determining the 'index'
		// within the data array that applies to this value
		var index = Math.round(xValue - line_functions[0].x(data[0]));
		index = Math.max(0, Math.min(data.length-1, index));
		return index;
	};

	this.get = function() {
		return {
			x: x,
			y: y,
			xAxis: xAxis,
			yAxis: yAxis,
			svg: svg,
			lines: lines,
			data: data,
			line_functions: line_functions
		};
	};

	self.on_resize();
}
