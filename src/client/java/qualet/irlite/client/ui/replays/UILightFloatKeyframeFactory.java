package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIFloatKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import qualet.irlite.client.ui.forms.editors.panels.IrliteTrackpads;

/** Keeps BBS numeric editing (auto keys, selection, undo, G and Bezier handles),
 * swapping only the value field for the same bounded slider used by our form panels. */
public final class UILightFloatKeyframeFactory extends UIFloatKeyframeFactory
{
    private final LightKeyframeRanges.Range range;

    public UILightFloatKeyframeFactory(Keyframe<Float> keyframe, UIKeyframes editor, LightKeyframeRanges.Range range)
    {
        super(keyframe, editor);
        this.range = range;

        UITrackpad previous = this.value;
        this.value = IrliteTrackpads.create((v) ->
        {
            Keyframe<Float> target = this.getEditTarget();
            this.setKeyframeValue(target, v);
            this.editor.getGraph().setValue(target.getValue(), true, true);
        }, range.min(), range.max());
        // Opening a legacy out-of-range key is read-only; only an edit clamps it.
        this.value.setValue(keyframe.getValue());
        this.scroll.addBefore(previous, this.value);
        previous.removeFromParent();
    }

    @Override
    protected void setKeyframeValue(Keyframe<Float> keyframe, double value)
    {
        super.setKeyframeValue(keyframe, this.range.clamp(value));
    }
}
