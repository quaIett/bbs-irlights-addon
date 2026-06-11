package org.wemppy.irlite.mixin.client;

import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Colors the keyframe track headers for IRLite light forms (point light + spotlight).
 * Without this, none of the IRLite property ids are in UIReplaysEditor's COLORS map,
 * so every light track falls through to the default Colors.BLUE. These property ids are
 * unique to the IRLite forms, so overriding by name is safe across other BBS forms.
 */
@Mixin(UIReplaysEditor.class)
public class UIReplaysEditorColorMixin
{
    private static final Map<String, Integer> IRLITE$COLORS = new HashMap<>();

    static
    {
        /* brightness */
        IRLITE$COLORS.put("intensity", Colors.ORANGE);
        /* volumetric group */
        IRLITE$COLORS.put("beam_strength", Colors.CYAN);
        IRLITE$COLORS.put("vl_density", 0x33ccaa);        // teal
        IRLITE$COLORS.put("anisotropy", 0x66ccff);        // light blue
        /* shape / reach */
        IRLITE$COLORS.put("bulb_size", Colors.PINK);      // penumbra / softness
        IRLITE$COLORS.put("radius", Colors.GREEN);        // point reach / spot outer angle
        IRLITE$COLORS.put("range", 0x33ff88);             // spotlight reach
        IRLITE$COLORS.put("inner_radius", 0x88ff44);      // lime, spot inner angle
        /* light mask */
        IRLITE$COLORS.put("entities_only", Colors.DEEP_PINK);
        IRLITE$COLORS.put("blocks_only", 0xffaa33);       // amber
        /* shadows */
        IRLITE$COLORS.put("shadows", 0x9b6dff);           // purple
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private static void irlite$colorLightTracks(String key, CallbackInfoReturnable<Integer> cir)
    {
        Integer color = IRLITE$COLORS.get(StringUtils.fileName(key));

        if (color != null)
        {
            cir.setReturnValue(color);
        }
    }
}
