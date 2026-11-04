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

    @Test public void emptyHistoryHasNoScore() {
        assertEquals(-1, History.todayScore(new JSONArray()));
    }

    @Test public void scoreIsLinearBetweenTwoAndFourAndClampedOutside() {
        assertEquals(100, History.scoreFromAverage(1));
        assertEquals(100, History.scoreFromAverage(2));
        assertEquals(75, History.scoreFromAverage(2.5));
        assertEquals(50, History.scoreFromAverage(3));
        assertEquals(25, History.scoreFromAverage(3.5));
        assertEquals(0, History.scoreFromAverage(4));
        assertEquals(0, History.scoreFromAverage(6));
    }

    @Test public void scoreUsesDisplayedLevelsRatherThanStoredIndexes() {
        assertEquals(100, History.todayScore(records(4)));
        assertEquals(50, History.todayScore(records(3)));
        assertEquals(0, History.todayScore(records(2)));
    }

    @Test public void increasingHungerUsesLinearIntegral() {
        assertEquals(2.5, History.averages(records(0, 5))[1], 0.0001);
        assertEquals(25, History.todayScore(records(0, 5)));
    }

    @Test public void mealUsesPreviousValueUntilMealTime() {
        assertEquals(3, History.averages(records(3, 0))[1], 0.0001);
        assertEquals(50, History.todayScore(records(3, 0)));
    }

    @Test public void averageAndScoreAreWeightedByElapsedTime() {
        JSONArray points = new JSONArray();
        points.put(new JSONArray().put(1000L).put(2));
        points.put(new JSONArray().put(61000L).put(4));
        points.put(new JSONArray().put(181000L).put(4));
        assertEquals(11.0 / 3, History.averages(points)[2], 0.0001);
        assertEquals(83, History.todayScore(points));
    }
    @Test public void editPreservesTimeAndPointCountAndRecomputesMean() {
        JSONArray points = records(3, 3);
        long time = points.optJSONArray(1).optLong(0);
        assertEquals(true, History.replaceValue(points, 1, time, 5));
        assertEquals(2, points.length());
        assertEquals(time, points.optJSONArray(1).optLong(0));
        assertEquals(4, History.averages(points)[1], 0.0001);
        assertEquals(100, History.todayScore(points));
    }

    @Test public void oldRowEditDoesNotOverwriteNewlyAppendedRecord() {
        JSONArray points = records(3, 3, 1);
        long time = points.optJSONArray(1).optLong(0);
        assertEquals(true, History.replaceValue(points, 1, time, 4));
        assertEquals(1, points.optJSONArray(2).optInt(1));
        assertEquals(false, History.replaceValue(points, 1, time + 1, 5));
        assertEquals(false, History.replaceValue(points, 1, time, 6));
    }
}