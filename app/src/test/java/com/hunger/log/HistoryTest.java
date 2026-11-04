package com.hunger.log;

import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HistoryTest {
    private JSONArray records(int... values) {
        JSONArray points = new JSONArray();
        long start = History.todayStart() + 1000;
        for (int i = 0; i < values.length; i++) {
            points.put(new JSONArray().put(start + i * 600000L).put(values[i]));
        }
        return points;
    }

    @Test public void firstRecordHasNoDurationScore() {
        assertEquals(-1, History.todayScore(records(3)));
    }

    @Test public void increasingHungerCountsLinearTimeInBand() {
        assertEquals(40, History.todayScore(records(0, 5)));
        assertEquals(2.5, History.averages(records(0, 5))[1], 0.0001);
    }

    @Test public void mealUsesPreviousValueUntilMealTime() {
        assertEquals(0, History.todayScore(records(5, 0)));
        assertEquals(100, History.todayScore(records(3, 0)));
        assertEquals(3, History.averages(records(3, 0))[1], 0.0001);
    }

    @Test public void constantValueInsideBandScoresFullDuration() {
        assertEquals(100, History.todayScore(records(2, 2, 2)));
        assertEquals(0, History.todayScore(records(1, 1)));
    }

    @Test public void previousDayDoesNotContributeToTodayScore() {
        JSONArray points = new JSONArray();
        points.put(new JSONArray().put(History.todayStart() - 600000L).put(3));
        points.put(new JSONArray().put(History.todayStart() + 600000L).put(3));
        assertEquals(-1, History.todayScore(points));
    }
}
