[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8000",
    [string]$SttModel = "Systran/faster-distil-whisper-small.en",
    [string]$TtsModel = "speaches-ai/Kokoro-82M-v1.0-ONNX",
    [string]$Voice = "bf_emma",
    [string]$InputAudioPath,
    [switch]$SyntheticLong,
    [ValidateRange(2, 20)][int]$Iterations = 5,
    [int]$MaxWarmSttMs = 10000,
    [int]$MaxWarmTtsMs = 5000,
    [double]$MaxSttRealTimeFactor = 0.5
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd("/")
$tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("speaches-latency-" + [Guid]::NewGuid())
$metricsDirectory = Join-Path $PSScriptRoot "..\runtime\metrics"
[System.IO.Directory]::CreateDirectory($tempDirectory) | Out-Null
[System.IO.Directory]::CreateDirectory($metricsDirectory) | Out-Null

function Invoke-CurlTimed {
    param([string[]]$Arguments)
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    & curl.exe @Arguments
    $exitCode = $LASTEXITCODE
    $watch.Stop()
    if ($exitCode -ne 0) {
        throw "curl failed with exit code $exitCode"
    }
    return [int]$watch.ElapsedMilliseconds
}

function Get-Percentile95 {
    param([int[]]$Values)
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * 0.95) - 1)
    return $sorted[$index]
}

try {
    $requestPath = Join-Path $tempDirectory "speech-request.json"
    $generatedAudioPath = Join-Path $tempDirectory "sample.wav"
    $transcriptPath = Join-Path $tempDirectory "transcript.json"
    $request = @{
        model = $TtsModel
        voice = $Voice
        input = "I enjoy learning English because it helps me communicate with people from different cultures."
        response_format = "wav"
    } | ConvertTo-Json -Compress
    [System.IO.File]::WriteAllText($requestPath, $request, [System.Text.Encoding]::UTF8)

    $tts = @()
    for ($index = 0; $index -lt $Iterations; $index++) {
        $tts += Invoke-CurlTimed @(
            "--fail-with-body", "--silent", "--show-error",
            "--output", $generatedAudioPath,
            "--header", "Content-Type: application/json",
            "--data-binary", "@$requestPath",
            "$base/v1/audio/speech"
        )
    }

    $sttInput = $generatedAudioPath
    $audioSource = "synthetic-short"
    if ($InputAudioPath) {
        $sttInput = [System.IO.Path]::GetFullPath($InputAudioPath)
        if (-not (Test-Path -LiteralPath $sttInput -PathType Leaf)) {
            throw "Audio file not found: $sttInput"
        }
        $audioSource = "supplied"
    } elseif ($SyntheticLong) {
        $longAudioPath = Join-Path $tempDirectory "synthetic-long.wav"
        $longRequestPath = Join-Path $tempDirectory "speech-long-request.json"
        $paragraph = "I would like to describe a memorable place in my hometown. It is a quiet public park near the river where families exercise, children play, and older people meet their friends. I first visited it when I was at secondary school, and since then it has become somewhere I go whenever I need to relax. What makes this place special is the combination of open space, fresh air, and a strong sense of community. In the future, I hope the local government will protect it and improve the walking paths without changing its peaceful character. "
        $longRequest = @{
            model = $TtsModel
            voice = $Voice
            input = ($paragraph * 3).Trim()
            response_format = "wav"
        } | ConvertTo-Json -Compress
        [System.IO.File]::WriteAllText($longRequestPath, $longRequest, [System.Text.Encoding]::UTF8)
        Invoke-CurlTimed @(
            "--fail-with-body", "--silent", "--show-error", "--output", $longAudioPath,
            "--header", "Content-Type: application/json", "--data-binary", "@$longRequestPath",
            "$base/v1/audio/speech"
        ) | Out-Null
        $sttInput = $longAudioPath
        $audioSource = "synthetic-long"
    }

    $stt = @()
    for ($index = 0; $index -lt $Iterations; $index++) {
        $stt += Invoke-CurlTimed @(
            "--fail-with-body", "--silent", "--show-error",
            "--output", $transcriptPath,
            "--form", "file=@$sttInput",
            "--form", "model=$SttModel",
            "--form", "language=en",
            "--form", "response_format=verbose_json",
            "$base/v1/audio/transcriptions"
        )
    }

    $transcription = Get-Content $transcriptPath -Raw | ConvertFrom-Json
    $audioDurationMs = if ($transcription.duration) { [int]([double]$transcription.duration * 1000) } else { 0 }
    $warmTts = @($tts | Select-Object -Skip 1)
    $warmStt = @($stt | Select-Object -Skip 1)
    $warmSttP95 = Get-Percentile95 $warmStt
    $realTimeFactor = if ($audioDurationMs -gt 0) { [Math]::Round($warmSttP95 / $audioDurationMs, 3) } else { $null }
    $sttPassed = if ($audioSource -eq "synthetic-long") {
        $null -ne $realTimeFactor -and $realTimeFactor -le $MaxSttRealTimeFactor
    } else {
        $warmSttP95 -le $MaxWarmSttMs
    }
    $ttsPassed = (Get-Percentile95 $warmTts) -le $MaxWarmTtsMs
    $thresholdsPassed = ($ttsPassed -and $sttPassed)
    $result = [ordered]@{
        measuredAt = [DateTimeOffset]::Now.ToString("o")
        baseUrl = $base
        iterations = $Iterations
        models = @{ stt = $SttModel; tts = $TtsModel; voice = $Voice }
        audioSource = $audioSource
        ttsMs = @{ first = $tts[0]; warmP95 = Get-Percentile95 $warmTts; samples = $tts }
        audioDurationMs = $audioDurationMs
        sttMs = @{ first = $stt[0]; warmP95 = $warmSttP95; samples = $stt; realTimeFactor = $realTimeFactor }
        thresholdsMs = @{ warmTtsP95 = $MaxWarmTtsMs; warmSttP95 = $MaxWarmSttMs; maxSttRealTimeFactor = $MaxSttRealTimeFactor }
        sttThresholdPassed = $sttPassed
        ttsThresholdPassed = $ttsPassed
        thresholdsPassed = $thresholdsPassed
        part2Ready = ($audioSource -eq "supplied" -and $thresholdsPassed)
        part2ProvisionalReady = ($audioSource -eq "synthetic-long" -and $sttPassed)
        transcript = $transcription.text
    }
    $json = $result | ConvertTo-Json -Depth 6
    $reportPath = Join-Path $metricsDirectory ("speaches-latency-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".json")
    [System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($reportPath), $json, [System.Text.Encoding]::UTF8)
    Write-Output $json
    Write-Output "Report: $([System.IO.Path]::GetFullPath($reportPath))"
    if (-not $result.thresholdsPassed) { exit 2 }
}
finally {
    if (Test-Path $tempDirectory) {
        Remove-Item -LiteralPath $tempDirectory -Recurse -Force
    }
}
