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

# Service IA lancé par les scripts de test : le script appelant définit $aiUrl, $python, $aiDir et $aiLog.
function Test-AiUp {
    try { return (Invoke-WebRequest -UseBasicParsing "$aiUrl/health" -TimeoutSec 2).StatusCode -eq 200 } catch { return $false }
}

function Start-Ai([string]$KeyOverride = $null) {
    if (Test-AiUp) { throw "Un service IA répond déjà sur $aiUrl : l'arrêter avant de lancer ce script." }
    $env:PYTHONUTF8 = "1"
    if ($KeyOverride) { $env:AI_INTERNAL_API_KEY = $KeyOverride }
    try {
        $process = Start-Process -FilePath $python -ArgumentList "-m", "app" -WorkingDirectory $aiDir `
            -RedirectStandardError $aiLog -RedirectStandardOutput "$aiLog.out" -PassThru -WindowStyle Hidden
    } finally {
        Remove-Item Env:AI_INTERNAL_API_KEY -ErrorAction SilentlyContinue
    }
    for ($i = 0; $i -lt 40 -and -not $process.HasExited; $i++) {
        if (Test-AiUp) { return $process }
        Start-Sleep -Milliseconds 250
    }
    Stop-Ai $process
    throw "Le service IA n'a pas démarré : $(Get-Content $aiLog -Raw -ErrorAction SilentlyContinue)"
}

function Stop-Ai($process) {
    if ($process -and -not $process.HasExited) { taskkill /PID $process.Id /T /F 2>&1 | Out-Null }
    for ($i = 0; $i -lt 20 -and (Test-AiUp); $i++) { Start-Sleep -Milliseconds 250 }
}

# Envoi multipart (champ « file ») : contenu, nom et type MIME choisis librement pour tester le serveur.
function Invoke-Upload([string]$Path, [byte[]]$Bytes, [string]$FileName, [string]$Mime = "application/pdf", [string]$Token = $null) {
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(60)
    if ($Token) { $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token) }
    $content = New-Object System.Net.Http.MultipartFormDataContent
    $file = New-Object System.Net.Http.ByteArrayContent(, $Bytes)
    $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($Mime)
    $content.Add($file, "file", $FileName)
    try {
        $response = $client.PostAsync($(if ($Path -match '^https?://') { $Path } else { "$BaseUrl$Path" }), $content).Result
        $code = [int]$response.StatusCode
        $text = [Text.Encoding]::UTF8.GetString($response.Content.ReadAsByteArrayAsync().Result)
    } catch {
        return [pscustomobject]@{ Code = 0; Json = $null; Error = $_.Exception.InnerException.Message }
    } finally {
        $client.Dispose()
    }
    $json = $null
    if ($text) { $json = $text | ConvertFrom-Json }
    return [pscustomobject]@{ Code = $code; Json = $json }
}

# Envoi d'un corps binaire brut (appel direct au service IA).
function Invoke-Binary([string]$Url, [byte[]]$Bytes, [string]$ContentType, [hashtable]$Headers = @{}) {
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(60)
    foreach ($name in $Headers.Keys) { $client.DefaultRequestHeaders.Add($name, $Headers[$name]) }
    $content = New-Object System.Net.Http.ByteArrayContent(, $Bytes)
    $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
    try {
        $response = $client.PostAsync($Url, $content).Result
        $text = [Text.Encoding]::UTF8.GetString($response.Content.ReadAsByteArrayAsync().Result)
        $json = $null
        if ($text) { $json = $text | ConvertFrom-Json }
        return [pscustomobject]@{ Code = [int]$response.StatusCode; Json = $json }
    } finally {
        $client.Dispose()
    }
}

# Requête SQL dans le conteneur MySQL ; le mot de passe passe par l'environnement (jamais sur la ligne de commande).
function Invoke-Sql([string]$Query) {
    $env:MYSQL_PWD = Get-EnvValue "DB_PASSWORD"
    try {
        $previous = [Console]::OutputEncoding
        [Console]::OutputEncoding = [Text.Encoding]::UTF8
        $lines = docker exec -e MYSQL_PWD smartrecruit-mysql mysql -u (Get-EnvValue "DB_USER") -D (Get-EnvValue "DB_NAME") `
            --batch --skip-column-names --default-character-set=utf8mb4 -e $Query
    } finally {
        [Console]::OutputEncoding = $previous
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    # Toujours un tableau de lignes, même pour une seule ligne (sinon PowerShell le déplie).
    return , @($lines | Where-Object { $_ } | ForEach-Object { , ($_ -split "`t") })
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
