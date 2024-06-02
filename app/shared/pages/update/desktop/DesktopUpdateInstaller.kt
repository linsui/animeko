package me.him188.ani.app.update

import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.FileOpener
import me.him188.ani.app.platform.Platform
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.intellij.lang.annotations.Language
import java.awt.Desktop
import java.io.File
import kotlin.system.exitProcess

interface DesktopUpdateInstaller : UpdateInstaller {
    override fun openForManualInstallation(file: File, context: ContextMP) {
        FileOpener.openInFileBrowser(file)
    }

    companion object {
        fun currentOS(): DesktopUpdateInstaller {
            return when (Platform.currentPlatform) {
                is Platform.Linux -> throw UnsupportedOperationException("Linux is not supported")
                is Platform.MacOS -> MacOSUpdateInstaller
                is Platform.Windows -> WindowsUpdateInstaller
                Platform.Android -> throw IllegalStateException("Android is not a desktop OS")
            }
        }
    }
}

object MacOSUpdateInstaller : DesktopUpdateInstaller {
    override fun install(file: File, context: ContextMP): InstallationResult {
        Desktop.getDesktop().open(file)
        exitProcess(0)
    }
}

object WindowsUpdateInstaller : DesktopUpdateInstaller {
    private val logger = logger<WindowsUpdateInstaller>()

    override fun install(file: File, context: ContextMP): InstallationResult {
        logger.info { "Installing update for Windows" }
        val appDir = File(System.getProperty("user.dir") ?: throw IllegalStateException("Cannot get app directory"))
        logger.info { "Current app dir: ${appDir.absolutePath}" }
        if (!appDir.resolve("Ani.exe").exists()) {
            logger.info { "Current app dir does not have 'Ani.exe'. Fallback to manual update" }
            return InstallationResult.Failed(InstallationFailureReason.UNSUPPORTED_FILE_STRUCTURE)
        }

        val installerScriptFile = appDir.resolve("install.cmd")
        installerScriptFile.writeText(getInstallerScript(file))
        logger.info { "Installer script written to ${installerScriptFile.absolutePath}" }

        val processBuilder = ProcessBuilder("cmd", "/c", "start", "cmd", "/c", installerScriptFile.name)
        processBuilder.directory(appDir).start()
        logger.info { "Installer started" }
        exitProcess(0)
    }

    @Language("cmd")
    private fun getInstallerScript(
        zipFile: File,
    ) = """
setlocal

echo Waiting until Ani.exe ends
:waitloop
tasklist /FI "IMAGENAME eq Ani.exe" 2>NUL | find /I /N "Ani.exe">NUL
if "%ERRORLEVEL%"=="0" (
    timeout /T 1 /NOBREAK > NUL
    goto waitloop
)

echo Deleting the specified files and directories
del /F /Q "Ani.exe"
del /F /Q "Ani.ico"
rmdir /S /Q "app"
rmdir /S /Q "runtime"

echo Extracting update.zip to the current folder
powershell -Command "Expand-Archive -Path '${zipFile.absolutePath}' -DestinationPath '.' -Force"

echo Copying everything from ./Ani to ./
xcopy /E /H /R /Y ".\Ani\*" "."

echo Deleting the extracted Ani directory
rmdir /S /Q ".\Ani"

echo Deleting the update zip file
del /F /Q "${zipFile.absolutePath}"
del /F /Q "${zipFile.absolutePath}.sha256"

echo Launching Ani.exe
start "" ".\Ani.exe"

echo Exiting script
exit
    """.trimIndent()

//    private fun getInstallerBat(
//        zipfile: String,
//    ) = """
//        setlocal
//    
//        set "ZIPFILE=$zipfile"
//        set "ANIPROCESS=Ani.exe"
//        set "TEMP_DIR=Ani"
//    
//        echo "安装包目录: %ZIPFILE%"
//        
//        REM Wait until Ani.exe ends
//        :checkProcess
//        tasklist /FI "IMAGENAME eq %ANIPROCESS%" 2>NUL | find /I /N "%ANIPROCESS%">NUL
//        if "%ERRORLEVEL%"=="0" (
//            echo "正在等待 Ani 程序关闭..."
//            timeout /T 1 >NUL
//            goto checkProcess
//        )
//    
//        REM Extract the zip file
//        echo "正在解压缩安装包 %ZIPFILE%..."
//        powershell -Command "Expand-Archive -Path '%ZIPFILE%' -DestinationPath ."
//        
//        pause
//        
//        echo "正在卸载旧版本..."
//    
//        rmdir /s /q "app"
//        rmdir /s /q "runtime"
//        rmdir /s /q "Ani.exe"
//        rmdir /s /q "Ani.ico"
//        
//        pause
//        
//        REM Copy files from Ani directory to current directory
//        echo "正在安装新版本..."
//        xcopy "%TEMP_DIR%\*" ".\" /E /Y
//        echo "安装成功"
//        
//        pause
//        
//        echo "正在清理临时文件..."
//        
//        rmdir /s /q "Ani"
//        
//        echo "安装全部完成, 正在启动 Ani..."
//        
//        start "" "%ANIPROCESS%"
//        
//        echo "本自动更新工具将在 5 秒后自动关闭..."
//        timeout /t 5 /nobreak >nul
//
//        REM Exit
//        endlocal
//        exit /b 0
//""".trimIndent()

//    del "%ZIPFILE%"
}
