package org.wemppy.irlite.client.light;

/** Per-frame relative-render origin, set by the film controller for relative replays. */
public final class IRLightRenderState
{
    private static boolean relativeRender;
    private static double originX;
    private static double originY;
    private static double originZ;

    private IRLightRenderState()
    {}

    public static void beginFrame()
    {
        relativeRender = false;
    }

    public static void setRelativeRender(double x, double y, double z)
    {
        relativeRender = true;
        originX = x;
        originY = y;
        originZ = z;
    }

    public static void clearRelativeRender()
    {
        relativeRender = false;
    }

    public static boolean isRelativeRender()
    {
        return relativeRender;
    }

    public static double getOriginX()
    {
        return originX;
    }

    public static double getOriginY()
    {
        return originY;
    }

    public static double getOriginZ()
    {
        return originZ;
    }
}
