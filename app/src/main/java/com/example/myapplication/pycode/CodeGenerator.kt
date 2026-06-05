package com.example.myapplication.pycode

import com.example.myapplication.data.SkillRepository
import com.example.myapplication.model.Skill

object CodeGenerator {

    /**
     * Генерация полного кода агента (базовый шаблон + выбранные навыки + requirements.txt)
     */
    fun generateFullAgent(selectedSkillIds: List<String>): String {
        val base = BaseAgentTemplate.template

        // 1. Получаем ВСЕ навыки с зависимостями
        val allSkills = SkillRepository.getAllSkillsWithDeps()

        // 2. Фильтруем только выбранные навыки
        val selectedSkills: List<Skill> = allSkills.filter { skill ->
            skill.id in selectedSkillIds
        }

        // 3. Собираем все уникальные зависимости
        val allRequiredPackages = selectedSkills
            .flatMap { it.requiredPackages }
            .distinct()
            .sorted()

        // 4. Генерируем содержимое requirements.txt
        val requirementsContent = if (allRequiredPackages.isEmpty()) {
            "# Нет дополнительных зависимостей"
        } else {
            allRequiredPackages.joinToString("\n")
        }

        // 5. Генерация кода для создания файлов навыков (остаётся как было, но улучшено)
        val extraFilesCode = selectedSkillIds.joinToString("\n\n") { skillId ->
            val skillCode = SkillModules.skillCodeById(skillId)
            val escaped = skillCode
                .replace("\\", "\\\\")
                .replace("\"\"\"", "\\\"\\\"\\\"")
                .replace("'''", "\\'\\'\\'")

            """
    # === Навык: $skillId ===
    skill_${skillId}_code = '''\\
$escaped'''.lstrip('\n')
    with open(os.path.join(project_name, "skills", "$skillId.py"), "w", encoding="utf-8") as f:
        f.write(skill_${skillId}_code)
            """.trimIndent()
        }

        // 6. Заменяем плейсхолдеры в шаблоне
        val result = base
            .replace("# {{EXTRA_SKILL_FILES_PLACEHOLDER}}", extraFilesCode)
            .replace("# {{REQUIREMENTS_TXT}}", requirementsContent)

        return result
    }

    /**
     * Получение скрипта сборки EXE (без изменений)
     */
    fun getBuildExeScript(): String {
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
    print("✅ EXE-файл создан в папке ./dist")

if __name__ == "__main__":
    build_exe()
""".trimIndent()
    }

    /**
     * Дополнительный метод: получить только requirements.txt (для показа пользователю перед генерацией)
     */
    fun generateRequirementsTxt(selectedSkillIds: List<String>): String {
        val allSkills = SkillRepository.getAllSkillsWithDeps()
        val selectedSkills = allSkills.filter { it.id in selectedSkillIds }

        val packages = selectedSkills
            .flatMap { it.requiredPackages }
            .distinct()
            .sorted()

        return if (packages.isEmpty()) {
            "# Нет дополнительных pip-пакетов"
        } else {
            packages.joinToString("\n") + "\n# Установите: pip install -r requirements.txt"
        }
    }

    /**
     * Получить список тяжёлых навыков (для предупреждения пользователя)
     */
    fun getHeavySkills(selectedSkillIds: List<String>): List<Skill> {
        val allSkills = SkillRepository.getAllSkillsWithDeps()
        return allSkills.filter {
            it.id in selectedSkillIds && it.implementationType == "heavy"
        }
    }
}