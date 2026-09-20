param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet('1.20.1', '1.20.4', '1.21.1', '1.21.11')][string]$MinecraftVersion = '1.20.4'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (!$JavaHome) { throw 'Pass a JDK 21 installation using -JavaHome.' }
$env:JAVA_HOME = $JavaHome
Push-Location $repo
try {
    & .\gradlew.bat profileTestClasspath "-Pmc=$MinecraftVersion" -I tools/profiles/classpath.init.gradle --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the current BBS 2.7 test classpath' }
} finally { Pop-Location }
$output = Join-Path $repo 'build/profiles-bbs-test'
$classes = Join-Path $output 'classes'
$boundary = Join-Path $output 'boundary/mchorse/bbs_mod'
New-Item -ItemType Directory -Force -Path $classes, $boundary | Out-Null
# Keep real BBS forms, values, tracks and serialization. Only isolate the singleton
# whose static initializer requires Minecraft's registry bootstrap.
$fixture = @'
package mchorse.bbs_mod;
import mchorse.bbs_mod.forms.FormArchitect;
import mchorse.bbs_mod.resources.AssetProvider;
public final class BBSMod {
    private static final FormArchitect FORMS = new FormArchitect();
    private static final AssetProvider PROVIDER = new AssetProvider();
    public static FormArchitect getForms() { return FORMS; }
    public static AssetProvider getProvider() { return PROVIDER; }
}
'@
$fixturePath = Join-Path $boundary 'BBSMod.java'
[IO.File]::WriteAllText($fixturePath, $fixture)
$classpath = [IO.File]::ReadAllText((Join-Path $output 'classpath.txt')).Replace('\', '/')
$compileArgs = @('--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', ('"' + $classpath + '"'), '-d', ('"' + $classes.Replace('\', '/') + '"'))
$compileArgs += @($fixturePath, (Join-Path $PSScriptRoot 'profiles/ProfilesBbsTest.java'), (Join-Path $PSScriptRoot 'profiles/ReplayPortApiCheck.java'), (Join-Path $PSScriptRoot 'profiles/BodyPartEntityFixture.java')) | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }
$argFile = Join-Path $output 'compile.args'
[IO.File]::WriteAllLines($argFile, $compileArgs)
& (Join-Path $JavaHome 'bin/javac.exe') "@$argFile"
if ($LASTEXITCODE -ne 0) { throw 'BBS profiles test compilation failed' }
$fixtureArgs = Join-Path $output 'entity-fixture.args'
# Production classpath first: never transform a fixture left by an earlier run.
[IO.File]::WriteAllLines($fixtureArgs, @('-cp', ('"' + $classpath + ';' + $classes.Replace('\', '/') + '"'), 'BodyPartEntityFixture', ('"' + $classes.Replace('\', '/') + '"')))
& (Join-Path $JavaHome 'bin/java.exe') "@$fixtureArgs"
if ($LASTEXITCODE -ne 0) { throw 'Body-part entity fixture generation failed' }
$runFile = Join-Path $output 'run.args'
[IO.File]::WriteAllLines($runFile, @('-cp', ('"' + $classes.Replace('\', '/') + ';' + $classpath + '"'), 'qualet.irlite.client.light.ProfilesBbsTest', ('"' + (Join-Path $output 'report.json').Replace('\', '/') + '"')))
Push-Location $output
try {
    & (Join-Path $JavaHome 'bin/java.exe') "@$runFile"
    if ($LASTEXITCODE -ne 0) { throw 'BBS profiles production checks failed' }
} finally { Pop-Location }

$apiArgs = Join-Path $output 'api.args'
[IO.File]::WriteAllLines($apiArgs, @('-cp', ('"' + $classes.Replace('\', '/') + ';' + $classpath + '"'), 'ReplayPortApiCheck', $MinecraftVersion, ('"' + (Join-Path $output 'api-report.json').Replace('\', '/') + '"')))
& (Join-Path $JavaHome 'bin/java.exe') "@$apiArgs"
if ($LASTEXITCODE -ne 0) { throw "Replay port bytecode anchor checks failed" }
