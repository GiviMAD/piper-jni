set "TARGET_DIR=src\main\resources\win-%AARCH%"

cmake -Bbuild -DCMAKE_INSTALL_PREFIX="%TARGET_DIR%"
cmake --build build --config Release -j %NUMBER_OF_PROCESSORS%
cmake --install build

for /f "tokens=*" %%F in ('dir /b /s /a:L "%TARGET_DIR%\*.dll" 2^>nul') do (
    set "LINK=%%F"
    for /f "tokens=*" %%T in ('powershell -NoProfile -Command "(Get-Item '!LINK!').Target"') do set "TARGET=%%T"
    if defined TARGET if exist "!TARGET!" (
        for %%D in ("!TARGET!") do set "TARGET_PARENT=%%~dpD"
        del /f /q "!LINK!"
        move /y "!TARGET!" "!LINK!"
        rd "!TARGET_PARENT!" 2>nul
    )
)
