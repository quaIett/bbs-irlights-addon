package qualet.irlite.mixin.client;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.ui.replays.LightKeyframeRanges;
import qualet.irlite.client.ui.replays.UILightFloatKeyframeFactory;

/** Scope the slider to a light's actual property; identical names on other forms
 * keep BBS's (or another addon's) normal factory and value type. */
@Mixin(UIKeyframeFactory.class)
public abstract class LightKeyframeEditorMixin
{
    @Inject(method = "createPanel", at = @At("HEAD"), cancellable = true)
    private static void irlite$boundedLightKey(Keyframe<Float> keyframe, UIKeyframes editor,
        CallbackInfoReturnable<UIKeyframeFactory<Float>> cir)
    {
        if (editor == null || keyframe.getFactory() != KeyframeFactories.FLOAT) return;
        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);
        LightKeyframeRanges.Range range = sheet == null || sheet.isBoneTrack ? null : LightKeyframeRanges.of(sheet.property);
        if (range != null) cir.setReturnValue(new UILightFloatKeyframeFactory(keyframe, editor, range));
    }
}
