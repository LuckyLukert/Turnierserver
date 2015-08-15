// https://remysharp.com/2010/07/21/throttling-function-calls
function throttle(fn, threshhold, scope) {
	threshhold || (threshhold = 250);
	var last,
			deferTimer;
	return function () {
		var context = scope || this;
		var now = +new Date,
				args = arguments;
		if (last && now < last + threshhold) {
			// hold on to it
			clearTimeout(deferTimer);
			deferTimer = setTimeout(function () {
				last = now;
				fn.apply(context, args);
			}, threshhold);
		} else {
			last = now;
			fn.apply(context, args);
		}
	};
}


var pane = {
	step: 0,
	data: [],
	name: name,
	is_playing: false,
};

var data = [];

var diff_chart = new LineChart("#diff_chart",
	[{
		x: function (d) { return d.step; },
		y: function (d) { return d.diff; },
		label: function(d) { return "Schritt: " + d.step + " Diff: " + d.diff; }
	}], data
);

$("#spielspezifisch").on("click", diff_chart.on_resize);
$(window).on("resize", throttle(diff_chart.on_resize, 1000));
diff_chart.update_chart = throttle(diff_chart.update_chart, 750);

var gain_chart = new LineChart("#gain_chart",
	[{
		x: function (d) { return d.step; },
		y: function (d) { return d.ai1_gain; },
		ylabel: function(d) { return d.ai1_name; },
		xlabel: function(d) { return d.step; }
	},
	{
		x: function (d) { return d.step; },
		y: function (d) { return d.ai2_gain; },
		ylabel: function(d) { return d.ai2_name; }
	}], data
);

$("#spielspezifisch").on("click", gain_chart.on_resize);
$(window).on("resize", throttle(gain_chart.on_resize, 1000));
gain_chart.update_chart = throttle(gain_chart.update_chart, 750);

var td_chart = new LineChart("#td_chart",
	[{
		x: function (d) { return d.step; },
		y: function (d) { return d.ai1_td; },
		label: function() { return "Ai1"; }
	},
	{
		x: function (d) { return d.step; },
		y: function (d) { return d.ai2_td; },
		label: function() { return "Ai2"; }
	}], data
);

$("#rechenpunkte").on("click", td_chart.on_resize);
$(window).on("resize", throttle(td_chart.on_resize, 1000));
td_chart.update_chart = throttle(td_chart.update_chart, 750);

var tabs_chart = new LineChart("#tabs_chart",
	[{
		x: function (d) { return d.step; },
		y: function (d) { return d.ai1_tabs; },
		label: function() { return "Ai1"; }
	},
	{
		x: function (d) { return d.step; },
		y: function (d) { return d.ai2_tabs; },
		label: function() { return "Ai2"; }
	}], data
);

$("#rechenpunkte").on("click", tabs_chart.on_resize);
$(window).on("resize", throttle(tabs_chart.on_resize, 1000));
tabs_chart.update_chart = throttle(tabs_chart.update_chart, 750);

var charts = [diff_chart, gain_chart, td_chart, tabs_chart];

function on_hover_change(index) {
	pane.step = index;
	$("#step_slider").slider("option", "value", index);
	draw();
	$.each(charts, function () {
		this.set_hover(index);
	});
}

$.map(charts, function(chart) {
	chart.on_hover_change = on_hover_change;
});

function draw() {
	update();
	//var d = pane.data[pane.step];
}

$("#step_slider").slider({
	range: "max",
	min: 0,
	max: 0,
	value: 0,
	step: 1,
	slide: function (event, ui) {
		pane.step = ui.value;
		draw();
		$.map(charts, function(chart) {
			chart.set_hover(pane.step);
		});
	}
});


function update() {
	var d = pane.data[pane.step];
	$.map(d.output, function(value, key) {
		var id = key.slice(0, key.indexOf("v"));
		if(aiCrash[id].crashed && aiCrash[id].step == pane.step) {
			if(!$("#ai_" + id + "_output").hasClass("crash")) {
				$("#ai_" + id + "_output").addClass("crash");
			}
			$("#ai_" + id + "_output").val(aiCrash[id].message);
		}
		else {
			if($("#ai_" + id + "_output").hasClass("crash")) {
				$("#ai_" + id + "_output").removeClass("crash");
			}
			$("#ai_" + id + "_output").val(value);
		}
	});

	if (pane.is_playing) {
		$("#play_button").addClass("active");
		$("#pause_button").removeClass("active");
	} else {
		$("#play_button").removeClass("active");
		$("#pause_button").addClass("active");
	}
}

function map_sorted(obj, func) {
	var sorted_keys = $.map(obj, function(element,index) {return index}).sort();
	return $.map(sorted_keys, function (key) {
		return func(key, obj[key]);
	});
}

function return_val(key, value) {
	return value;
}

$(document).ready(function () {
	console.log("Streaming game data from", window.location.origin + $("#game_script").attr("stream"));
	var evtSrc = new EventSource(window.location.origin + $("#game_script").attr("stream"));
	NProgress.configure({ trickle: false });
	NProgress.start();

	evtSrc.onerror = function () {
		console.log("SSE Err");
		evtSrc.close();
		NProgress.done();
	};

	evtSrc.addEventListener("state", function(e) {
		d = JSON.parse(e.data);
		NProgress.set(d.progress);
		console.log(d);
		pane.data.push(d);
		//NProgress.set(d.progress);
		$("#step_slider").slider("option", "max", pane.data.length-1);
		var values = map_sorted(d.wonChips, return_val);
		var calculationPoints = map_sorted(d.calculationPoints, return_val);
		var labels = map_sorted(d.calculationPoints, function(key, value) {
			var id = key.slice(0, key.indexOf("v"));
			return ais[id];
		});
		var d = {};
		d.diff = Math.abs(values[0] - values[1]);
		d.ai1_abs = values[0];
		d.ai2_abs = values[1];
		d.ai1_gain = values[0];
		d.ai2_gain = values[1];
		if (data.length > 0) {
			d.ai1_gain -= data[data.length-1].ai1_abs;
			d.ai2_gain -= data[data.length-1].ai2_abs;
		}
		d.ai1_tabs = calculationPoints[0];
		d.ai2_tabs = calculationPoints[1];
		d.ai1_td = 0;
		d.ai2_td = 0;
		if (data.length > 0) {
			d.ai1_td = (data[data.length-1].ai1_tabs - calculationPoints[0]);
			d.ai2_td = (data[data.length-1].ai2_tabs - calculationPoints[1]);
		}

		d.ai1_name = labels[0];
		d.ai2_name = labels[1];

		d.step = pane.data.length;
		data.push(d);
		$.map(charts, function (chart) {
			chart.update_chart();
		});
		draw();
	});

	evtSrc.addEventListener("crash", function (e) {
		d = JSON.parse(e.data);
		aiCrash[d.ai].crashed = true;
		aiCrash[d.ai].crashStep = d.step;
		aiCrash[d.ai].crashMessage = d.message;
		if(pane.step == d.step) {
			var output = $("ai_" + d.ai + "_output");
			if(!ouput.hasClass("crash")) {
				output.addClass("crash");
			}
			output.val(d.message);
		}
	});

	evtSrc.addEventListener("stream_stopped", function (e) {
		console.log("stream_stopped");
		evtSrc.close();
		NProgress.done();
	});

	// ## in ne generelle lib verschieben
	evtSrc.addEventListener("game_finished", function(e) {
		console.log("game_finished", e.data);
		$("#finished_message").show();
		$("#finished_message").find("a").attr("href", e.data);
	});

	// ## in ne generelle lib verschieben
	evtSrc.addEventListener("qualified", function(e) {
		$("#qualified_message").show();
		NProgress.done();
	});

	// ## in ne generelle lib verschieben
	evtSrc.addEventListener("failed", function(e) {
		$("#failed_message").show();
		NProgress.done();
	});

	evtSrc.addEventListener("finished_transmitting", function(e) {
		console.log("finished_transmitting");
		NProgress.done();
	});
});
