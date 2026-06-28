package qualet.irlite.client.ui.forms.editors.panels;

import java.lang.reflect.Constructor;
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
 * value is always assignable to our {@code UITrackpad} fields and {@code .limit(...)} chaining works
 * unchanged.</p>
 */
public final class IrliteTrackpads
{
    /** The Refreshed slider adapter's {@code (Consumer<Double>)} constructor, or null when unavailable. */
    private static final Constructor<?> ADAPTER_CTOR = resolveAdapter();

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
}
