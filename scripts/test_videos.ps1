$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $root
$videosDir = Join-Path $repoRoot "videos"
$outDir = Join-Path $repoRoot "output_autoedit"

if (!(Test-Path $videosDir)) {
  throw "Videos folder not found: $videosDir"
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "Using videos dir: $videosDir"
Write-Host "Output dir: $outDir"

$videos = Get-ChildItem -Path $videosDir -File -Filter *.mp4
if ($videos.Count -eq 0) {
  throw "No .mp4 files found in $videosDir"
}

$apiBase = "http://localhost:8081"

foreach ($v in $videos) {
  $idempotency = [guid]::NewGuid().ToString()
  Write-Host ""
  Write-Host "=== Processing: $($v.Name) ==="

  $jobResp = Invoke-RestMethod -Method Post -Uri "$apiBase/v1/jobs" -Headers @{ "Idempotency-Key" = $idempotency } -ContentType "application/json" -Body "{}"
  $jobId = $jobResp.jobId
  $uploadUrl = $jobResp.uploadUrl
  $commitUrl = "$apiBase$($jobResp.commitUrl)"
  $statusUrl = "$apiBase$($jobResp.statusUrl)"

  Write-Host "jobId: $jobId"

  # Upload MP4 to presigned URL
  Write-Host "Uploading..."
  # Use curl for reliable large-file PUT (Invoke-WebRequest can hang with large presigned uploads)
  & curl.exe -L --fail -X PUT -H "Content-Type: video/mp4" --data-binary "@$($v.FullName)" "$uploadUrl" | Out-Null

  # Commit (use curl to avoid WinHTTP edge cases)
  Write-Host "Committing upload..."
  & curl.exe -s -S -L --fail -X POST "$commitUrl" | Out-Null

  # Poll
  Write-Host "Waiting for worker..."
  $downloadUrl = $null
  for ($i = 0; $i -lt 240; $i++) { # up to ~40 min @ 10s
    $stJson = & curl.exe -s -S -L --fail "$statusUrl"
    $st = $stJson | ConvertFrom-Json
    Write-Host "  status=$($st.status) attempts=$($st.attemptCount)"
    if ($st.status -eq "SUCCEEDED") {
      $downloadUrl = $st.downloadUrl
      break
    }
    if ($st.status -eq "FAILED") {
      throw "Job failed: $($st.errorCode) $($st.errorMessage)"
    }
    Start-Sleep -Seconds 10
  }

  if (-not $downloadUrl) {
    throw "Timed out waiting for job $jobId"
  }

  $outPath = Join-Path $outDir ($v.BaseName + "_cleaned.mp4")
  Write-Host "Downloading to $outPath ..."
  & curl.exe -L --fail "$downloadUrl" -o "$outPath" | Out-Null
}

Write-Host ""
Write-Host "DONE. Outputs in: $outDir"

