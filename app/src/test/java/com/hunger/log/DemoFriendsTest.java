package com.hunger.log;

import org.junit.Test;
import static org.junit.Assert.*;

public class DemoFriendsTest {
    @Test public void currentMessageAndFriendBarUseSameState() {
        for (long tick = 10000; tick < 10100; tick++) {
            int friend = DemoFriends.friend(tick);
            long now = tick * DemoFriends.INTERVAL + 100;
            assertEquals(tick, DemoFriends.latestTick(friend, now));
            assertEquals(DemoFriends.value(tick), DemoFriends.state(friend, now));
            assertTrue(DemoFriends.message(tick).startsWith(DemoFriends.NAMES[friend]));
        }
    }

    @Test public void eachFriendRetainsLastEventUntilNextTurn() {
        long now = 10001 * DemoFriends.INTERVAL;
        for (int friend = 0; friend < 3; friend++) {
            long latest = DemoFriends.latestTick(friend, now);
            assertTrue(latest <= DemoFriends.tick(now));
            assertTrue(DemoFriends.tick(now) - latest < 3);
            assertEquals(friend, DemoFriends.friend(latest));
            assertTrue(DemoFriends.state(friend, now) >= 0);
            assertTrue(DemoFriends.state(friend, now) < 6);
            assertEquals(DemoFriends.state(friend, now), DemoFriends.state(friend, now + 500));
        }
    }
}
