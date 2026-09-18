package qualet.irlite.client.forms;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

/** One guide gesture, writing into the replay rather than its rendered form copy. */
final class SpotGuideKeyframes
{
    private final FormProperties properties;
    private final ValueFloat property;
    private final TrackId track;
    private final boolean autoKeyframe;
    private final Keyframe<Float> existing;
    private boolean changed;

    private SpotGuideKeyframes(FormProperties properties, ValueFloat property, TrackId track,
                              boolean autoKeyframe, Keyframe<Float> existing)
    {
        this.properties = properties;
        this.property = property;
        this.track = track;
        this.autoKeyframe = autoKeyframe;
        this.existing = existing;
    }

    static SpotGuideKeyframes begin(FormProperties properties, ValueFloat property, int tick, boolean autoKeyframe)
    {
        TrackId track = TrackId.parse(FormUtils.getPropertyPath(property));

        if (track == null)
        {
            return null;
        }

        KeyframeChannel<Float> channel = properties.get(track);
        KeyframeSegment<Float> segment = channel == null ? null : channel.findSegment(tick);

        if (!autoKeyframe && segment == null)
        {
            return null;
        }

        return new SpotGuideKeyframes(properties, property, track, autoKeyframe, segment == null ? null : segment.a);
    }

    void write(float value, int tick)
    {
        /* Snapshot the group BEFORE creating a channel, so undo removes a newly
         * created track too and film sync receives its complete contents. */
        BaseValue.edit(this.properties, IValueListener.FLAG_BATCH, properties ->
        {
            if (this.autoKeyframe)
            {
                KeyframeChannel<Float> channel = properties.get(this.track);

                if (channel == null)
                {
                    channel = properties.create(this.property);
                }

                channel.insertInheriting(tick, value);
            }
            else
            {
                this.existing.setValue(value);
            }
        });

        this.changed = true;
    }

    void finish()
    {
        if (this.changed)
        {
            this.properties.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }
    }
}
