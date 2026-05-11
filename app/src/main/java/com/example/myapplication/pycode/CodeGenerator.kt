package com.example.myapplication.pycode

import com.example.myapplication.data.SkillRepository

object CodeGenerator {

    // Генерация полного кода агента (базовый шаблон + выбранные навыки)
    fun generateFullAgent(selectedSkillIds: List<String>): String {
        val base = BaseAgentTemplate.template

        val extraFilesCode = selectedSkillIds.joinToString("\n") { skillId ->
            val skillCode = SkillModules.skillCodeById(skillId)
            // Экранируем обратные слеши и тройные кавычки, чтобы код можно было вставить в r-строку
            val escaped = skillCode
                .replace("\\", "\\\\")
                .replace("\"\"\"", "\\\"\\\"\\\"") // если будут тройные двойные кавычки
                .replace("'''", "\\'\\'\\'")       // если тройные одинарные
            """
    # Добавление навыка $skillId
    skill_${skillId}_code = '''\\
$escaped'''.lstrip('\n')
    with open(os.path.join(project_name, "skills", "$skillId.py"), "w", encoding="utf-8") as f:
        f.write(skill_${skillId}_code)
        """.trimIndent()
        }

        return base.replace("# {{EXTRA_SKILL_FILES_PLACEHOLDER}}", extraFilesCode)
    }

    // Извлекаем скрипт сборки EXE из шаблона
    fun getBuildExeScript(): String {
        // build_exe код уже содержится внутри BaseAgentTemplate.template
        // Мы можем просто вернуть его отдельно (он всегда одинаков)
        return """
import subprocess
import sys

def build_exe():
    subprocess.check_call([
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--name", "MyAgent",
        "--distpath", "./dist",
        "agent.py"
    ])
    print("EXE-файл создан в папке ./dist")

if __name__ == "__main__":
    build_exe()
""".trimIndent()
    }
}