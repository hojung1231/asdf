package com.hunger.log;

import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class MetricTest {
    private final long day = History.todayStart();
    private long hour(int hour) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(day);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
        return calendar.getTimeInMillis();
    }
    private JSONArray point(int hour, int level) { return new JSONArray().put(hour(hour)).put(6 - level); }

    @Test public void focusIgnoresNonWorkingHoursAndOtherDays() {
        JSONArray points = new JSONArray().put(new JSONArray().put(day - 3600000).put(5))
                .put(point(7, 1)).put(point(8, 6)).put(point(17, 6)).put(point(18, 1));
        assertEquals(100, Metric.FOCUS.score(points, day));
        assertEquals(2, Metric.FOCUS.scorePoints(points, day).length());
        assertEquals(-1, Metric.FOCUS.score(new JSONArray().put(point(7, 6)).put(point(18, 6)), day));
    }
    @Test public void focusScoreIsLinearAndTimeWeighted() {
        JSONArray points = new JSONArray().put(point(8, 6)).put(point(9, 1)).put(point(17, 1));
        assertEquals(6, Metric.FOCUS.score(points, day));
        assertEquals(0, Metric.FOCUS.score(new JSONArray().put(point(8, 1)), day));
        assertEquals(100, Metric.FOCUS.score(new JSONArray().put(point(8, 6)), day));
    }
    @Test public void stressIncludesAllHoursAndOlderRecords() {
        JSONArray points = new JSONArray().put(new JSONArray().put(day - 3600000).put(0))
                .put(point(7, 6)).put(point(18, 1));
        // 8 hours at level 6, then an 11-hour linear transition to level 1.
        assertEquals(29, Metric.STRESS.score(points, day));
        assertEquals(100, Metric.STRESS.score(new JSONArray().put(point(23, 1)), day));
        assertEquals(0, Metric.STRESS.score(new JSONArray().put(point(0, 6)), day));
    }
    @Test public void newMetricsUseLinearTransitionsInBothDirections() {
        JSONArray points = new JSONArray().put(point(8, 1)).put(point(9, 6));
        assertEquals(2.5, History.averages(points, Metric.FOCUS)[1], 0.0001);
        assertEquals(2.5, History.averages(points, Metric.STRESS)[1], 0.0001);
        assertEquals(5, History.averages(points, Metric.HUNGER)[1], 0.0001);
        assertEquals(-1, Metric.STRESS.score(new JSONArray(), day));
    }
    @Test public void sharedGraphUsesOneTimeAxisForAllThreeSeriesAndExcludesOlderDays() {
        JSONArray[] series = {
            new JSONArray().put(new JSONArray().put(day - 1).put(0)).put(new JSONArray().put(day + 1000).put(2)),
            new JSONArray().put(new JSONArray().put(day + 5000).put(0)),
            new JSONArray().put(new JSONArray().put(day + 3000).put(4))
        };
        long[] range = WidgetPlot.range(series);
        assertEquals(day + 1000, range[0]);
        assertEquals(day + 5000, range[1]);
        assertEquals(22, WidgetPlot.x(day + 1000, range, 300), .001);
        assertEquals(153, WidgetPlot.x(day + 3000, range, 300), .001);
        assertEquals(284, WidgetPlot.x(day + 5000, range, 300), .001);
    }
}
