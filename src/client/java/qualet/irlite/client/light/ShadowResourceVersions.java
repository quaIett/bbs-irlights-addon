package qualet.irlite.client.light;

import java.util.concurrent.atomic.AtomicLong;

public final class ShadowResourceVersions
{
    private static final AtomicLong NEXT = new AtomicLong();
    private static volatile long reload = next();

    private ShadowResourceVersions() {}
    public static long next() { return NEXT.incrementAndGet(); }
    public static long reloadVersion() { return reload; }
    public static void reloaded() { reload = next(); }
}
