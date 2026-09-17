param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME,
    [string]$CoreRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) '../irl-core')
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (!$GradleUserHome) { $GradleUserHome = Join-Path $env:USERPROFILE '.gradle' }
$jomlDir = Join-Path $GradleUserHome 'caches/modules-2/files-2.1/org.joml/joml/1.10.5'
$joml = Get-ChildItem -LiteralPath $jomlDir -Recurse -Filter 'joml-*.jar' |
    Where-Object Name -NotMatch '(sources|javadoc)' | Select-Object -First 1
if (!$joml) { throw "Minecraft 1.20.4 JOML 1.10.5 not found under $jomlDir" }
$output = Join-Path $repo 'build/render-cookie-test'
$generated = Join-Path $output 'boundary'
$classes = Join-Path $output 'classes'
New-Item -ItemType Directory -Force -Path $classes, $generated | Out-Null

# Only Minecraft/BBS/asset/GL boundaries are replaced. The resolver, complete
# spotlight register path, CookieArray LRU and core LightMath are production.
$boundary = @{
'com/mojang/blaze3d/systems/RenderSystem.java' = @'
package com.mojang.blaze3d.systems;
import org.joml.Matrix3f;
public final class RenderSystem {
    public static final Matrix3f inverse = new Matrix3f();
    public static long reads;
    public static Matrix3f getInverseViewRotationMatrix() { reads++; return inverse; }
}
'@
'net/minecraft/util/math/Vec3d.java' = @'
package net.minecraft.util.math;
public class Vec3d {
    public final double x, y, z;
    public Vec3d(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
}
'@
'net/minecraft/client/MinecraftClient.java' = @'
package net.minecraft.client;
import net.minecraft.util.math.Vec3d;
public class MinecraftClient {
    private static final MinecraftClient INSTANCE = new MinecraftClient();
    public static MinecraftClient getInstance() { return INSTANCE; }
    public final GameRenderer gameRenderer = new GameRenderer();
    public static class GameRenderer {
        private final Camera camera = new Camera();
        public Camera getCamera() { return camera; }
    }
    public static class Camera {
        public Vec3d pos = new Vec3d(0,0,0);
        public Vec3d getPos() { return pos; }
    }
}
'@
'mchorse/bbs_mod/forms/renderers/FormRenderType.java' = @'
package mchorse.bbs_mod.forms.renderers;
public enum FormRenderType { ENTITY, PREVIEW }
'@
'mchorse/bbs_mod/forms/renderers/FormRenderingContext.java' = @'
package mchorse.bbs_mod.forms.renderers;
import org.joml.Matrix4f;
public class FormRenderingContext {
    public boolean modelRenderer;
    public FormRenderType type = FormRenderType.ENTITY;
    public final Stack stack = new Stack();
    public final Stencil stencilMap = new Stencil();
    public int getPickingIndex() { return 0; }
    public static class Stencil { public void addPicking(Object form, Object handle) {} }
    public static class Stack {
        private final Entry entry = new Entry();
        public Entry peek() { return entry; }
    }
    public static class Entry {
        private final Matrix4f matrix = new Matrix4f();
        public Matrix4f getPositionMatrix() { return matrix; }
    }
}
'@
'mchorse/bbs_mod/ui/utils/icons/Icon.java' = @'
package mchorse.bbs_mod.ui.utils.icons; public class Icon {}
'@
'mchorse/bbs_mod/ui/utils/icons/Icons.java' = @'
package mchorse.bbs_mod.ui.utils.icons;
public class Icons { public static final Icon FRUSTUM = new Icon(); }
'@
'mchorse/bbs_mod/utils/colors/Color.java' = @'
package mchorse.bbs_mod.utils.colors;
public class Color { public float r=1, g=1, b=1, a=1; }
'@
'mchorse/bbs_mod/resources/Link.java' = @'
package mchorse.bbs_mod.resources;
public class Link {
    public String key;
    public Link(String key) { this.key=key; }
    @Override public String toString() { return key; }
}
'@
'mchorse/bbs_mod/BBSMod.java' = @'
package mchorse.bbs_mod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import mchorse.bbs_mod.resources.Link;
import qualet.irlite.client.forms.RenderCookieTest;
public class BBSMod {
    private static final Provider provider = new Provider();
    public static Provider getProvider() { return provider; }
    public static class Provider {
        public InputStream getAsset(Link link) throws IOException {
            RenderCookieTest.event("read:" + link);
            if (RenderCookieTest.callback != null) {
                Runnable action = RenderCookieTest.callback;
                RenderCookieTest.callback = null;
                action.run();
            }
            if (link.key.startsWith("missing")) throw new IOException("missing fixture");
            return new ByteArrayInputStream(link.key.getBytes(StandardCharsets.UTF_8));
        }
    }
}
'@
'org/qualet/irl/light/CookieArrayBase.java' = @'
package org.qualet.irl.light;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import qualet.irlite.client.forms.RenderCookieTest;
public abstract class CookieArrayBase {
    public static final int RES = 512;
    private int texture;
    protected CookieArrayBase(int layers) { if(layers != 32) throw new AssertionError("capacity changed"); }
    protected final int textureId() { return texture; }
    public static ByteBuffer decode(byte[] raw) {
        String key = new String(raw, StandardCharsets.UTF_8);
        RenderCookieTest.event("decode:" + key);
        return key.startsWith("corrupt") ? null : ByteBuffer.wrap(raw);
    }
    protected final void uploadLayer(ByteBuffer pixels, int layer) {
        String key = new String(pixels.array(), StandardCharsets.UTF_8);
        RenderCookieTest.event("upload:" + key + ":" + layer);
        if (key.startsWith("upload-fail")) throw new IllegalStateException("upload fixture");
        if (texture == 0) { texture = 1; RenderCookieTest.event("create"); }
    }
    protected final void deleteTexture() {
        if (texture != 0) { RenderCookieTest.event("delete"); texture = 0; }
    }
}
'@
'org/lwjgl/system/MemoryUtil.java' = @'
package org.lwjgl.system;
import java.nio.ByteBuffer;
import qualet.irlite.client.forms.RenderCookieTest;
public final class MemoryUtil {
    public static void memFree(ByteBuffer b) { RenderCookieTest.event("free"); }
}
'@
'org/lwjgl/stb/STBImage.java' = @'
package org.lwjgl.stb;
public final class STBImage { public static String stbi_failure_reason() { return "fixture"; } }
'@
'org/slf4j/Logger.java' = @'
package org.slf4j;
public interface Logger { void debug(String message, Object... args); void warn(String message, Object... args); }
'@
'org/slf4j/LoggerFactory.java' = @'
package org.slf4j;
public final class LoggerFactory {
    private static final Logger LOGGER = new Logger() {
        public void debug(String message, Object... args) {}
        public void warn(String message, Object... args) {}
    };
    public static Logger getLogger(String name) { return LOGGER; }
}
'@
'qualet/irlite/forms/SpotlightForm.java' = @'
package qualet.irlite.forms;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
public class SpotlightForm {
    public static class Value<T> {
        public T value;
        public Value(T value) { this.value=value; }
        public T get() { return value; }
    }
    public final Value<Color> color = new Value<>(new Color());
    public final Value<Float> radius = new Value<>(35F), innerRadius = new Value<>(25F),
        range = new Value<>(12F), intensity = new Value<>(1F), anisotropy = new Value<>(0.4F),
        vlDensity = new Value<>(0.05F), beamStrength = new Value<>(1F), bulbSize = new Value<>(0F),
        cookieRotation = new Value<>(13F), cookieScale = new Value<>(1F);
    public final Value<Boolean> entitiesOnly = new Value<>(false), blocksOnly = new Value<>(false),
        shadows = new Value<>(true), cookieInvert = new Value<>(false);
    public final Value<Link> cookie = new Value<>(null);
}
'@
'qualet/irlite/client/forms/AbstractLightFormRenderer.java' = @'
package qualet.irlite.client.forms;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Color;
public abstract class AbstractLightFormRenderer<T> {
    protected final T form;
    protected AbstractLightFormRenderer(T form) { this.form=form; }
    protected abstract Color lightColor();
    protected abstract Icon icon();
    protected abstract void renderGuide(FormRenderingContext context, Color color);
    protected void renderStencilHandles(FormRenderingContext context) {}
    protected abstract void registerLight(FormRenderingContext context);
}
'@
'qualet/irlite/client/forms/SpotGuideDrag.java' = @'
package qualet.irlite.client.forms;
public class SpotGuideDrag {
    public static final int HANDLE_RADIUS=1, HANDLE_INNER=2, HANDLE_RANGE=3;
    public static void captureGuideMatrix(Object form, Object stack) {}
}
'@
'qualet/irlite/client/forms/LightGuideRenderer.java' = @'
package qualet.irlite.client.forms;
public class LightGuideRenderer {
    public static void renderSpotlight(Object stack, Object color, float range, float radius, float inner) {}
    public static void renderSpotlightGrabRing(Object stack, float range, float radius, int index) {}
    public static void renderSpotlightGrabCap(Object stack, float range, int index) {}
}
'@
'org/qualet/irl/light/LightRegistry.java' = @'
package org.qualet.irl.light;
import java.util.*;
public final class LightRegistry {
    public static boolean capture = true;
    public static final List<double[]> lights = new ArrayList<>();
    public static double sink;
    public static void registerSpot(double x, double y, double z, float dx, float dy, float dz,
        float r, float g, float b, float intensity, float range, float outer, float inner,
        boolean entities, boolean blocks, float anisotropy, float density, float beam,
        float bulb, boolean shadows, float cookie, float rot, float scale, float flags, int identity) {
        sink += x+y+z+dx+dy+dz+cookie;
        if (capture) lights.add(new double[]{x,y,z,dx,dy,dz,r,g,b,intensity,range,outer,inner,
            entities?1:0,blocks?1:0,anisotropy,density,beam,bulb,shadows?1:0,cookie,rot,scale,flags,identity});
    }
}
'@
}
$sources = [Collections.Generic.List[string]]::new()
foreach ($entry in $boundary.GetEnumerator()) {
    $path = Join-Path $generated $entry.Key
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [IO.File]::WriteAllText($path, $entry.Value)
    $sources.Add($path)
}
$sources.Add((Join-Path $repo 'src/client/java/qualet/irlite/client/light/IRLightPositionResolver.java'))
$sources.Add((Join-Path $repo 'src/client/java/qualet/irlite/client/forms/SpotlightFormRenderer.java'))
$sources.Add((Join-Path $repo 'src/client/java/qualet/irlite/client/light/cookie/CookieArray.java'))
$sources.Add((Join-Path $CoreRoot 'src/main/java/org/qualet/irl/light/LightMath.java'))
$sources.Add((Join-Path $PSScriptRoot 'render-cookie/RenderCookieTest.java'))
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'render-cookie/reference') -Filter '*.java' |
    ForEach-Object { $sources.Add($_.FullName) }
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
& $javac --release 17 -encoding UTF-8 -cp $joml.FullName -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Render/cookie test compilation failed' }
& $java '-Xms256m' '-Xmx256m' '-XX:+UseSerialGC' -cp "$classes$([IO.Path]::PathSeparator)$($joml.FullName)" qualet.irlite.client.forms.RenderCookieTest
if ($LASTEXITCODE -ne 0) { throw 'Render/cookie production checks failed' }
