# === CONFIG BUILD & UPLOAD ===
$ServerPanelHost = "srv830711.hstgr.cloud"
$ServerPanelPort = 2224
$PanelUser = "admin"
$PanelPass = "@Hidalgo34290"   # mot de passe du panel Hostinger, pas root

$LocalJar  = "C:\PluginCreator\EntrepriseManager\target\RoleplayCity.jar"
$RemoteJar = "/plugins/RoleplayCity.jar"

$WinScp = "C:\Program Files (x86)\WinSCP\WinSCP.com"

# === CONFIG SSH VPS ===
$SSH = "C:\Windows\System32\OpenSSH\ssh.exe"
$VPSIP = "91.108.102.66"
$RootPass = "@Hidalgo34290"     # mot de passe root VPS

# === BUILD (Maven) ===
Write-Host "? Compilation du plugin..."
mvn -q -DskipTests clean package

# === UPLOAD JAR (SFTP) ===
Write-Host "? Upload du plugin vers le panel Hostinger..."
& "$WinScp" /command `
    "open sftp://$PanelUser:`"$PanelPass`"@$ServerPanelHost:$ServerPanelPort -hostkey=* " `
    "put `"$LocalJar`" `"$RemoteJar`"" `
    "exit"

if ($LASTEXITCODE -ne 0) {
    Write-Error "? Upload ?chou?"
    exit 1
}
Write-Host "? Plugin upload? !"

# === RESTART MINECRAFT AMP INSTANCE ===
Write-Host "? Restart du serveur Minecraft AMP..."
echo "$RootPass" | & $SSH root@$VPSIP -p 22 "su -l amp -c 'ampinstmgr restart SurvivalFrench01'"

if ($LASTEXITCODE -ne 0) {
    Write-Error "? Restart AMP ?chou?"
    exit 1
}

Write-Host "? Serveur SurvivalFrench red?marr? avec succ?s !"
Write-Host "? D?ploiement termin?"
