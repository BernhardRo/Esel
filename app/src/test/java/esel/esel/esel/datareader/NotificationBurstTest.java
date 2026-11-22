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
        SGV previousValue = new SGV(110, 1_000L, 0);
        SGV updatedValue = new SGV(118, 1_246L, 0);

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
    public void replayedValueAfterRestartDoesNotDisplaceNextReading() {
        SGV lastAccepted = new SGV(126, 1_000L, 0);
        SGV repost = new SGV(126, 245_557L, 0);
        SGV updated = new SGV(130, 300_914L, 0);

        assertFalse(SettledNotificationReader.isNewReading(repost, lastAccepted));
        assertTrue(SettledNotificationReader.isNewReading(updated, lastAccepted));
        assertTrue(SettledNotificationReader.isNewReading(new SGV(126, 301_000L, 0), lastAccepted));
    }
}
