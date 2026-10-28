param([string]$ConfigPath = (Join-Path $PSScriptRoot 'config.json'))
$ErrorActionPreference = 'Stop'
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$email = Read-Host 'Correo de la cuenta Firebase del agente'
$password = Read-Host 'Contraseña del agente' -AsSecureString
$apiKey = Read-Host 'Clave Gemini (Enter para informes locales)' -AsSecureString
function Unseal([Security.SecureString]$value) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$secretJson = @{ email=$email; password=(Unseal $password); geminiKey=(Unseal $apiKey) } | ConvertTo-Json -Compress
$directory = Split-Path -Parent $config.secretFile
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$secretJson | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $config.secretFile
$secretJson = $null
Write-Host 'Credenciales protegidas para tu usuario de Windows.'
