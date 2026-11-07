# Builds dist\Arcanic-Setup.msi from native\build\Arcanic.exe with nothing but Windows itself (the Windows Installer
# automation object and makecab) - no WiX, nothing to install on the build machine.
#
# The installer is per-user: no administrator prompt, the launcher goes to %LOCALAPPDATA%\Programs\Arcanic, gets a
# Start menu and a desktop shortcut and an entry in "Apps" to remove it. A newer .msi replaces an older install by itself
# (same UpgradeCode, a new ProductCode on every build). The game and the settings (~\.arcanic) are not touched by removal.
#
#   powershell -ExecutionPolicy Bypass -File native\installer\build-msi.ps1
param([string]$Exe, [string]$Out)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot)
if (-not $Exe) { $Exe = Join-Path $root 'native\build\Arcanic.exe' }
if (-not $Out) { $Out = Join-Path $root 'dist\Arcanic-Setup.msi' }
$ico = Join-Path $root 'tools\arcanic.ico'
$license = Join-Path $PSScriptRoot 'LICENSE.txt'               # shown in the wizard and put beside the launcher
$upgradeCode = '{6B0E2C54-6E0B-4F0E-9B5D-3A7C1F4E21A7}'          # never changes: this is how an update finds the old install
$componentId = '{C7D1A8F2-52B3-4C8E-8E1B-0F6B9D3A44C1}'
$vi = (Get-Item $Exe).VersionInfo
$version = '{0}.{1}.{2}' -f $vi.FileMajorPart, $vi.FileMinorPart, $vi.FileBuildPart
$fileVersion = "$version.$($vi.FilePrivatePart)"
$size = (Get-Item $Exe).Length

$work = Join-Path $env:TEMP ("arcanic-msi-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $work | Out-Null
try {
    # ---- the cabinet: the file inside is named by its key in the File table
    Copy-Item $Exe (Join-Path $work 'ArcanicExe')
    Copy-Item $license (Join-Path $work 'LicenseTxt')
    $ddf = Join-Path $work 'arc.ddf'
    @('.Set CabinetNameTemplate=arc.cab', ".Set DiskDirectoryTemplate=`"$work`"", '.Set CompressionType=LZX', '.Set CompressionMemory=21', '.Set Cabinet=on', '.Set Compress=on',
      '.Set MaxDiskSize=0', '.Set RptFileName=nul', '.Set InfFileName=nul',
      "`"$(Join-Path $work 'ArcanicExe')`" ArcanicExe", "`"$(Join-Path $work 'LicenseTxt')`" LicenseTxt") | Set-Content $ddf -Encoding ascii
    & makecab.exe /F $ddf | Out-Null
    if (-not (Test-Path (Join-Path $work 'arc.cab'))) { throw 'makecab failed' }

    $msi = Join-Path $work 'out.msi'
    $bf = [Reflection.BindingFlags]
    # (values that passed through PowerShell are wrapped; COM wants them bare)
    function Bare($a) { ,[object[]]@($a | ForEach-Object { if ($_ -is [psobject]) { $_.PSObject.BaseObject } else { $_ } }) }
    function Call($o, $name, $a = @()) { $o.GetType().InvokeMember($name, $bf::InvokeMethod, $null, $o, (Bare $a)) }
    function PGet($o, $name, $a = @()) { $o.GetType().InvokeMember($name, $bf::GetProperty, $null, $o, (Bare $a)) }
    function PSet($o, $name, $a) { $o.GetType().InvokeMember($name, $bf::SetProperty, $null, $o, (Bare $a)) | Out-Null }
    $installer = New-Object -ComObject WindowsInstaller.Installer
    $db = Call $installer 'OpenDatabase' @($msi, 3)
    function Sql($q) { $v = Call $db 'OpenView' @($q); Call $v 'Execute' | Out-Null; Call $v 'Close' | Out-Null; [Runtime.InteropServices.Marshal]::ReleaseComObject($v) | Out-Null }
    function Row($table, [object[]]$values) {
        # the values go in the order the table was created with, as parameters (so any text is safe)
        $cols = $script:columns[$table]; $n = $values.Count
        $rec = Call $installer 'CreateRecord' @($n)
        for ($k = 0; $k -lt $n; $k++) {
            $x = $values[$k]
            if ($null -eq $x) { continue }
            if ($x -is [int] -or $x -is [long]) { PSet $rec 'IntegerData' @(($k + 1), [int]$x) } else { PSet $rec 'StringData' @(($k + 1), [string]$x) }
        }
        $v = Call $db 'OpenView' @("INSERT INTO ``$table`` ($cols) VALUES (" + ((1..$n | ForEach-Object { '?' }) -join ', ') + ")")
        Call $v 'Execute' @($rec) | Out-Null; Call $v 'Close' | Out-Null
        [Runtime.InteropServices.Marshal]::ReleaseComObject($v) | Out-Null; [Runtime.InteropServices.Marshal]::ReleaseComObject($rec) | Out-Null
    }
    function Stream($q, $file) {
        $rec = Call $installer 'CreateRecord' @(1); Call $rec 'SetStream' @(1, $file) | Out-Null
        $v = Call $db 'OpenView' @($q); Call $v 'Execute' @($rec) | Out-Null; Call $v 'Close' | Out-Null
        [Runtime.InteropServices.Marshal]::ReleaseComObject($v) | Out-Null; [Runtime.InteropServices.Marshal]::ReleaseComObject($rec) | Out-Null
    }
    $script:columns = @{}
    function Table($name, $defs, $key) {
        Sql "CREATE TABLE ``$name`` ($defs PRIMARY KEY $key)"
        $script:columns[$name] = ([regex]::Matches($defs, '`([^`]+)`') | ForEach-Object { '`' + $_.Groups[1].Value + '`' }) -join ', '
    }

    # ---- the schema (the standard Windows Installer tables this package uses)
    Table 'Property' '`Property` CHAR(72) NOT NULL, `Value` LONGCHAR NOT NULL LOCALIZABLE' '`Property`'
    Table 'Directory' '`Directory` CHAR(72) NOT NULL, `Directory_Parent` CHAR(72), `DefaultDir` CHAR(255) NOT NULL LOCALIZABLE' '`Directory`'
    Table 'Component' '`Component` CHAR(72) NOT NULL, `ComponentId` CHAR(38), `Directory_` CHAR(72) NOT NULL, `Attributes` SHORT NOT NULL, `Condition` CHAR(255), `KeyPath` CHAR(72)' '`Component`'
    Table 'Feature' '`Feature` CHAR(38) NOT NULL, `Feature_Parent` CHAR(38), `Title` CHAR(64) LOCALIZABLE, `Description` CHAR(255) LOCALIZABLE, `Display` SHORT, `Level` SHORT NOT NULL, `Directory_` CHAR(72), `Attributes` SHORT NOT NULL' '`Feature`'
    Table 'FeatureComponents' '`Feature_` CHAR(38) NOT NULL, `Component_` CHAR(72) NOT NULL' '`Feature_`, `Component_`'
    Table 'File' '`File` CHAR(72) NOT NULL, `Component_` CHAR(72) NOT NULL, `FileName` CHAR(255) NOT NULL LOCALIZABLE, `FileSize` LONG NOT NULL, `Version` CHAR(72), `Language` CHAR(20), `Attributes` SHORT, `Sequence` LONG NOT NULL' '`File`'
    Table 'Media' '`DiskId` SHORT NOT NULL, `LastSequence` LONG NOT NULL, `DiskPrompt` CHAR(64) LOCALIZABLE, `Cabinet` CHAR(255), `VolumeLabel` CHAR(32), `Source` CHAR(72)' '`DiskId`'
    Table 'Shortcut' '`Shortcut` CHAR(72) NOT NULL, `Directory_` CHAR(72) NOT NULL, `Name` CHAR(128) NOT NULL LOCALIZABLE, `Component_` CHAR(72) NOT NULL, `Target` CHAR(72) NOT NULL, `Arguments` CHAR(255), `Description` CHAR(255) LOCALIZABLE, `Hotkey` SHORT, `Icon_` CHAR(72), `IconIndex` SHORT, `ShowCmd` SHORT, `WkDir` CHAR(72)' '`Shortcut`'
    Table 'Icon' '`Name` CHAR(72) NOT NULL, `Data` OBJECT NOT NULL' '`Name`'
    Table 'Registry' '`Registry` CHAR(72) NOT NULL, `Root` SHORT NOT NULL, `Key` CHAR(255) NOT NULL LOCALIZABLE, `Name` CHAR(255) LOCALIZABLE, `Value` LONGCHAR LOCALIZABLE, `Component_` CHAR(72) NOT NULL' '`Registry`'
    Table 'RemoveFile' '`FileKey` CHAR(72) NOT NULL, `Component_` CHAR(72) NOT NULL, `FileName` CHAR(255) LOCALIZABLE, `DirProperty` CHAR(72) NOT NULL, `InstallMode` SHORT NOT NULL' '`FileKey`'
    Table 'Upgrade' '`UpgradeCode` CHAR(38) NOT NULL, `VersionMin` CHAR(20), `VersionMax` CHAR(20), `Language` CHAR(255), `Attributes` LONG NOT NULL, `Remove` CHAR(255), `ActionProperty` CHAR(72) NOT NULL' '`UpgradeCode`, `VersionMin`, `VersionMax`, `Language`, `Attributes`'
    Table 'CustomAction' '`Action` CHAR(72) NOT NULL, `Type` SHORT NOT NULL, `Source` CHAR(72), `Target` CHAR(255)' '`Action`'
    # the wizard
    Table 'Dialog' '`Dialog` CHAR(72) NOT NULL, `HCentering` SHORT NOT NULL, `VCentering` SHORT NOT NULL, `Width` SHORT NOT NULL, `Height` SHORT NOT NULL, `Attributes` LONG, `Title` CHAR(128) LOCALIZABLE, `Control_First` CHAR(50) NOT NULL, `Control_Default` CHAR(50), `Control_Cancel` CHAR(50)' '`Dialog`'
    Table 'Control' '`Dialog_` CHAR(72) NOT NULL, `Control` CHAR(50) NOT NULL, `Type` CHAR(20) NOT NULL, `X` SHORT NOT NULL, `Y` SHORT NOT NULL, `Width` SHORT NOT NULL, `Height` SHORT NOT NULL, `Attributes` LONG, `Property` CHAR(50), `Text` LONGCHAR LOCALIZABLE, `Control_Next` CHAR(50), `Help` CHAR(50) LOCALIZABLE' '`Dialog_`, `Control`'
    Table 'ControlEvent' '`Dialog_` CHAR(72) NOT NULL, `Control_` CHAR(50) NOT NULL, `Event` CHAR(50) NOT NULL, `Argument` CHAR(255) NOT NULL, `Condition` CHAR(255), `Ordering` SHORT' '`Dialog_`, `Control_`, `Event`, `Argument`, `Condition`'
    Table 'ControlCondition' '`Dialog_` CHAR(72) NOT NULL, `Control_` CHAR(50) NOT NULL, `Action` CHAR(50) NOT NULL, `Condition` CHAR(255) NOT NULL' '`Dialog_`, `Control_`, `Action`, `Condition`'
    Table 'EventMapping' '`Dialog_` CHAR(72) NOT NULL, `Control_` CHAR(50) NOT NULL, `Event` CHAR(50) NOT NULL, `Attribute` CHAR(50) NOT NULL' '`Dialog_`, `Control_`, `Event`'
    Table 'TextStyle' '`TextStyle` CHAR(72) NOT NULL, `FaceName` CHAR(32) NOT NULL, `Size` SHORT NOT NULL, `Color` LONG, `StyleBits` SHORT' '`TextStyle`'
    Table 'UIText' '`Key` CHAR(72) NOT NULL, `Text` CHAR(255) LOCALIZABLE' '`Key`'
    Table 'CheckBox' '`Property` CHAR(72) NOT NULL, `Value` CHAR(64)' '`Property`'
    Table 'Binary' '`Name` CHAR(72) NOT NULL, `Data` OBJECT NOT NULL' '`Name`'
    Table 'ActionText' '`Action` CHAR(72) NOT NULL, `Description` LONGCHAR LOCALIZABLE, `Template` LONGCHAR LOCALIZABLE' '`Action`'
    foreach ($t in 'InstallExecuteSequence', 'InstallUISequence', 'AdminExecuteSequence') { Table $t '`Action` CHAR(72) NOT NULL, `Condition` CHAR(255), `Sequence` SHORT' '`Action`' }

    # ---- what the product is
    $productCode = '{' + [guid]::NewGuid().ToString().ToUpper() + '}'
    foreach ($p in @(
        @('ProductName', 'Arcanic'), @('Manufacturer', 'Arcanic'), @('ProductVersion', $version), @('ProductLanguage', '1033'),
        @('ProductCode', $productCode), @('UpgradeCode', $upgradeCode), @('SecureCustomProperties', 'OLDFOUND;INSTALLDIR;DESKTOPSHORTCUT'),
        @('DefaultUIFont', 'Dlg'), @('ErrorDialog', 'ErrorDlg'), @('_BrowseProperty', 'INSTALLDIR'), @('DESKTOPSHORTCUT', '1'), @('LAUNCHAPP', '1'),
        @('ARPPRODUCTICON', 'app.ico'), @('ARPNOMODIFY', '1'), @('ARPNOREPAIR', '1'), @('ARPURLINFOABOUT', 'https://arcanic.su'),
        @('MSIFASTINSTALL', '7'))) { Row 'Property' $p }          # 7: no restore point, quick costing, fewer progress messages

    # ---- where it goes: %LOCALAPPDATA%\Programs\Arcanic, no administrator rights
    Row 'Directory' @('TARGETDIR', $null, 'SourceDir')
    Row 'Directory' @('LocalAppDataFolder', 'TARGETDIR', '.')
    Row 'Directory' @('ProgramsDir', 'LocalAppDataFolder', 'Programs')
    Row 'Directory' @('INSTALLDIR', 'ProgramsDir', 'Arcanic')
    Row 'Directory' @('ProgramMenuFolder', 'TARGETDIR', '.')
    Row 'Directory' @('DesktopFolder', 'TARGETDIR', '.')

    Row 'Feature' @('Main', $null, 'Arcanic', 'Arcanic launcher', 1, 1, 'INSTALLDIR', 0)
    Row 'Component' @('Launcher', $componentId, 'INSTALLDIR', 256, $null, 'ArcanicExe')    # 256: a 64-bit component
    Row 'FeatureComponents' @('Main', 'Launcher')
    Row 'File' @('ArcanicExe', 'Launcher', 'ARCANIC.EXE|Arcanic.exe', [long]$size, $fileVersion, '1049', 16384, 1)   # 16384: stored compressed in the cabinet
    Row 'File' @('LicenseTxt', 'Launcher', 'LICENSE.TXT|LICENSE.txt', [long](Get-Item $license).Length, $null, $null, 16384, 2)
    Row 'Media' @(1, 2, $null, '#arc.cab', $null, $null)
    Row 'Shortcut' @('StartMenu', 'ProgramMenuFolder', 'ARCANIC|Arcanic', 'Launcher', '[#ArcanicExe]', $null, 'Arcanic launcher', $null, $null, $null, 1, 'INSTALLDIR')
    # the desktop shortcut is its own component: the wizard has a checkbox for it
    Row 'Component' @('DesktopLink', '{2F8A6C1D-9B47-4E53-A0C8-7D15E3B9F642}', 'DesktopFolder', 4, 'DESKTOPSHORTCUT', 'DesktopLinkKey')   # 4: the key path is a registry value
    Row 'FeatureComponents' @('Main', 'DesktopLink')
    Row 'Registry' @('DesktopLinkKey', 1, 'Software\Arcanic\Launcher', 'DesktopShortcut', '1', 'DesktopLink')
    Row 'Shortcut' @('Desktop', 'DesktopFolder', 'ARCANIC|Arcanic', 'DesktopLink', '[#ArcanicExe]', $null, 'Arcanic launcher', $null, $null, $null, 1, 'INSTALLDIR')
    Row 'Registry' @('InstallDir', 1, 'Software\Arcanic\Launcher', 'InstallDir', '[INSTALLDIR]', 'Launcher')
    Row 'Registry' @('Version', 1, 'Software\Arcanic\Launcher', 'Version', $version, 'Launcher')
    Row 'RemoveFile' @('RemoveInstallDir', 'Launcher', $null, 'INSTALLDIR', 2)
    # an older (or the same) version found on the PC is removed first, so an update is just "run the new .msi"
    Row 'Upgrade' @($upgradeCode, '0.0.0', $version, $null, 768, $null, 'OLDFOUND')
    # the launcher starts from the last page of the wizard (its checkbox), or by itself after a progress-bar-only install
    Row 'CustomAction' @('LaunchApp', 210, 'ArcanicExe', $null)

    foreach ($a in @(
        @('FindRelatedProducts', $null, 25), @('LaunchConditions', $null, 100), @('ValidateProductID', $null, 700),
        @('CostInitialize', $null, 800), @('FileCost', $null, 900), @('CostFinalize', $null, 1000), @('MigrateFeatureStates', $null, 1200),
        @('InstallValidate', $null, 1400), @('RemoveExistingProducts', $null, 1401), @('InstallInitialize', $null, 1500),
        @('ProcessComponents', $null, 1600), @('UnpublishFeatures', $null, 1800), @('RemoveRegistryValues', $null, 2600),
        @('RemoveShortcuts', $null, 3200), @('RemoveFiles', $null, 3500), @('InstallFiles', $null, 4000), @('CreateShortcuts', $null, 4500),
        @('WriteRegistryValues', $null, 5000), @('RegisterUser', $null, 6000), @('RegisterProduct', $null, 6100),
        @('PublishFeatures', $null, 6300), @('PublishProduct', $null, 6400), @('InstallFinalize', $null, 6600),
        @('LaunchApp', 'NOT Installed AND UILevel = 3', 6700))) { Row 'InstallExecuteSequence' $a }
    foreach ($a in @(
        @('FindRelatedProducts', $null, 25), @('LaunchConditions', $null, 100), @('ValidateProductID', $null, 700),
        @('CostInitialize', $null, 800), @('FileCost', $null, 900), @('CostFinalize', $null, 1000), @('MigrateFeatureStates', $null, 1200),
        @('FatalError', $null, -3), @('UserExit', $null, -2), @('ExitDlg', $null, -1),
        @('WelcomeDlg', 'NOT Installed', 1230), @('MaintenanceDlg', 'Installed', 1235), @('ProgressDlg', $null, 1280),
        @('ExecuteAction', $null, 1300))) { Row 'InstallUISequence' $a }

    # ---- the wizard: Welcome -> folder (+ desktop shortcut) -> progress -> finish (+ start the launcher)
    Row 'TextStyle' @('Dlg', 'Segoe UI', 9, $null, 0)
    Row 'TextStyle' @('Title', 'Segoe UI', 15, $null, 1)
    Row 'UIText' @('NewFolder', 'Folder|New Folder')
    Row 'UIText' @('AbsentPath', $null)
    Row 'CheckBox' @('DESKTOPSHORTCUT', '1')
    Row 'CheckBox' @('LAUNCHAPP', '1')
    Row 'CheckBox' @('LICENSEACCEPTED', '1')
    foreach ($a in @(
        @('InstallFiles', 'Copying the launcher', $null), @('CreateShortcuts', 'Creating shortcuts', $null), @('WriteRegistryValues', 'Saving settings', $null),
        @('RemoveExistingProducts', 'Removing the previous version', $null), @('RemoveFiles', 'Removing files', $null), @('RemoveShortcuts', 'Removing shortcuts', $null),
        @('RegisterProduct', 'Registering the program', $null), @('PublishProduct', 'Finishing', $null), @('InstallValidate', 'Checking the installation', $null))) { Row 'ActionText' $a }

    $W = 370; $H = 270; $TXT = 196611; $BTN = 3                  # text: visible, enabled, transparent, "&" is not a shortcut mark
    function Dlg($name, $first, $default, $cancel, $attr = 3, $w = $W, $h = $H) { Row 'Dialog' @($name, 50, 50, $w, $h, $attr, 'Arcanic Setup', $first, $default, $cancel) }
    function Ctl($dlg, $name, $type, $x, $y, $w, $h, $attr, $prop, $text, $next) { Row 'Control' @($dlg, $name, $type, $x, $y, $w, $h, $attr, $prop, $text, $next, $null) }
    function Ev($dlg, $ctl, $event, $arg, $cond = '1', $order = 1) { Row 'ControlEvent' @($dlg, $ctl, $event, $arg, $cond, $order) }
    function Page($dlg, $title, $text) {                         # what every full page shares: the icon, the heading, the text, the line over the buttons
        Ctl $dlg 'Logo' 'Icon' 20 18 32 32 5242881 $null 'AppIco' $null
        Ctl $dlg 'Title' 'Text' 64 20 290 26 $TXT $null ('{\Title}' + $title) $null
        if ($text) { Ctl $dlg 'Desc' 'Text' 64 52 290 70 $TXT $null $text $null }
        Ctl $dlg 'Line' 'Line' 0 234 $W 0 1 $null $null $null
    }

    Dlg 'WelcomeDlg' 'Next' 'Next' 'Cancel'
    Page 'WelcomeDlg' 'Welcome to Arcanic' "This wizard installs the Arcanic launcher [ProductVersion].`r`n`r`nNo administrator rights are needed. Minecraft, Java and the mods are downloaded by the launcher itself the first time you press Play."
    Ctl 'WelcomeDlg' 'Next' 'PushButton' 236 243 56 17 $BTN $null 'Next' 'Cancel'
    Ctl 'WelcomeDlg' 'Cancel' 'PushButton' 304 243 56 17 $BTN $null 'Cancel' 'Next'
    Ev 'WelcomeDlg' 'Next' 'NewDialog' 'LicenseDlg'

    # the license: the text of LICENSE.txt as RTF (anything outside ASCII goes in as \uN, so no code page is involved)
    $sb = New-Object Text.StringBuilder
    [void]$sb.Append('{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0\fnil Segoe UI;}}\f0\fs16 ')
    foreach ($ch in [IO.File]::ReadAllText($license, [Text.Encoding]::UTF8).Replace("`r", '').ToCharArray()) {
        $c = [int]$ch
        if ($ch -eq "`n") { [void]$sb.Append('\par ') }
        elseif ($ch -eq '\' -or $ch -eq '{' -or $ch -eq '}') { [void]$sb.Append('\').Append($ch) }
        elseif ($c -lt 128) { [void]$sb.Append($ch) }
        else { if ($c -gt 32767) { $c -= 65536 }; [void]$sb.Append('\u').Append($c).Append('?') }
    }
    [void]$sb.Append('}')
    Dlg 'LicenseDlg' 'Accept' 'Next' 'Cancel'
    Page 'LicenseDlg' 'License' $null
    Ctl 'LicenseDlg' 'Lead' 'Text' 64 50 290 12 $TXT $null 'Arcanic is free software under the GNU General Public License v3:' $null
    Ctl 'LicenseDlg' 'License' 'ScrollableText' 20 66 334 138 7 $null $sb.ToString() 'Accept'
    Ctl 'LicenseDlg' 'Accept' 'CheckBox' 20 210 334 14 $BTN 'LICENSEACCEPTED' 'I accept the terms of the license' 'Back'
    Ctl 'LicenseDlg' 'Back' 'PushButton' 174 243 56 17 $BTN $null 'Back' 'Next'
    Ctl 'LicenseDlg' 'Next' 'PushButton' 236 243 56 17 $BTN $null 'Next' 'Cancel'
    Ctl 'LicenseDlg' 'Cancel' 'PushButton' 304 243 56 17 $BTN $null 'Cancel' 'License'
    Row 'ControlCondition' @('LicenseDlg', 'Next', 'Disable', 'LICENSEACCEPTED <> "1"')
    Row 'ControlCondition' @('LicenseDlg', 'Next', 'Enable', 'LICENSEACCEPTED = "1"')
    Ev 'LicenseDlg' 'Back' 'NewDialog' 'WelcomeDlg'
    Ev 'LicenseDlg' 'Next' 'NewDialog' 'InstallDirDlg'
    Ev 'LicenseDlg' 'Cancel' 'SpawnDialog' 'CancelDlg'
    Ev 'WelcomeDlg' 'Cancel' 'SpawnDialog' 'CancelDlg'

    Dlg 'InstallDirDlg' 'Next' 'Next' 'Cancel'
    Page 'InstallDirDlg' 'Where to install' $null
    Ctl 'InstallDirDlg' 'FolderLabel' 'Text' 64 58 290 12 $TXT $null 'Install Arcanic to this folder:' $null
    Ctl 'InstallDirDlg' 'Folder' 'PathEdit' 64 72 222 18 11 '_BrowseProperty' $null 'Browse'
    Ctl 'InstallDirDlg' 'Browse' 'PushButton' 292 72 62 17 $BTN $null 'Browse...' 'Desktop'
    Ctl 'InstallDirDlg' 'Desktop' 'CheckBox' 64 104 290 14 $BTN 'DESKTOPSHORTCUT' 'Create a shortcut on the desktop' 'Back'
    Ctl 'InstallDirDlg' 'Note' 'Text' 64 130 290 40 $TXT $null 'The game itself is kept in a separate folder; it can be moved later in the launcher settings.' $null
    Ctl 'InstallDirDlg' 'Back' 'PushButton' 174 243 56 17 $BTN $null 'Back' 'Next'
    Ctl 'InstallDirDlg' 'Next' 'PushButton' 236 243 56 17 $BTN $null 'Install' 'Cancel'
    Ctl 'InstallDirDlg' 'Cancel' 'PushButton' 304 243 56 17 $BTN $null 'Cancel' 'Folder'
    Ev 'InstallDirDlg' 'Browse' 'SpawnDialog' 'BrowseDlg'
    Ev 'InstallDirDlg' 'Back' 'NewDialog' 'LicenseDlg'
    Ev 'InstallDirDlg' 'Next' 'SetTargetPath' '[_BrowseProperty]' '1' 1
    Ev 'InstallDirDlg' 'Next' 'EndDialog' 'Return' '1' 2
    Ev 'InstallDirDlg' 'Cancel' 'SpawnDialog' 'CancelDlg'

    Dlg 'BrowseDlg' 'Path' 'OK' 'Cancel'
    Ctl 'BrowseDlg' 'Title' 'Text' 20 14 330 26 $TXT $null '{\Title}Choose a folder' $null
    Ctl 'BrowseDlg' 'ComboLabel' 'Text' 20 50 44 12 $TXT $null 'Look in:' $null
    Ctl 'BrowseDlg' 'Combo' 'DirectoryCombo' 66 48 224 80 458763 '_BrowseProperty' $null 'Up'
    Ctl 'BrowseDlg' 'Up' 'PushButton' 296 48 54 17 $BTN $null 'Up' 'List'
    Ctl 'BrowseDlg' 'List' 'DirectoryList' 20 72 330 118 11 '_BrowseProperty' $null 'Path'
    Ctl 'BrowseDlg' 'PathLabel' 'Text' 20 198 330 12 $TXT $null 'Folder:' $null
    Ctl 'BrowseDlg' 'Path' 'PathEdit' 20 210 330 18 11 '_BrowseProperty' $null 'OK'
    Ctl 'BrowseDlg' 'Line' 'Line' 0 234 $W 0 1 $null $null $null
    Ctl 'BrowseDlg' 'OK' 'PushButton' 236 243 56 17 $BTN $null 'OK' 'Cancel'
    Ctl 'BrowseDlg' 'Cancel' 'PushButton' 304 243 56 17 $BTN $null 'Cancel' 'Combo'
    Ev 'BrowseDlg' 'Up' 'DirectoryListUp' '0'
    Ev 'BrowseDlg' 'OK' 'SetTargetPath' '[_BrowseProperty]' '1' 1
    Ev 'BrowseDlg' 'OK' 'EndDialog' 'Return' '1' 2
    Ev 'BrowseDlg' 'Cancel' 'Reset' '0' '1' 1
    Ev 'BrowseDlg' 'Cancel' 'EndDialog' 'Return' '1' 2

    Dlg 'ProgressDlg' 'Cancel' 'Cancel' 'Cancel' 5               # 5: visible, not modal (the install runs behind it), can be minimised
    Page 'ProgressDlg' 'Please wait' $null
    Ctl 'ProgressDlg' 'ActionText' 'Text' 64 70 290 12 $TXT $null $null $null
    Ctl 'ProgressDlg' 'Bar' 'ProgressBar' 64 88 290 10 65537 $null 'Progress' $null
    Ctl 'ProgressDlg' 'Cancel' 'PushButton' 304 243 56 17 $BTN $null 'Cancel' $null
    Ev 'ProgressDlg' 'Cancel' 'SpawnDialog' 'CancelDlg'
    Row 'EventMapping' @('ProgressDlg', 'ActionText', 'ActionText', 'Text')
    Row 'EventMapping' @('ProgressDlg', 'Bar', 'SetProgress', 'Progress')

    Dlg 'ExitDlg' 'Finish' 'Finish' 'Finish'
    Page 'ExitDlg' 'Done' $null
    Ctl 'ExitDlg' 'DoneInstall' 'Text' 64 52 290 30 $TXT $null 'Arcanic is installed. You can start it from the Start menu or the desktop.' $null
    Ctl 'ExitDlg' 'DoneRemove' 'Text' 64 52 290 30 $TXT $null 'Arcanic was removed from this computer. The game files and your settings were kept.' $null
    Ctl 'ExitDlg' 'Launch' 'CheckBox' 64 92 290 14 $BTN 'LAUNCHAPP' 'Start Arcanic now' 'Finish'
    Ctl 'ExitDlg' 'Finish' 'PushButton' 304 243 56 17 $BTN $null 'Finish' 'Launch'
    Row 'ControlCondition' @('ExitDlg', 'DoneInstall', 'Hide', 'Installed')
    Row 'ControlCondition' @('ExitDlg', 'DoneRemove', 'Hide', 'NOT Installed')
    Row 'ControlCondition' @('ExitDlg', 'Launch', 'Hide', 'Installed')
    Ev 'ExitDlg' 'Finish' 'DoAction' 'LaunchApp' 'LAUNCHAPP = 1 AND NOT Installed' 1
    Ev 'ExitDlg' 'Finish' 'EndDialog' 'Return' '1' 2

    foreach ($d in @(@('FatalError', 'Setup did not finish', 'Something went wrong and nothing was changed on this computer. Run the installer again; if it fails again, tell us in Discord.'),
                     @('UserExit', 'Setup was cancelled', 'Nothing was changed on this computer. Run the installer again whenever you like.'))) {
        Dlg $d[0] 'Finish' 'Finish' 'Finish'
        Page $d[0] $d[1] $d[2]
        Ctl $d[0] 'Finish' 'PushButton' 304 243 56 17 $BTN $null 'Close' $null
        Ev $d[0] 'Finish' 'EndDialog' 'Exit'
    }

    Dlg 'MaintenanceDlg' 'Cancel' 'Cancel' 'Cancel'
    Page 'MaintenanceDlg' 'Arcanic is already installed' 'This version of Arcanic is already on this computer. You can remove it here; the game files and your settings are kept.'
    Ctl 'MaintenanceDlg' 'Remove' 'PushButton' 222 243 70 17 $BTN $null 'Remove' 'Cancel'
    Ctl 'MaintenanceDlg' 'Cancel' 'PushButton' 304 243 56 17 $BTN $null 'Close' 'Remove'
    Ev 'MaintenanceDlg' 'Remove' '[REMOVE]' 'ALL' '1' 1
    Ev 'MaintenanceDlg' 'Remove' 'EndDialog' 'Return' '1' 2
    Ev 'MaintenanceDlg' 'Cancel' 'EndDialog' 'Exit'

    Dlg 'CancelDlg' 'No' 'No' 'No' 3 240 80
    Ctl 'CancelDlg' 'Text' 'Text' 20 16 200 24 $TXT $null 'Stop the Arcanic setup?' $null
    Ctl 'CancelDlg' 'Yes' 'PushButton' 60 52 56 17 $BTN $null 'Yes' 'No'
    Ctl 'CancelDlg' 'No' 'PushButton' 124 52 56 17 $BTN $null 'No' 'Yes'
    Ev 'CancelDlg' 'Yes' 'EndDialog' 'Exit'
    Ev 'CancelDlg' 'No' 'EndDialog' 'Return'

    # the box Windows Installer uses for its own messages (a file in use, no rights for the folder, ...)
    Dlg 'ErrorDlg' 'ErrorText' $null $null 65539 270 105
    Ctl 'ErrorDlg' 'ErrorText' 'Text' 20 14 230 54 $TXT $null $null $null
    foreach ($b in @(@('Y', 'Yes', 'ErrorYes'), @('N', 'No', 'ErrorNo'), @('A', 'Cancel', 'ErrorAbort'), @('C', 'Cancel', 'ErrorCancel'), @('I', 'Ignore', 'ErrorIgnore'), @('O', 'OK', 'ErrorOk'), @('R', 'Retry', 'ErrorRetry'))) {
        Ctl 'ErrorDlg' $b[0] 'PushButton' 100 80 56 17 $BTN $null $b[1] $null
        Ev 'ErrorDlg' $b[0] 'EndDialog' $b[2]
    }
    Stream "INSERT INTO ``Binary`` (``Name``, ``Data``) VALUES ('AppIco', ?)" $ico
    foreach ($a in @(
        @('CostInitialize', $null, 800), @('FileCost', $null, 900), @('CostFinalize', $null, 1000), @('InstallValidate', $null, 1400),
        @('InstallInitialize', $null, 1500), @('InstallAdminPackage', $null, 3900), @('InstallFiles', $null, 4000), @('InstallFinalize', $null, 6600))) { Row 'AdminExecuteSequence' $a }

    Stream "INSERT INTO ``Icon`` (``Name``, ``Data``) VALUES ('app.ico', ?)" $ico
    Stream "INSERT INTO ``_Streams`` (``Name``, ``Data``) VALUES ('arc.cab', ?)" (Join-Path $work 'arc.cab')

    # ---- the summary: 64-bit, files in a cabinet, no elevation needed
    $si = PGet $db 'SummaryInformation' @(20)
    PSet $si 'Property' @(1, 1252)
    PSet $si 'Property' @(2, 'Installation Database')
    PSet $si 'Property' @(3, "Arcanic $version")
    PSet $si 'Property' @(4, 'Arcanic')
    PSet $si 'Property' @(5, 'Installer')
    PSet $si 'Property' @(6, 'Installs the Arcanic launcher')
    PSet $si 'Property' @(7, 'x64;1033')
    PSet $si 'Property' @(9, ('{' + [guid]::NewGuid().ToString().ToUpper() + '}'))   # the package code: new for every build
    PSet $si 'Property' @(14, 500)
    PSet $si 'Property' @(15, 10)                                 # 2: compressed source, 8: no administrator rights needed
    PSet $si 'Property' @(18, 'Arcanic build-msi.ps1')
    PSet $si 'Property' @(19, 2)
    Call $si 'Persist' | Out-Null
    Call $db 'Commit' | Out-Null
    foreach ($o in $si, $db, $installer) { [Runtime.InteropServices.Marshal]::ReleaseComObject($o) | Out-Null }
    [GC]::Collect(); [GC]::WaitForPendingFinalizers()

    New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null
    Copy-Item $msi $Out -Force
    "Done: $Out  (Arcanic $version, $([math]::Round((Get-Item $Out).Length / 1MB, 2)) MB)"
} finally {
    [GC]::Collect(); [GC]::WaitForPendingFinalizers()
    try { [IO.Directory]::Delete($work, $true) } catch {}
}

