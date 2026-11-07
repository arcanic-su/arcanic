@echo off
rem SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
rem SPDX-License-Identifier: GPL-3.0-or-later
rem Arcanic - authors: tuffboy999 and Va1der. GPL-3.0 with an attribution term (section 7(b)), see NOTICE.
rem Builds the native launcher: native\build\Arcanic.exe - one file, nothing to install beside it.
rem Needs Visual Studio Build Tools with the C++ workload (cl.exe).
rem No windowsapp.lib: it would import API sets that older Windows lacks and the exe would not even start there. The
rem WinRT pieces (only the --media helper uses them) are delay-loaded instead.
cd /d "%~dp0"
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" ( echo Visual Studio Build Tools not found & exit /b 1 )
for /f "usebackq delims=" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VS=%%i"
if not defined VS ( echo The C++ tools are not installed & exit /b 1 )
call "%VS%\VC\Auxiliary\Build\vcvars64.bat" >nul 2>nul
if not exist build mkdir build
rc /nologo /fo build\res.res res.rc || exit /b 1
rem Release hardening: Control Flow Guard (/guard:cf), Spectre-safe codegen (/Qspectre), and at link time DEP (/NXCOMPAT),
rem ASLR with high-entropy 64-bit addresses (/DYNAMICBASE /HIGHENTROPYVA) and CFG (/GUARD:CF). No /DEBUG, so no PDB or
rem symbols ship with the launcher. These make memory-corruption attacks and DLL-injection much harder.
cl /nologo /std:c++20 /utf-8 /EHsc /O2 /MT /W3 /guard:cf /Qspectre /wd4244 /wd4267 /DUNICODE /D_UNICODE /Fo:build\ /Fe:build\Arcanic.exe main.cpp build\res.res ^
  /link /SUBSYSTEM:WINDOWS /MANIFEST:NO /GUARD:CF /DYNAMICBASE /HIGHENTROPYVA /NXCOMPAT d2d1.lib dwrite.lib windowscodecs.lib winhttp.lib bcrypt.lib shell32.lib user32.lib gdi32.lib ole32.lib oleaut32.lib advapi32.lib crypt32.lib ws2_32.lib kernel32.lib runtimeobject.lib delayimp.lib ^
  /DELAYLOAD:api-ms-win-core-winrt-l1-1-0.dll /DELAYLOAD:api-ms-win-core-winrt-string-l1-1-0.dll /DELAYLOAD:api-ms-win-core-winrt-error-l1-1-1.dll /DELAYLOAD:api-ms-win-core-winrt-error-l1-1-0.dll /IGNORE:4199 || exit /b 1
echo Done: native\build\Arcanic.exe
