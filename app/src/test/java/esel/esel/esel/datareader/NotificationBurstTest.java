package esel.esel.esel.datareader;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NotificationBurstTest {
    private static final long NOW = 1_250L;

    @Test
    public void newerReadingReplacesPendingReadingWithoutRestartingWindow() {
        SettledNotificationReader.NotificationBurst burst = new SettledNotificationReader.NotificationBurst();
        SGV previousValue = new SGV(157, 1_000L, 0);
        SGV updatedValue = new SGV(158, 1_246L, 0);

        assertTrue(burst.offerAndStartWindow(previousValue, NOW));
        assertFalse(burst.offerAndStartWindow(updatedValue, NOW));

        assertSame(updatedValue, burst.take());
        assertNull(burst.take());
        assertTrue(burst.offerAndStartWindow(new SGV(121, NOW, 0), NOW));
    }

    @Test
    public void lateDeliveryOfAnOlderNotificationDoesNotReplaceTheUpdate() {
        SettledNotificationReader.NotificationBurst burst = new SettledNotificationReader.NotificationBurst();
        SGV updatedValue = new SGV(118, 1_246L, 0);

        assertTrue(burst.offerAndStartWindow(updatedValue, NOW));
        assertFalse(burst.offerAndStartWindow(new SGV(110, 1_000L, 0), NOW));

        assertSame(updatedValue, burst.take());
    }

    @Test
    public void laterCallbackWinsWhenTheNotificationTimestampIsIdentical() {
        SettledNotificationReader.NotificationBurst burst = new SettledNotificationReader.NotificationBurst();
        SGV updatedValue = new SGV(118, 1_000L, 0);

        assertTrue(burst.offerAndStartWindow(new SGV(110, 1_000L, 0), NOW));
        assertFalse(burst.offerAndStartWindow(updatedValue, NOW));

        assertSame(updatedValue, burst.take());
    }

    @Test
    public void repeatedNotificationsDoNotRestartWindow() {
        SettledNotificationReader.NotificationBurst burst = new SettledNotificationReader.NotificationBurst();
        SGV reading = new SGV(120, NOW, 0);

        assertTrue(burst.offerAndStartWindow(reading, NOW));
        assertFalse(burst.offerAndStartWindow(reading, NOW + 1_000L));
        assertSame(reading, burst.take());
    }

    @Test
    public void futureReadingCannotStartWindowOrReplaceValidReading() {
        SettledNotificationReader.NotificationBurst burst = new SettledNotificationReader.NotificationBurst();
        SGV future = new SGV(110, NOW + 3_600_000L, 0);
        SGV valid = new SGV(120, NOW, 0);

        assertFalse(burst.offerAndStartWindow(future, NOW));
        assertTrue(burst.offerAndStartWindow(valid, NOW));
        assertFalse(burst.offerAndStartWindow(future, NOW));
        assertSame(valid, burst.take());
    }

    @Test
    public void invalidValueCannotStartWindowOrReplaceValidReading() {
        SettledNotificationReader.NotificationBurst burst = new SettledNotificationReader.NotificationBurst();
        SGV invalid = new SGV(-1, NOW, 0);
        SGV valid = new SGV(120, NOW - 250L, 0);

        assertFalse(burst.offerAndStartWindow(invalid, NOW));
        assertTrue(burst.offerAndStartWindow(valid, NOW));
        assertFalse(burst.offerAndStartWindow(invalid, NOW));
        assertSame(valid, burst.take());
    }

    @Test
    public void changedValueInSeparateBurstCanBypassTheFourMinuteRule() {
        SGV updated = new SGV(130, 56_357L, 0);

        assertTrue(SettledNotificationReader.canForward(updated, 1_000L, 126));
        assertTrue(SettledNotificationReader.hasChangedValue(updated, 126));
    }

    @Test
    public void unchangedValueKeepsItsRepeatInterval() {
        SGV repost = new SGV(126, 245_557L, 0);
        SGV nextReading = new SGV(126, 301_000L, 0);

        assertFalse(SettledNotificationReader.canForward(repost, 1_000L, 126));
        assertTrue(SettledNotificationReader.canForward(nextReading, 1_000L, 126));
        assertFalse(SettledNotificationReader.hasChangedValue(repost, 126));
    }

    @Test
    public void changedValueStillNeedsANewerTimestamp() {
        assertFalse(SettledNotificationReader.canForward(new SGV(130, 999L, 0), 1_000L, 126));
        assertFalse(SettledNotificationReader.canForward(new SGV(130, 1_000L, 0), 1_000L, 126));
    }

    @Test
    public void unknownPreviousValueDoesNotBypassTheFourMinuteRule() {
        SGV reading = new SGV(130, 2_000L, 0);

        assertTrue(SettledNotificationReader.canForward(reading, 1_000L, -1));
        assertFalse(SettledNotificationReader.hasChangedValue(reading, -1));
    }
}
