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
import qualet.irlite.client.ui.replays.LightKeyframeRanges;
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
        verifyAnimatedEffects();
        verifyOutlineThickness();
        verifyKeyframeSliderLimits();
        verifyReplayUx();
        verifyBodyPartReplayOwnership();
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
            check(!paths.contains("vl_steps") && !paths.contains("vl_shadow_stride") && !paths.contains("vl_noise_stride"), "VL quality remains global");
            check(paths.contains("outline_pixel_size"), "outline thickness is animatable");
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

    /** Saved numeric tracks must drive the rendered profile even on a default light. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verifyAnimatedEffects() throws Exception
    {
        String[] paths = {"vl_intensity", "vl_max_dist", "vl_tip_boost", "vl_tip_radius",
            "vl_noise_amount", "vl_noise_scale", "vl_noise_speed", "vl_noise_morph",
            "outline_strength", "outline_fresnel", "outline_back", "outline_front_strength", "outline_glow_strength"};
        String[] fields = {"intensity", "maxDist", "tipBoost", "tipRadius", "noiseAmount", "noiseScale",
            "noiseSpeed", "noiseMorph", "strength", "fresnel", "back", "front", "glow"};
        int[] offsets = {0, 4, 8, 12, 16, 20, 24, 48, 64, 68, 72, 76, 80};
        var write = LightProfile.class.getDeclaredMethod("write", java.nio.ByteBuffer.class, int.class);
        write.setAccessible(true);

        for (LightForm source : List.of(new PointLightForm(), new SpotlightForm()))
        {
            for (int i = 0; i < paths.length; i++)
            {
                LightForm live = (LightForm) FormUtils.copy(source);
                var saved = live.toData();
                ValueFloat value = (ValueFloat) FormUtils.getProperty(live, paths[i]);
                FormProperties authored = new FormProperties("properties");
                KeyframeChannel channel = authored.getOrCreate(source, paths[i]);
                float a = value.getMin().floatValue();
                float b = a + (value.getMax().floatValue() - a) * .8F;
                channel.insert(0, a);
                channel.insert(10, b);
                channel.get(0).getInterpolation().setInterp(Interpolations.LINEAR);
                FormProperties playback = new FormProperties("properties");
                playback.fromData(authored.toData());

                check(profile(live) == null, "unanimated default light inherits globals");
                for (int tick : new int[] {0, 5, 10, 0})
                {
                    playback.applyProperties(live, tick);
                    LightProfile p = profile(live);
                    boolean vl = paths[i].startsWith("vl_");
                    check(p != null, "numeric key activates profile: " + paths[i]);
                    check(p.customVl == vl && p.customOutline != vl, "only keyed section activates: " + paths[i]);
                    float expected = a + (b - a) * tick / 10F;
                    check(Math.abs(LightProfile.class.getField(fields[i]).getFloat(p) - expected) < .0001F,
                        "animated value reaches light registry: " + paths[i]);
                    var packed = java.nio.ByteBuffer.allocate(LightProfile.BYTES);
                    write.invoke(p, packed, 0);
                    check((packed.getInt(96) & 3) == (vl ? 1 : 2), "GPU uses the animated section");
                    check(Math.abs(packed.getFloat(offsets[i]) - expected) < .0001F, "GPU payload follows key: " + paths[i]);
                    if (paths[i].equals("outline_front_strength")) check((p.flags & 512) != 0, "front strength key activates front rim");
                    if (paths[i].equals("outline_glow_strength")) check((p.flags & 1024) != 0, "glow strength key activates glow");
                    check(live.toData().equals(saved), "animation does not alter saved form settings");
                }
                playback.resetProperties(live);
                check(profile(live) == null, "reset releases automatic profile: " + paths[i]);
                check(live.toData().equals(saved) && source.toData().equals(saved), "source and live form remain unchanged");

                // A key equal to the saved value still overrides global settings.
                channel.get(0).setValue(value.getOriginalValue());
                authored.applyProperties(live, 0);
                check(profile(live) != null, "equal-valued key still activates profile");
                channel.removeAll();
                authored.applyProperties(live, 0);
                check(profile(live) == null, "deleting all keys releases the profile");
            }

            LightForm live = (LightForm) FormUtils.copy(source);
            FormProperties both = new FormProperties("properties");
            both.getOrCreate(live, "vl_tip_boost").insert(0, 2F);
            both.getOrCreate(live, "outline_strength").insert(0, 1F);
            both.applyProperties(live, 0);
            check(profile(live).customVl && profile(live).customOutline, "both sections animate together");
            live.effects.outlineReplays.set(ReplaySelection.encode("film", List.of(), ReplaySelection.Mode.SELECTED));
            check(profile(live).selectedReplays && profile(live).replayIds.length == 0, "numeric keys preserve Nobody targeting");
            live.effects.outlineReplays.set(ReplaySelection.encode("", List.of(), ReplaySelection.Mode.INHERIT));
            check(profile(live).customOutline && !profile(live).selectedReplays, "list inheritance does not disable numeric outline keys");
            live.effects.customVl.set(true);
            both.resetProperties(live);
            check(profile(live).customVl && !profile(live).customOutline, "reset keeps manually enabled section");

            live.effects.vlEnabled.set(false);
            live.effects.vlNoise.set(false);
            live.effects.outline.set(false);
            FormProperties noise = new FormProperties("properties");
            noise.getOrCreate(live, "vl_noise_amount").insert(0, .8F);
            noise.applyProperties(live, 0);
            check(profile(live).intensity > 0 && (profile(live).flags & 2) != 0, "noise key activates beam and noise");
            noise.resetProperties(live);
            check(profile(live).intensity == 0 && (profile(live).flags & 2) == 0, "reset restores disabled beam and noise");
            FormProperties outline = new FormProperties("properties");
            outline.getOrCreate(live, "outline_strength").insert(0, 1F);
            outline.applyProperties(live, 0);
            check((profile(live).flags & 256) != 0, "strength key activates disabled outline");
            outline.resetProperties(live);
            check((profile(live).flags & 256) == 0, "reset restores disabled outline");
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyBodyPartReplayOwnership() throws Exception
    {
        Field formsField = ReplayOutlineContext.class.getDeclaredField("FORMS");
        formsField.setAccessible(true);
        var forms = (java.util.Map<Form, Integer>) formsField.get(null);
        Field entitiesField = ReplayOutlineContext.class.getDeclaredField("ENTITIES");
        entitiesField.setAccessible(true);
        var entities = (java.util.Map<mchorse.bbs_mod.forms.entities.IEntity, Integer>) entitiesField.get(null);
        var actor = new mchorse.bbs_mod.forms.entities.StubEntity();
        Form root = new mchorse.bbs_mod.forms.forms.ModelForm();
        Form other = new mchorse.bbs_mod.forms.forms.ModelForm();
        var part = new mchorse.bbs_mod.forms.forms.BodyPart("attachment");
        var nested = new mchorse.bbs_mod.forms.forms.BodyPart("nested");
        part.setForm(new mchorse.bbs_mod.forms.forms.ModelForm());
        nested.setForm(new mchorse.bbs_mod.forms.forms.ModelForm());
        root.parts.addBodyPart(part);
        part.getForm().parts.addBodyPart(nested);
        forms.put(root, 71);
        forms.put(other, 92);
        entities.put(actor, 71);

        try
        {
            check(!part.useTarget.get(), "regression uses the default private body-part entity");
            check(ReplayOutlineContext.renderId(root, actor) == 71, "root replay retains its entity tag");
            check(ReplayOutlineContext.renderId(part.getForm(), part.getEntity()) == 71,
                "body part inherits selected replay for both Lit and Outline");
            check(ReplayOutlineContext.renderId(nested.getForm(), nested.getEntity()) == 71,
                "deeply nested body part inherits selected replay");
            check(ReplayOutlineContext.currentId() == 0, "test runs outside the parent draw scope");
            part.getForm().renderLast.set(true);
            check(ReplayOutlineContext.renderId(part.getForm(), part.getEntity()) == 71,
                "render-last attachment resolves owner without an active parent draw");
            part.useTarget.set(true);
            check(ReplayOutlineContext.renderId(part.getForm(), part.getRenderEntity(actor)) == 71,
                "useTarget body part retains actor ownership");
            check(ReplayOutlineContext.renderId(nested.getForm(), nested.getEntity()) == 71,
                "mixed useTarget nesting preserves ownership");
            root.parts.removeBodyPart(part);
            other.parts.addBodyPart(part);
            check(ReplayOutlineContext.renderId(nested.getForm(), nested.getEntity()) == 92,
                "moving attachment to another replay changes ownership");
            check(ReplayOutlineContext.renderId(new mchorse.bbs_mod.forms.forms.ModelForm(), part.getEntity()) == 0,
                "unrelated forms never inherit the previous replay");
            forms.clear();
            check(ReplayOutlineContext.renderId(nested.getForm(), nested.getEntity()) == 0,
                "frame reset or disabled replay clears attachment ownership");
        }
        finally
        {
            forms.clear();
            entities.clear();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verifyOutlineThickness() throws Exception
    {
        var write = LightProfile.class.getDeclaredMethod("write", java.nio.ByteBuffer.class, int.class);
        write.setAccessible(true);
        for (LightForm source : List.of(new PointLightForm(), new SpotlightForm()))
        {
            IrliteConfig.outlinePixelSize.set(2);
            source.effects.customOutline.set(true);
            MapType legacy = (MapType) source.toData();
            legacy.remove("outline_pixel_size");
            source.fromData(legacy);
            check(profile(source).pixelSize == 2, "legacy own outline retains global thickness");
            IrliteConfig.outlinePixelSize.set(5);
            check(profile(source).pixelSize == 5, "unkeyed legacy thickness follows global edits");
            source.effects.customOutline.set(false);
            FormProperties properties = new FormProperties("properties");
            KeyframeChannel channel = properties.getOrCreate(source, "outline_pixel_size");
            channel.insert(0, 1F);
            channel.insert(10, 5F);
            channel.insert(20, 0F);
            channel.get(0).getInterpolation().setInterp(Interpolations.LINEAR);
            var catalog = TrackCatalog.of(source, properties);
            LightTrackLayout.decorate(catalog);
            var track = catalog.stream().filter(t -> t.kind() == TrackKind.PROPERTY && t.id().subject().equals("outline_pixel_size")).findFirst().orElseThrow();
            check(track.title().get().equals("Thickness") && track.parent().formPath().equals("irlights.outline"), "thickness is in the Outline timeline section");
            FormProperties restored = new FormProperties("properties");
            restored.fromData(properties.toData());
            LightForm live = (LightForm) FormUtils.copy(source);
            for (int tick : new int[] {0, 5, 10, 20, 0})
            {
                restored.applyProperties(live, tick);
                LightProfile p = profile(live);
                int expected = tick == 0 ? 1 : tick == 5 ? 3 : 5;
                check(p != null && p.customOutline && !p.customVl && (p.flags & 256) != 0, "thickness key alone enables outline");
                check(p.pixelSize == expected, "thickness at " + tick + ": expected " + expected + ", got " + p.pixelSize);
                var packed = java.nio.ByteBuffer.allocate(LightProfile.BYTES);
                write.invoke(p, packed, 0);
                check(packed.getFloat(84) == expected, "animated thickness reaches GPU slot");
                check(live.toData().equals(source.toData()), "thickness playback does not edit the saved form");
            }
            restored.resetProperties(live);
            check(profile(live) == null, "reset releases thickness override");
            LightEffectsRegistration.copyGlobals(live.effects, false);
            check(live.effects.outlinePixelSize.get() == 5, "copy globals includes thickness");
            live.effects.outlinePixelSize.set(4F);
            LightForm copy = (LightForm) FormUtils.copy(live);
            check(copy.effects.outlinePixelSize.get() == 4 && profile(copy).pixelSize == 4, "explicit thickness survives save and copy");
        }
        IrliteConfig.outlinePixelSize.set(2);
    }

    /** UI bounds must match the effective GPU bounds rather than arbitrary form-editor limits. */
    private static void verifyKeyframeSliderLimits() throws Exception
    {
        String[] paths = {"vl_intensity", "vl_max_dist", "vl_tip_boost", "vl_tip_radius", "vl_noise_amount",
            "vl_noise_scale", "vl_noise_speed", "vl_noise_morph", "outline_strength", "outline_fresnel",
            "outline_back", "outline_front_strength", "outline_glow_strength"};
        int[] offsets = {0, 4, 8, 12, 16, 20, 24, 48, 64, 68, 72, 76, 80};
        var write = LightProfile.class.getDeclaredMethod("write", java.nio.ByteBuffer.class, int.class);
        write.setAccessible(true);
        for (LightForm form : List.of(new PointLightForm(), new SpotlightForm()))
        {
            for (int i = 0; i < paths.length; i++)
            {
                ValueFloat value = (ValueFloat) form.get(paths[i]);
                var range = LightKeyframeRanges.of(value);
                check(range != null, "clamped render property has slider: " + paths[i]);
                for (double raw : new double[] {range.min() - 10, range.max() + 10})
                {
                    value.setRuntimeValue((float) raw);
                    var packed = java.nio.ByteBuffer.allocate(LightProfile.BYTES);
                    write.invoke(profile(form), packed, 0);
                    check(Math.abs(packed.getFloat(offsets[i]) - range.clamp(raw)) < .0001,
                        "slider boundary matches real GPU saturation: " + paths[i]);
                }
                value.setRuntimeValue(null);
            }
            check(LightKeyframeRanges.of(form.intensity) == null, "main light intensity remains unrestricted");
            check(LightKeyframeRanges.of(form.beamStrength) == null && LightKeyframeRanges.of(form.vlDensity) == null,
                "unbounded beam and density keep normal trackpads");
            check(LightKeyframeRanges.of(form.effects.outlinePixelSize).clamp(0) == 0, "thickness slider preserves global sentinel");
        }
        Form unrelated = new Form() {};
        ValueFloat sameName = new ValueFloat("vl_noise_amount", 7F);
        unrelated.add(sameName);
        check(LightKeyframeRanges.of(sameName) == null, "same-named properties on unrelated forms are unaffected");
        check(LightKeyframeRanges.of(null) == null, "non-property tracks are unaffected");
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
