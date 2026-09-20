package qualet.irlite.client.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import qualet.irlite.forms.SpotlightForm;

/** Focused checks against real BBS tracks, serialization and playback; no game bootstrap. */
public final class SpotGuideKeyframesTest
{
    private static int checks;

    private static void check(boolean condition, String message)
    {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        /* BBS 2.7 split the one "pose and transform overlays" setting in two. */
        BBSSettings.recordingPoseOverlays = new ValueInt("test", 0);
        BBSSettings.recordingTransformOverlays = new ValueInt("test", 0);
        KeyframeFactories.setup();

        SpotlightForm root = new SpotlightForm();

        for (ValueFloat property : new ValueFloat[] {root.range, root.radius, root.innerRadius})
        {
            FormProperties properties = new FormProperties("properties");
            TrackId track = TrackId.parse(FormUtils.getPropertyPath(property));
            check(SpotGuideKeyframes.begin(properties, property, 10, false) == null, "auto off: missing track refused");
            SpotGuideKeyframes gesture = SpotGuideKeyframes.begin(properties, property, 10, true);
            gesture.finish();
            check(properties.get(track) == null, "click alone creates no track");

            BaseType[] before = {null};
            boolean[] ended = {false};
            properties.preCallback((value, flag) ->
            {
                if (value == properties && before[0] == null) before[0] = properties.toData();
                ended[0] |= (flag & IValueListener.FLAG_UNMERGEABLE) != 0;
            });
            gesture.write(30F, 10);
            gesture.write(35F, 10);
            gesture.finish();
            KeyframeChannel<Float> channel = properties.get(track);
            check(channel.getKeyframes().size() == 1 && channel.get(0).getTick() == 10, "first key at playhead, no duplicates");
            check(channel.get(0).getValue() == 35F, "same frame receives final drag value");
            check(ended[0], "gesture closes undo merge");
            BaseType after = properties.toData();
            properties.fromData(before[0]);
            check(properties.get(track) == null, "before snapshot removes newly created track");
            properties.fromData(after);
            properties.applyProperties(root, 10);
            check(property.get() == 35F, "saved key drives correct property");

            channel = properties.get(track);
            channel.get(0).getInterpolation().setInterp(Interpolations.CONST);
            channel.insert(30, 65F);
            gesture = SpotGuideKeyframes.begin(properties, property, 20, true);
            gesture.write(45F, 20);
            gesture.write(46F, 20);
            gesture.finish();
            check(channel.getKeyframes().size() == 3, "auto key inserted between neighbours only once");
            check(channel.get(0).getValue() == 35F && channel.get(2).getValue() == 65F, "neighbour values preserved");
            check(channel.get(1).getInterpolation().has(Interpolations.CONST), "new key inherits interpolation");
            gesture = SpotGuideKeyframes.begin(properties, property, 25, false);
            gesture.write(50F, 25);
            gesture.finish();
            check(channel.getKeyframes().size() == 3 && channel.get(1).getValue() == 50F, "auto off edits governing key");

            FormProperties emptyProperties = new FormProperties("empty");
            channel = emptyProperties.create(property);
            check(SpotGuideKeyframes.begin(emptyProperties, property, 7, false) == null, "auto off: empty channel refused");
            gesture = SpotGuideKeyframes.begin(emptyProperties, property, 7, true);
            gesture.write(20F, 7);
            gesture.write(25F, 8);
            gesture.finish();
            check(channel.getKeyframes().size() == 2 && channel.get(1).getTick() == 8, "auto key follows advancing playhead");
        }
        System.out.println("Spot guide keyframes PASS: " + checks + " checks");
    }
}
