$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourceRoot = Join-Path $projectRoot 'art\source'
$outputRoot = Join-Path $projectRoot 'app\src\main\res\drawable-nodpi'
$oldRoot = Join-Path $projectRoot 'art\baseline\drawables'
New-Item -ItemType Directory -Force $outputRoot,$oldRoot | Out-Null
Get-ChildItem -LiteralPath $sourceRoot -Filter '*.png' | Where-Object { $_.BaseName -ne 'style-guide' } | ForEach-Object {
    $assetName = $_.BaseName
    $assetSize = if ($assetName -eq 'home_diorama') { 1024 } elseif ($assetName.StartsWith('material_')) { 512 } else { 256 }
    $outputPath = Join-Path $outputRoot ($assetName + '.webp')
    # Trim transparent packaging margins only. Preserve the complete authored silhouette.
    $filters = "scale=${assetSize}:${assetSize}:force_original_aspect_ratio=decrease:flags=lanczos"
    if (-not $assetName.StartsWith('material_') -and $assetName -ne 'home_diorama') {
        $pythonPath = 'C:\Users\toast\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
        $bounds = (& $pythonPath -c "from PIL import Image; import sys,json; im=Image.open(sys.argv[1]); print(json.dumps(im.getchannel('A').point(lambda a: 255 if a > 16 else 0).getbbox()))" $_.FullName) | ConvertFrom-Json
        if ($bounds) {
            $cropWidth = $bounds[2] - $bounds[0]
            $cropHeight = $bounds[3] - $bounds[1]
            $filters = "crop=${cropWidth}:${cropHeight}:$($bounds[0]):$($bounds[1]),$filters"
        }
    }
    & ffmpeg -hide_banner -loglevel error -y -i $_.FullName -vf $filters -c:v libwebp -lossless 1 -compression_level 6 $outputPath
    if ($LASTEXITCODE -ne 0) { throw "Encoding failed: $assetName" }
    $oldAsset = Join-Path $projectRoot "app\src\main\res\drawable\$assetName.xml"
    if (Test-Path -LiteralPath $oldAsset) { Move-Item -LiteralPath $oldAsset -Destination (Join-Path $oldRoot ($assetName + '.xml')) -Force }
}
Write-Output 'Individual WebP assets packaged; alpha preserved; originals retained in art/source.'

