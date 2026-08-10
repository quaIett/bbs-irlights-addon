package qualet.irlite.client.ui.forms.editors.panels;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Factory for the numeric trackpad widgets used by IRLite's light form panels.
 *
 * <p>When the "Refreshed" UI addon ({@code refreshedui}) is present, it ships
 * {@code org.qualet.refreshedui.client.ui.UISliderTrackpadAdapter} — a {@link UITrackpad} subclass
 * that renders as a slider when given a finite bounded range. Refreshed swaps it into BBS's own
 * panels via {@code @Redirect} mixins, but those mixins do not target our panels, so we opt in here.</p>
 *
 * <p>We resolve the adapter reflectively (no compile/build dependency on Refreshed): if the mod is
 * absent — or anything about the adapter changes and instantiation fails — {@link #create(Consumer)}
 * falls back to a plain {@link UITrackpad}. The adapter {@code extends UITrackpad}, so the returned
 * value is always assignable to our {@code UITrackpad} fields.</p>
 *
 * <p>Ranges go through {@link #create(Consumer, double, double)} rather than a direct
 * {@code .limit(min, max)} call: BBS 2.4 moved {@code limit()} up into the new generic
 * {@code UINumericInput<T>} superclass, so its erased descriptor returns {@code UINumericInput}
 * instead of {@code UITrackpad}. A compiled {@code .limit(...)} call site therefore binds to a
 * method that only exists on the BBS build it was compiled against and dies with
 * {@code NoSuchMethodError} on the other one. Reflection binds by name at runtime and works on
 * both.</p>
 */
public final class IrliteTrackpads
{
    /** The Refreshed slider adapter's {@code (Consumer<Double>)} constructor, or null when unavailable. */
    private static final Constructor<?> ADAPTER_CTOR = resolveAdapter();

    /** {@code UITrackpad#limit(double, double)}, wherever the host BBS build declares it. */
    private static final Method LIMIT = resolveLimit();

    private IrliteTrackpads()
    {
    }

    private static Constructor<?> resolveAdapter()
    {
        if (!FabricLoader.getInstance().isModLoaded("refreshedui"))
        {
            return null;
        }

        try
        {
            Class<?> adapter = Class.forName("org.qualet.refreshedui.client.ui.UISliderTrackpadAdapter");

            return adapter.getDeclaredConstructor(Consumer.class);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static Method resolveLimit()
    {
        try
        {
            // getMethod() walks up the hierarchy, so this finds limit() whether UITrackpad declares
            // it itself (BBS 2.3.x) or inherits it from UINumericInput (BBS 2.4+).
            return UITrackpad.class.getMethod("limit", double.class, double.class);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Creates a trackpad: the Refreshed slider adapter when available, otherwise a plain BBS trackpad.
     * The callback contract is identical to {@code new UITrackpad(callback)}.
     */
    public static UITrackpad create(Consumer<Double> callback)
    {
        if (ADAPTER_CTOR != null)
        {
            try
            {
                return (UITrackpad) ADAPTER_CTOR.newInstance(callback);
            }
            catch (Throwable ignored)
            {
                // Fall through to the stock trackpad if the adapter cannot be instantiated.
            }
        }

        return new UITrackpad(callback);
    }

    /**
     * Creates a trackpad clamped to {@code [min, max]}, equivalent to {@code create(callback).limit(min, max)}
     * but binding {@code limit()} at runtime so the same jar works across BBS builds. If {@code limit()}
     * cannot be called, the trackpad stays unclamped rather than taking down the form editor.
     */
    public static UITrackpad create(Consumer<Double> callback, double min, double max)
    {
        UITrackpad trackpad = create(callback);

        if (LIMIT != null)
        {
            try
            {
                LIMIT.invoke(trackpad, min, max);
            }
            catch (Throwable ignored)
            {
            }
        }

        return trackpad;
    }
}
