package com.hunger.log;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeightHistoryTest {
    @Test public void weightNormalizesDecimalSeparatorsAndZeros() {
        assertEquals("72.5", WeightHistory.normalize(" 072.50 "));
        assertEquals("72.5", WeightHistory.normalize("72,5"));
        assertEquals("0.25", WeightHistory.normalize("0.25"));
    }

    @Test public void invalidValuesAreRejected() {
        for (String value : new String[]{"", "0", "-72", "NaN", "1e2", "1000", "72.555", "72..5"}) {
            assertNull(value, WeightHistory.normalize(value));
        }
    }

    @Test public void dayFilterIncludesStartAndExcludesNextDay() {
        org.json.JSONArray points = new org.json.JSONArray();
        points.put(new org.json.JSONArray().put(999L).put(0));
        points.put(new org.json.JSONArray().put(1000L).put(4));
        points.put(new org.json.JSONArray().put(2000L).put(2));
        org.json.JSONArray daily = History.inPeriod(points, 1000L, 2000L);
        assertEquals(1, daily.length());
        assertEquals(100, History.todayScore(daily));
        assertEquals(0, History.inPeriod(points, 2001L, 3000L).length());
    }

    @Test public void dailyScoreUsesOnlyThatDaysValues() {
        org.json.JSONArray points = new org.json.JSONArray();
        points.put(new org.json.JSONArray().put(1000L).put(0));
        points.put(new org.json.JSONArray().put(2000L).put(0));
        points.put(new org.json.JSONArray().put(3000L).put(4));
        points.put(new org.json.JSONArray().put(4000L).put(4));
        assertEquals(0, History.todayScore(History.inPeriod(points, 1000L, 3000L)));
        assertEquals(100, History.todayScore(History.inPeriod(points, 3000L, 5000L)));
    }
}
