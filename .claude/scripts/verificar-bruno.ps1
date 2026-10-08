# Uso: verificar-bruno.ps1 [-Objetivo carpeta] [-RecargarDatos] [-Puerto n] [nombre=valor ...]
# Cada nombre=valor se pasa a bru como --env-var, por ejemplo para sobrescribir una variable del entorno.
# -Puerto levanta la app en otro puerto que el de local.yml, para convivir con otra app en ese (FA-99).
[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$Objetivo = '.',
    [switch]$RecargarDatos,
    [ValidateRange(1, 65535)][int]$Puerto,
    [int]$TiempoArranque = 120,
    [Parameter(ValueFromRemainingArguments)][string[]]$Variables
)

# Corre bruno/ contra la app de esta rama y la base local de pruebas, nunca contra otra (FA-61).
# Valida antes de tocar nada: puerto libre y conexion R2DBC del perfil local apuntando a localhost
# y a financeapp, resuelta con las variables de la terminal. Levanta la app sin esas variables, corre
# bru y la apaga siempre. Codigos: 0 verde, 2 validacion, 3 arranque, otro = el de bru.
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$hostsLocales = @('localhost', '127.0.0.1', '::1')
$baseDePruebas = 'financeapp'

function Detener([int]$codigo, [string]$mensaje) {
    Write-Host "VERIFICACION DETENIDA: $mensaje" -ForegroundColor Red
    exit $codigo
}

# ${VAR:default} con las variables del entorno, o solo con los defaults si $conEntorno es falso.
function Resolver([string]$texto, [bool]$conEntorno) {
    [regex]::Replace($texto, '\$\{([A-Za-z0-9_]+)(?::([^}]*))?\}', {
        param($m)
        $valor = if ($conEntorno) { [Environment]::GetEnvironmentVariable($m.Groups[1].Value) } else { $null }
        if ($valor) { return $valor }
        if ($m.Groups[2].Success) { return $m.Groups[2].Value }
        throw "La variable $($m.Groups[1].Value) no tiene valor ni default."
    })
}

function Destino([string]$url) {
    if ($url -notmatch '^r2dbc:postgresql://(\[[^\]]+\]|[^:/?]+)(?::(\d+))?/([^?]+)') {
        Detener 2 "no se entiende la URL R2DBC '$url'."
    }
    [pscustomobject]@{ Host = $Matches[1].Trim('[', ']'); Puerto = $Matches[2]; Base = $Matches[3] }
}

function QuienEscucha([int]$puerto) {
    Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty OwningProcess
}

function VariableBruno([string]$entorno, [string]$nombre) {
    if ($entorno -notmatch "name:\s*$nombre\s*\r?\n\s*value:\s*(\S+)") {
        Detener 2 "no se encontro la variable $nombre en bruno/environments/local.yml."
    }
    $Matches[1]
}

function ConPuerto([string]$url, [int]$puerto) {
    $uri = [UriBuilder]$url
    $uri.Port = $puerto
    $uri.Uri.AbsoluteUri.TrimEnd('/')
}

# 1. Puerto: -Puerto, o el de host en el entorno local de Bruno.
$entornoBruno = Get-Content (Join-Path $repo 'bruno\environments\local.yml') -Raw
$hostBruno = VariableBruno $entornoBruno 'host'
$variablesPuerto = @()
if ($PSBoundParameters.ContainsKey('Puerto')) {
    # Con dos --env-var del mismo nombre no se sabe cual gana: bru podria ir a otro puerto que la app.
    $choque = @($Variables | Where-Object { $_ -match '^(host|baseUrl)=' })
    if ($choque) {
        Detener 2 "-Puerto ya fija host y baseUrl; quita $($choque -join ', ') o el parametro -Puerto."
    }
    $puerto = $Puerto
    $variablesPuerto = @("host=$(ConPuerto $hostBruno $puerto)",
        "baseUrl=$(ConPuerto (VariableBruno $entornoBruno 'baseUrl') $puerto)")
} else {
    $puerto = ([uri]$hostBruno).Port
}

$ocupante = QuienEscucha $puerto
if ($ocupante) {
    $proceso = Get-Process -Id $ocupante -ErrorAction SilentlyContinue
    Detener 2 ("el puerto $puerto esta ocupado por PID $ocupante ($($proceso.ProcessName)). Puede ser una app " +
        "apuntando a otra base: detenla tu, o corre con -Puerto y otro puerto si quieres dejarla arriba. " +
        "Este script no mata procesos ajenos.")
}

# 2. Base: la URL del perfil local tal como la veria la app arrancada desde esta terminal.
$yamlLocal = Get-Content (Join-Path $repo 'src\main\resources\application-local.yaml') -Raw
if ($yamlLocal -notmatch '(?ms)^\s*r2dbc:\s*\r?\n\s*url:\s*(\S+)\s*\r?\n\s*username:\s*(\S+)\s*\r?\n\s*password:\s*(\S+)') {
    Detener 2 'no se encontro spring.r2dbc (url, username, password) en application-local.yaml.'
}
$plantilla = @{ Url = $Matches[1]; Usuario = $Matches[2].Trim('"'); Clave = $Matches[3].Trim('"') }

$urlConEntorno = if ($env:SPRING_R2DBC_URL) { $env:SPRING_R2DBC_URL } else { Resolver $plantilla.Url $true }
$destino = Destino $urlConEntorno
if ($destino.Host -notin $hostsLocales -or $destino.Base -ne $baseDePruebas) {
    $origen = if ($env:SPRING_R2DBC_URL) { 'SPRING_R2DBC_URL' } else { 'application-local.yaml con las variables DB_* de esta terminal' }
    Detener 2 ("la base resuelta es $($destino.Host)/$($destino.Base) (desde $origen), no localhost/$baseDePruebas. " +
        'Limpia esas variables o corrige el archivo antes de verificar.')
}
if ($env:SPRING_PROFILES_ACTIVE -and $env:SPRING_PROFILES_ACTIVE -ne 'local') {
    Write-Host "Aviso: la terminal tiene SPRING_PROFILES_ACTIVE=$($env:SPRING_PROFILES_ACTIVE); la app se levanta con local."
}
Write-Host "Puerto $puerto libre. Base: $($destino.Host)/$($destino.Base)."

# A partir de aqui, solo los valores del archivo: el proceso hijo no hereda las variables.
$limpia = Destino (Resolver $plantilla.Url $false)
Get-ChildItem env: | Where-Object { $_.Name -like 'DB_*' -or $_.Name -like 'SPRING_R2DBC_*' } |
    ForEach-Object { Remove-Item "env:$($_.Name)" }
$env:SPRING_PROFILES_ACTIVE = 'local'
# El perfil local lee el puerto de SERVER_PORT (PORT solo lo usa Cloud Run): uno heredado de la
# terminal sacaria a la app del puerto validado.
$env:SERVER_PORT = $puerto

$bru = Get-Command bru -ErrorAction SilentlyContinue
if (-not $bru) { Detener 2 'bru no esta en el PATH (FA-39).' }

# 3. Datos, siempre contra localhost explicito.
if ($RecargarDatos) {
    $env:PGPASSWORD = Resolver $plantilla.Clave $false
    $puertoBase = if ($limpia.Puerto) { $limpia.Puerto } else { '5432' }
    & psql -h localhost -p $puertoBase -U (Resolver $plantilla.Usuario $false) -d $baseDePruebas `
        -v ON_ERROR_STOP=1 -q -f (Join-Path $repo 'docs\database\test-data.sql')
    Remove-Item env:PGPASSWORD
    if ($LASTEXITCODE -ne 0) { Detener 3 'la recarga de test-data.sql fallo.' }
}

# 4. Arranque, corrida y apagado.
$carpetaLog = Join-Path $repo 'build\verificar-bruno'
New-Item -ItemType Directory -Force $carpetaLog | Out-Null
$log = Join-Path $carpetaLog 'bootrun.log'
$wrapper = $null
$app = $null
$codigo = 0
try {
    $wrapper = Start-Process -FilePath (Join-Path $repo 'gradlew.bat') -ArgumentList 'bootRun', '--console=plain' `
        -WorkingDirectory $repo -RedirectStandardOutput $log -RedirectStandardError "$log.err" -NoNewWindow -PassThru

    $limite = (Get-Date).AddSeconds($TiempoArranque)
    while ($true) {
        $texto = Get-Content $log -Raw -ErrorAction SilentlyContinue
        if ($texto -match 'Started FinanceappBkApplication') { break }
        if ($texto -match 'APPLICATION FAILED TO START|BUILD FAILED' -or $wrapper.HasExited -or (Get-Date) -gt $limite) {
            Get-Content $log -Tail 30 -ErrorAction SilentlyContinue
            $codigo = 3
            throw 'La app no arranco.'
        }
        Start-Sleep -Milliseconds 500
    }
    # El java de la app es hijo del daemon de Gradle, no del wrapper: se guarda para apagarlo directo.
    $app = QuienEscucha $puerto
    if (-not $app) {
        # Una copia de application-local.yaml que no lea SERVER_PORT la deja en otro puerto: se busca
        # en el log para apagarla igual, porque el taskkill del wrapper no la alcanza.
        $real = if ($texto -match 'Netty started on port (\d+)') { [int]$Matches[1] } else { $null }
        if ($real) { $app = QuienEscucha $real }
        $codigo = 3
        throw ("La app arranco en el puerto $real y no en el $puerto. Revisa que server.port de " +
            'application-local.yaml lea SERVER_PORT.')
    }
    Write-Host "App arriba (PID $app, log en $log)."

    Push-Location (Join-Path $repo 'bruno')
    try {
        $extra = @(@($variablesPuerto) + @($Variables) | Where-Object { $_ } | ForEach-Object { '--env-var', $_ })
        & $bru.Source run $Objetivo -r --env local @extra
        $codigo = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} catch {
    if ($codigo -eq 0) { $codigo = 3 }
    Write-Host $_ -ForegroundColor Red
} finally {
    foreach ($id in @($app, $wrapper.Id) | Where-Object { $_ }) {
        & taskkill /PID $id /T /F 2>$null | Out-Null
    }
    $hasta = (Get-Date).AddSeconds(15)
    while ((QuienEscucha $puerto) -and (Get-Date) -lt $hasta) { Start-Sleep -Milliseconds 500 }
    $sigue = QuienEscucha $puerto
    if ($sigue) {
        Write-Host "Aviso: el puerto $puerto sigue ocupado por PID $sigue tras apagar la app." -ForegroundColor Yellow
        if ($codigo -eq 0) { $codigo = 3 }
    } else {
        Write-Host "App detenida, puerto $puerto libre."
    }
}
exit $codigo
