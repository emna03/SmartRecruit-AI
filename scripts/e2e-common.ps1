# Fonctions communes aux scripts de test de bout en bout (à charger avec : . "$PSScriptRoot\e2e-common.ps1").
# Le script appelant doit définir $BaseUrl avant d'appeler Invoke-Api.

$script:envFile = Join-Path (Split-Path -Parent $PSScriptRoot) ".env"

function Get-EnvValue([string]$key) {
    $line = Get-Content $script:envFile | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if (-not $line) { throw "$key absent de $script:envFile" }
    return ($line -split '=', 2)[1]
}

# $Path relatif à $BaseUrl, ou URL absolue (ex. appel direct au service IA) ; $Headers : en-têtes supplémentaires.
function Invoke-Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null, [hashtable]$Headers = @{}) {
    $params = @{
        UseBasicParsing = $true
        Method          = $Method
        Uri             = $(if ($Path -match '^https?://') { $Path } else { "$BaseUrl$Path" })
        Headers         = $Headers.Clone()
        ContentType     = "application/json; charset=utf-8"
    }
    if ($Token) { $params.Headers.Authorization = "Bearer $Token" }
    if ($null -ne $Body) {
        [byte[]]$bytes = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress -Depth 5))
        $params.Body = $bytes
    }
    try {
        $response = Invoke-WebRequest @params
        $code = [int]$response.StatusCode
        $text = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    } catch {
        $errorResponse = $_.Exception.Response
        if (-not $errorResponse) { throw }
        $code = [int]$errorResponse.StatusCode
        $stream = $errorResponse.GetResponseStream()
        # PowerShell 5.1 a déjà lu le corps quand la réponse ne précise pas de charset : revenir au début.
        if ($stream.CanSeek) { $stream.Position = 0 }
        $text = (New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)).ReadToEnd()
    }
    $json = $null
    if ($text) { $json = $text | ConvertFrom-Json }
    return [pscustomobject]@{ Code = $code; Json = $json }
}

$script:passed = 0
$script:failed = 0
function Assert-Check([string]$Name, [bool]$Condition) {
    if ($Condition) { $script:passed++; $status = "OK" } else { $script:failed++; $status = "KO" }
    "{0,-72} {1}" -f $Name, $status
}

function Write-Summary {
    ""
    "Résultat : $script:passed OK, $script:failed KO"
    if ($script:failed -gt 0) { exit 1 }
}
