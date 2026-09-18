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
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve BBS classpath' }
    $output = Join-Path $repo 'build/spot-guide-test'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $classpath = [IO.File]::ReadAllText((Join-Path $repo 'build/profiles-bbs-test/classpath.txt')).Replace('\', '/')
    # Isolate only the Minecraft singleton; tracks/forms/playback remain real BBS code.
    $fixture = Join-Path $output 'BBSMod.java'
    [IO.File]::WriteAllText($fixture, @'
package mchorse.bbs_mod;
import mchorse.bbs_mod.forms.FormArchitect;
import mchorse.bbs_mod.resources.AssetProvider;
public final class BBSMod {
    private static final FormArchitect FORMS = new FormArchitect();
    private static final AssetProvider PROVIDER = new AssetProvider();
    public static FormArchitect getForms() { return FORMS; }
    public static AssetProvider getProvider() { return PROVIDER; }
}
'@)
    $compile = Join-Path $output 'compile.args'
    [IO.File]::WriteAllLines($compile, @('--release', '17', '-proc:none', '-cp', ('"' + $classpath + '"'), '-d', ('"' + $output.Replace('\', '/') + '"'), ('"' + $fixture.Replace('\', '/') + '"'), ('"' + (Join-Path $PSScriptRoot 'profiles/SpotGuideKeyframesTest.java').Replace('\', '/') + '"')))
    & (Join-Path $JavaHome 'bin/javac.exe') "@$compile"
    if ($LASTEXITCODE -ne 0) { throw 'Spot guide test compilation failed' }
    $run = Join-Path $output 'run.args'
    [IO.File]::WriteAllLines($run, @('-cp', ('"' + $output.Replace('\', '/') + ';' + $classpath + '"'), 'qualet.irlite.client.forms.SpotGuideKeyframesTest'))
    & (Join-Path $JavaHome 'bin/java.exe') "@$run"
    if ($LASTEXITCODE -ne 0) { throw 'Spot guide checks failed' }
} finally { Pop-Location }
