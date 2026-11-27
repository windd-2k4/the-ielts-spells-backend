[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\runtime\certs\windows-ca-bundle.pem")
)

$ErrorActionPreference = "Stop"
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutput
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

$certificates = @{}
foreach ($storePath in @(
    "Cert:\CurrentUser\Root",
    "Cert:\CurrentUser\CA",
    "Cert:\LocalMachine\Root",
    "Cert:\LocalMachine\CA"
)) {
    Get-ChildItem $storePath -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.HasPrivateKey -or -not $_.Thumbprint) {
            # Only the public certificate is exported below; private keys are never read.
        }
        $certificates[$_.Thumbprint] = $_
    }
}

$builder = [System.Text.StringBuilder]::new()
foreach ($certificate in ($certificates.Values | Sort-Object Subject, Thumbprint)) {
    $base64 = [Convert]::ToBase64String(
        $certificate.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert),
        [System.Base64FormattingOptions]::InsertLineBreaks
    )
    [void]$builder.AppendLine("-----BEGIN CERTIFICATE-----")
    [void]$builder.AppendLine($base64)
    [void]$builder.AppendLine("-----END CERTIFICATE-----")
}

[System.IO.File]::WriteAllText($resolvedOutput, $builder.ToString(), [System.Text.Encoding]::ASCII)
Write-Output "Exported $($certificates.Count) public CA certificates to $resolvedOutput"
