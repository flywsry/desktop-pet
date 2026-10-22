param(
 [string]$ProjectRoot=$PSScriptRoot,
 [string]$JdkHome=$env:JAVA_HOME,
 [string]$RuntimeHome,
 [string]$BuildRoot,
 [string]$OutputRoot
)
$ErrorActionPreference='Stop'
if(!$JdkHome){
 $javac=Get-Command javac.exe -ErrorAction SilentlyContinue
 if(!$javac){throw 'Set JAVA_HOME to a JDK 17 installation, or pass -JdkHome'}
 $JdkHome=Split-Path (Split-Path $javac.Source -Parent) -Parent
}
if(!$BuildRoot){$BuildRoot=Join-Path $ProjectRoot ('.packaging\'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
if(!$OutputRoot){$OutputRoot=Join-Path $ProjectRoot ('release\'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
if(!$RuntimeHome){
 $archive=Join-Path $ProjectRoot '.packaging/runtime/temurin17-jre.zip'
 if(!(Test-Path -LiteralPath $archive)){throw 'Download and extract a Windows x64 Java 17 runtime, then pass -RuntimeHome <folder>. No runtime archives are tracked in Git.'}
 $metadata=Get-Content -LiteralPath (Join-Path $ProjectRoot '.packaging/runtime/runtime-download.json') -Raw | ConvertFrom-Json
 if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $metadata.sha256){throw 'Cached runtime checksum mismatch'}
 $expanded=Join-Path $ProjectRoot '.packaging/runtime/expanded'
 if(!(Test-Path -LiteralPath $expanded)){Expand-Archive -LiteralPath $archive -DestinationPath $expanded}
 $RuntimeHome=(Get-ChildItem -LiteralPath $expanded -Directory | Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe')} | Select-Object -First 1).FullName
 if(!$RuntimeHome){throw 'Cannot locate cached runtime'}
}
foreach($exe in @('javac.exe','jar.exe','jpackage.exe')){if(!(Test-Path -LiteralPath (Join-Path $JdkHome "bin/$exe"))){throw "Missing build tool: $exe"}}
if(!(Test-Path -LiteralPath (Join-Path $RuntimeHome 'bin/java.exe'))){throw 'RuntimeHome must be an extracted Windows x64 Java 17 runtime'}
New-Item -ItemType Directory -Force -Path "$BuildRoot/src","$BuildRoot/classes","$BuildRoot/input",$OutputRoot | Out-Null
Copy-Item -Path "$ProjectRoot/src/main/java/com/example/pet/*.java" -Destination "$BuildRoot/src"
foreach($name in @('base','graphics','controls')){
 $jar=Join-Path $ProjectRoot ".m2-repository/org/openjfx/javafx-$name/17.0.18/javafx-$name-17.0.18-win.jar"
 if(!(Test-Path -LiteralPath $jar)){$jar=Join-Path $env:USERPROFILE ".m2/repository/org/openjfx/javafx-$name/17.0.18/javafx-$name-17.0.18-win.jar"}
 if(!(Test-Path -LiteralPath $jar)){throw "Resolve dependencies first with mvn compile; missing JavaFX $name"}
 Copy-Item -LiteralPath $jar -Destination "$BuildRoot/input"
}
$sources=(Get-ChildItem -LiteralPath "$BuildRoot/src" -Filter '*.java').FullName
& "$JdkHome/bin/javac.exe" -encoding UTF-8 --release 17 -cp "$BuildRoot/input/*" -d "$BuildRoot/classes" $sources
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
foreach($source in $sources){
 $class=Join-Path "$BuildRoot/classes/com/example/pet" (([IO.Path]::GetFileNameWithoutExtension($source))+'.class')
 if(!(Test-Path -LiteralPath $class)){throw "Class not generated: $class"}
}
& "$JdkHome/bin/jar.exe" --create --file "$BuildRoot/input/desktop-pet.jar" --main-class com.example.pet.Launcher -C "$BuildRoot/classes" .
if($LASTEXITCODE -ne 0){throw 'JAR creation failed'}
& "$JdkHome/bin/jpackage.exe" --type app-image --name DesktopPet --app-version 0.1.1 --vendor DesktopPet --description 'Desktop companion' --input "$BuildRoot/input" --main-jar desktop-pet.jar --main-class com.example.pet.Launcher --runtime-image $RuntimeHome --dest $OutputRoot --java-options '-Dfile.encoding=UTF-8'
if($LASTEXITCODE -ne 0){throw 'jpackage failed'}
$app=Join-Path $OutputRoot 'DesktopPet'
New-Item -ItemType Directory -Force -Path "$app/pets","$app/licenses" | Out-Null
Copy-Item -LiteralPath "$ProjectRoot/pets/default" -Destination "$app/pets" -Recurse
Copy-Item -LiteralPath "$ProjectRoot/pets/rig-template.properties" -Destination "$app/pets"
$readme=@"
小桌宠 Windows x64 免安装版 0.1.1

先解压整个 DesktopPet 文件夹，再双击 DesktopPet.exe。
无需安装 Java、IDEA 或 Maven，无需管理员权限，启动不需要联网下载。
不要只复制 EXE，也不要在压缩包内直接运行。

左键点击互动，按住拖动，快速拖动后松手甩出。
右键可固定位置、调整大小、隐藏到托盘或退出。
双击托盘图标可以找回桌宠。Alt+F4 隐藏，真正结束请选“退出”。

素材：pets/default/pet.png；角色绑定：pets/default/rig.properties。
设置和启动日志：%LOCALAPPDATA%\DesktopPet。
支持 Windows 10/11 x64；其他系统和 ARM 设备需另行构建。

运行时：Eclipse Temurin OpenJDK 17；界面：JavaFX 17.0.18。
许可和第三方说明见 runtime/legal 与 licenses。
"@
[IO.File]::WriteAllText("$app/使用说明.txt",$readme,(New-Object Text.UTF8Encoding($true)))
Copy-Item -Path "$ProjectRoot/packaging/licenses/*" -Destination "$app/licenses/"
$zip=Join-Path $OutputRoot 'DesktopPet-0.1.1-windows-x64.zip'
Compress-Archive -LiteralPath $app -DestinationPath $zip -CompressionLevel Optimal
Write-Output "APP_IMAGE=$app"
Write-Output "ZIP=$zip"