package org.wemppy.irlite.mixin.client;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wemppy.irlite.client.ui.patcher.UIPatcherSection;

@Mixin(UISettingsOverlayPanel.class)
public abstract class UISettingsOverlayPanelMixin
{
    @Shadow public UIScrollView options;
    @Shadow private ValueGroup category;
    @Shadow private String filter;

    @Shadow public abstract void refresh();

    @Inject(method = "refresh", at = @At("TAIL"))
    private void irlite$appendPatcher(CallbackInfo ci)
    {
        if (this.filter == null || !this.filter.isEmpty() || this.category == null)
        {
            return;
        }
        if (!"irlite".equals(this.category.getId()))
        {
            return;
        }

        UIPatcherSection.append(this.options, this::refresh);
        this.options.resize();
    }
}
