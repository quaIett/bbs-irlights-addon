package qualet.irlite.client.light;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.film.replays.tracks.TrackStyle;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.qualet.irl.light.LightProfile;
import org.qualet.irl.light.LightRegistry;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.ui.replays.LightTrackLayout;
import qualet.irlite.forms.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Real BBS 2.6 copy/save/playback; no game, OpenGL context or UI automation. */
public final class ProfilesBbsTest
{
    private static int checks;

    private static void check(boolean condition, String message)
    {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception
    {
        BBSSettings.recordingPoseTransformOverlays = new ValueInt("test", 0);
        BBSSettings.primaryColor = new ValueInt("test", 0x44aaff);
        KeyframeFactories.setup();
        TrackStyle.setup();
        BBSMod.getForms().register(PointLightForm.FORM_ID, PointLightForm.class, null);
        BBSMod.getForms().register(SpotlightForm.FORM_ID, SpotlightForm.class, null);
        Random random = new Random(93);
        String selection = ReplaySelection.encode("folder/film", List.of("actor-a", "actor-b", "actor-a"));

        for (int i = 0; i < 120; i++)
        {
            LightForm form = (i & 1) == 0 ? new PointLightForm() : new SpotlightForm();
            check(form.effects.isDefault(), "old forms inherit globals by default");
            form.effects.customVl.set(true);
            form.effects.customOutline.set(true);
            form.effects.selectedReplays.set(true);
            form.effects.selectedLightReplays.set(true);
            form.effects.vlIntensity.set(random.nextFloat() * 5);
            form.effects.outlineStrength.set(random.nextFloat() * 3);
            form.effects.vlNoiseMorph.set(1.25F);
            form.effects.outlineReplays.set(selection);
            form.effects.lightReplays.set(selection);
            form.beamStrength.set(2.75F);
            LightForm copy = (LightForm) FormUtils.copy(form);
            check(copy != form && copy.effects != form.effects, "independent copied effects");
            check(copy.effects.toData().equals(form.effects.toData()), "all profile values roundtrip");
            check(copy.beamStrength.get() == 2.75F, "existing property preserved");
            check(copy.effects.outlineReplays.get().equals(selection) && copy.effects.lightReplays.get().equals(selection), "both replay lists roundtrip");
            check(FormUtils.getForm(copy.effects.vlIntensity) == copy, "flat copied value has correct owner");
            copy.effects.vlIntensity.set(0F);
            check(form.effects.vlIntensity.get() != 0F, "source values survive editing a copy");
            MapType legacy = new MapType();
            legacy.putFloat("beam_strength", 3.5F);
            LightForm old = (i & 1) == 0 ? new PointLightForm() : new SpotlightForm();
            old.fromData(legacy);
            check(old.beamStrength.get() == 3.5F && old.effects.isDefault(), "pre-profile films load with inheritance");
        }

        check(ReplaySelection.decode(selection).replays().equals(Set.of("actor-a", "actor-b")), "stable replay IDs are deduplicated");
        check(ReplaySelection.decode("{broken").isEmpty(), "malformed input fails closed");
        check(ReplaySelection.decode(ReplaySelection.encode("фильм/тест", List.of())).isEmpty(), "empty selection");
        verifyTimeline();
        verifyRegistration();
        verifyReplayUx();
        Files.writeString(Path.of(args[0]), "{\"passed\":true,\"bbs\":\"2.6\",\"checks\":" + checks
            + ",\"timelinePlayback\":true,\"legacyForms\":true,\"steppedSelections\":true,\"globalQuality\":true,\"replayUx\":true}");
        System.out.println("BBS 2.6 profiles PASS: " + checks + " checks");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verifyTimeline()
    {
        for (LightForm light : List.of(new PointLightForm(), new SpotlightForm()))
        {
            var paths = FormUtils.collectPropertyPaths(light);
            check(paths.containsAll(List.of("vl_intensity", "outline_strength", "outline_replays", "light_replays")), "flat animation paths");
            check(!paths.contains("effects") && !paths.contains("custom_vl") && !paths.contains("outline_target"), "profile modes remain form switches");
            check(!paths.contains("vl_steps") && !paths.contains("vl_shadow_stride") && !paths.contains("vl_noise_stride") && !paths.contains("outline_pixel_size"), "quality remains global");
            FormProperties properties = new FormProperties("properties");
            for (String path : paths)
            {
                var value = FormUtils.getProperty(light, path);
                if (!light.isLightTrack(value)) continue;
                check(FormUtils.getForm(value) == light, "BBS resolves track owner");
                KeyframeChannel channel = properties.getOrCreate(light, path);
                check(channel != null, "BBS creates channel for " + path);
                if (value instanceof BaseValueNumber number)
                {
                    double a = number.getMin().doubleValue();
                    double b = a + (number.getMax().doubleValue() - a) * .5;
                    if (value instanceof ValueFloat) { channel.insert(0, (float) a); channel.insert(10, (float) b); }
                    else { channel.insert(0, (int) a); channel.insert(10, (int) b); }
                    ((Keyframe) channel.getKeyframes().get(0)).getInterpolation().setInterp(Interpolations.LINEAR);
                    properties.applyProperties(light, 5);
                    check(Math.abs(((Number) value.get()).doubleValue() - (a + b) * .5) < .0001, "numeric midpoint: " + path);
                    properties.resetProperties(light);
                }
            }
            String first = ReplaySelection.encode("film/тест", List.of("actor-a", "actor-c"));
            String next = ReplaySelection.encode("film/other", List.of("actor-b", "actor-d"));
            for (ValueReplaySelection value : List.of(light.effects.outlineReplays, light.effects.lightReplays))
            {
                KeyframeChannel channel = properties.getOrCreate(light, value.getId());
                channel.insert(0, first);
                channel.insert(10, next);
                properties.applyProperties(light, 9.99F);
                check(value.get().equals(first), "selection holds before keyframe");
                properties.applyProperties(light, 10);
                check(value.get().equals(next), "film and list switch at exact keyframe");
            }
            FormProperties restored = new FormProperties("properties");
            restored.fromData(properties.toData());
            properties.resetProperties(light);
            restored.applyProperties(light, 5);
            check(light.effects.outlineReplays.get().equals(first) && light.effects.lightReplays.get().equals(first), "both saved list tracks play back");
            restored.resetProperties(light);
            check(light.effects.outlineReplays.get().isEmpty() && light.effects.lightReplays.get().isEmpty(), "playback restores source defaults");
            var catalog = TrackCatalog.of(light, properties);
            LightTrackLayout.decorate(catalog);
            Set<Object> ids = new HashSet<>();
            for (var track : catalog) check(ids.add(track.id()), "unique grouped track id");
            check(catalog.stream().anyMatch(t -> t.kind() == TrackKind.BODY_PART), "group headers exist");
            check(ReplaySelection.isSelectable(new Form() {}) && !ReplaySelection.isSelectable(light), "bare lights are not selectable");
        }
    }

    private static void verifyRegistration() throws Exception
    {
        PointLightForm form = new PointLightForm();
        form.effects.customVl.set(true);
        form.effects.vlIntensity.set(3.25F);
        IrliteConfig.vlSteps = new ValueInt("test", 24);
        IrliteConfig.outlinePixelSize = new ValueInt("test", 2);
        LightRegistry.clear();
        LightRegistry.registerPoint(0, 0, 0, 1, 1, 1, 1, 10, false, false, .4F, .05F, 1, 0, false, System.identityHashCode(form));
        LightEffectsRegistration.apply(form);
        Field profiles = LightRegistry.class.getDeclaredField("profiles");
        profiles.setAccessible(true);
        LightProfile registered = ((LightProfile[]) profiles.get(null))[0];
        form.effects.vlIntensity.set(.25F);
        check(registered.intensity == 3.25F, "profile registration snapshots values");
        check(registered.steps == 24 && registered.pixelSize == 2, "quality from global settings");
        LightEffectsRegistration.apply(form);
        check(registered.intensity == .25F, "next registration sees edits");
        form.effects.customVl.set(false);
        LightEffectsRegistration.apply(form);
        Field has = LightRegistry.class.getDeclaredField("hasProfile");
        has.setAccessible(true);
        check(!((boolean[]) has.get(null))[0], "switching off restores inheritance");
    }

    /** The headless JVM installs the same hook as ReplaySelectionTrackMixin; all remaining
     * copy/serialization/playback and profile registration run the real BBS/IRLights classes. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verifyReplayUx() throws Exception
    {
        var registryField = mchorse.bbs_mod.film.replays.tracks.TrackBehaviours.class.getDeclaredField("REGISTRY");
        registryField.setAccessible(true);
        var registry = (java.util.Map) registryField.get(null);
        var original = registry.get(TrackKind.PROPERTY);
        registry.put(TrackKind.PROPERTY, new mchorse.bbs_mod.film.replays.tracks.behaviours.PropertyTrack()
        {
            @Override
            public void apply(mchorse.bbs_mod.film.replays.tracks.TrackContext context,
                mchorse.bbs_mod.film.replays.tracks.TrackId track, KeyframeChannel channel, float tick, float blend)
            {
                if (!ReplaySelectionPlayback.resetBeforeFirst(context, track, channel, tick, blend))
                    super.apply(context, track, channel, tick, blend);
            }
        });
        Field activeField = ReplayOutlineContext.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        var active = (java.util.Map) activeField.get(null);
        active.put("film", new java.util.HashMap<>(java.util.Map.of("a", 11, "b", 22)));
        IrliteConfig.outline = new mchorse.bbs_mod.settings.values.numeric.ValueBoolean("test", false);

        try
        {
            String selectedA = ReplaySelection.encode("film", List.of("a"), ReplaySelection.Mode.SELECTED);
            String selectedB = ReplaySelection.encode("film", List.of("b"), ReplaySelection.Mode.SELECTED);
            String nobody = ReplaySelection.encode("film", List.of(), ReplaySelection.Mode.SELECTED);
            String inherit = ReplaySelection.encode("", List.of(), ReplaySelection.Mode.INHERIT);
            String legacyA = ReplaySelection.encode("film", List.of("a"));

            for (LightForm source : List.of(new PointLightForm(), new SpotlightForm()))
            {
                source.effects.outline.set(false);
                source.effects.outlineTarget.set(2);
                source.effects.outlineStrength.set(1.25F);
                var savedForm = source.toData();
                LightForm live = (LightForm) FormUtils.copy(source);
                FormProperties properties = new FormProperties("properties");
                KeyframeChannel lit = properties.getOrCreate(source, "light_replays");
                KeyframeChannel outline = properties.getOrCreate(source, "outline_replays");
                lit.insert(10, selectedA);
                lit.insert(20, nobody);
                lit.insert(30, inherit);
                outline.insert(15, selectedB);
                outline.insert(25, nobody);
                outline.insert(35, inherit);
                FormProperties restored = new FormProperties("properties");
                restored.fromData(properties.toData());
                check(restored.toData().equals(properties.toData()), "new modes roundtrip in real BBS tracks");

                restored.applyProperties(live, 9.99F);
                check(profile(live) == null, "new lists do not activate before first key");
                restored.applyProperties(live, 10);
                LightProfile p = profile(live);
                check(p.selectedLightReplays && !p.selectedReplays && !p.customOutline, "Lit activates alone with all form switches off");
                check(java.util.Arrays.equals(p.lightReplayIds, new int[] {11}), "Lit resolves stable actor IDs");
                restored.applyProperties(live, 15);
                p = profile(live);
                check(p.selectedReplays && p.customOutline && (p.flags & 256) != 0, "Outline activates with global and local outline off");
                check((p.flags & (3 << 11)) == 0, "selected replays override a conflicting blocks/entities target");
                check(p.strength == 1.25F, "automatic outline keeps local look settings");
                check(java.util.Arrays.equals(p.replayIds, new int[] {22}) && java.util.Arrays.equals(p.lightReplayIds, new int[] {11}), "Lit and Outline lists stay independent");

                restored.applyProperties(live, 20);
                p = profile(live);
                check(p.selectedLightReplays && p.lightReplayIds.length == 0 && p.replayIds[0] == 22, "Nobody lights nobody, keeping Outline");
                restored.applyProperties(live, 25);
                p = profile(live);
                check(p.selectedReplays && p.replayIds.length == 0, "Nobody outlines nobody");
                restored.applyProperties(live, 30);
                p = profile(live);
                check(!p.selectedLightReplays && p.selectedReplays, "Lit inheritance does not turn off Outline mode");
                restored.applyProperties(live, 35);
                check(profile(live) == null, "both inheritance keys restore the original light");
                restored.applyProperties(live, 15);
                restored.applyProperties(live, 0);
                check(profile(live) == null, "scrubbing backwards releases automatic modes");

                var savedTracks = restored.toData();
                restored.getOrCreate(live, "outline_replays").get(0).setValue(selectedA);
                restored.applyProperties(live, 15);
                check(profile(live).replayIds[0] == 11, "editing a key reaches an existing live form without reopening its editor");
                restored.fromData(savedTracks);
                restored.applyProperties(live, 15);
                check(profile(live).replayIds[0] == 22, "restoring an undo snapshot restores targets");
                restored.resetProperties(live);
                check(profile(live) == null, "releasing animation clears modes");
                check(source.toData().equals(savedForm) && live.toData().equals(savedForm), "key edits never change persistent form settings");
                check(!source.effects.customOutline.get() && !source.effects.selectedReplays.get()
                    && !source.effects.selectedLightReplays.get(), "automatic activation does not flip form switches");

                /* Old lists with every combination of switches keep their original semantics. */
                live.effects.lightReplays.set(legacyA);
                live.effects.outlineReplays.set(legacyA);
                for (int switches = 0; switches < 16; switches++)
                {
                    live.effects.customOutline.set((switches & 1) != 0);
                    live.effects.outline.set((switches & 2) != 0);
                    live.effects.selectedReplays.set((switches & 4) != 0);
                    live.effects.selectedLightReplays.set((switches & 8) != 0);
                    p = profile(live);
                    if ((switches & 13) == 0) check(p == null, "inactive legacy lists stay inactive");
                    else check(p.customOutline == ((switches & 1) != 0) && ((p.flags & 256) != 0) == ((switches & 2) != 0)
                        && p.selectedReplays == ((switches & 4) != 0) && p.selectedLightReplays == ((switches & 8) != 0), "legacy switch combination preserved");
                }
                restored.applyProperties(live, 35);
                p = profile(live);
                check(p.replayIds[0] == 11 && p.lightReplayIds[0] == 11, "inheritance restores the form's saved lists");
                live.effects.outlineReplays.set(ReplaySelection.encode("film", List.of("b")));
                check(profile(live).replayIds[0] == 22, "inherited base-list edits invalidate cached targets");
                restored.resetProperties(live);

                FormProperties legacy = new FormProperties("properties");
                legacy.getOrCreate(live, "outline_replays").insert(10, legacyA);
                legacy.applyProperties(live, 0);
                check(live.effects.outlineReplays.get().equals(legacyA), "legacy first key keeps BBS extrapolation");
                legacy.resetProperties(live);
                live.effects.outlineReplays.setRuntimeValue(ReplaySelection.encode("absent-film", List.of("a"), ReplaySelection.Mode.SELECTED));
                check(profile(live).replayIds[0] == 0, "foreign-film replay does not match");
                live.effects.outlineReplays.setRuntimeValue(ReplaySelection.encode("film", List.of("removed"), ReplaySelection.Mode.SELECTED));
                check(profile(live).replayIds[0] == 0, "removed replay does not match");
            }
        }
        finally
        {
            registry.put(TrackKind.PROPERTY, original);
            active.clear();
        }
    }

    private static LightProfile profile(LightForm form) throws Exception
    {
        LightRegistry.clear();
        LightRegistry.registerPoint(0, 0, 0, 1, 1, 1, 1, 10, false, false, .4F, .05F, 1, 0, false, System.identityHashCode(form));
        LightEffectsRegistration.apply(form);
        Field has = LightRegistry.class.getDeclaredField("hasProfile");
        has.setAccessible(true);
        if (!((boolean[]) has.get(null))[0]) return null;
        Field profiles = LightRegistry.class.getDeclaredField("profiles");
        profiles.setAccessible(true);
        return ((LightProfile[]) profiles.get(null))[0];
    }
}
