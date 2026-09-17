# Generate the complete replay-aware patch from Original / Modification.
$ErrorActionPreference = 'Stop'
& python (Join-Path $PSScriptRoot 'gen-replay-patches.py') Solas
if ($LASTEXITCODE -ne 0) { throw 'Patch generation failed' }
