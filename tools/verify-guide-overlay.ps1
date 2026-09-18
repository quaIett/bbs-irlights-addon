param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MinecraftVersion = '1.20.4'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = $JavaHome
Push-Location $repo
try {
    & ./gradlew.bat profileTestClasspath "-Pmc=$MinecraftVersion" -I tools/profiles/classpath.init.gradle --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve overlay test classpath' }
    $output = Join-Path $repo 'build/guide-overlay-test'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $classpath = [IO.File]::ReadAllText((Join-Path $repo 'build/profiles-bbs-test/classpath.txt')).Replace('\', '/')
    $fixture = Join-Path $output 'BBSRendering.java'
    [IO.File]::WriteAllText($fixture, @'
package mchorse.bbs_mod.client;
public final class BBSRendering {
    public static boolean active;
    public static boolean isIrisWorldShadersEnabled() { return active; }
    public static boolean isIrisWorldForms() { return active; }
}
'@)
    $compile = Join-Path $output 'compile.args'
    [IO.File]::WriteAllLines($compile, @('--release', '17', '-proc:none', '-cp', ('"' + $classpath + '"'), '-d', ('"' + $output.Replace('\', '/') + '"'), ('"' + $fixture.Replace('\', '/') + '"'), ('"' + (Join-Path $PSScriptRoot 'profiles/GuideOverlayTest.java').Replace('\', '/') + '"')))
    & (Join-Path $JavaHome 'bin/javac.exe') "@$compile"
    if ($LASTEXITCODE -ne 0) { throw 'Overlay test compilation failed' }
    $run = Join-Path $output 'run.args'
    [IO.File]::WriteAllLines($run, @('-cp', ('"' + $output.Replace('\', '/') + ';' + $classpath + '"'), 'qualet.irlite.client.forms.GuideOverlayTest', $MinecraftVersion))
    & (Join-Path $JavaHome 'bin/java.exe') "@$run"
    if ($LASTEXITCODE -ne 0) { throw 'Overlay checks failed' }
} finally { Pop-Location }
