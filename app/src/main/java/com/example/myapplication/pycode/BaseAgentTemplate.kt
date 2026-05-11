package com.example.myapplication.pycode

object BaseAgentTemplate {
    val template = $$"""
# base_agent.py
import os
import sys

def generate_agent_project(project_name="my_agent_project", model="gpt-3.5-turbo", temperature=0.7):
    ""$${'"'}
    Генерирует полностью самодостаточный проект AI-агента.
    После генерации скопируйте папку проекта и используйте её в любой системе.
    ""$${'"'}
    # Создание структуры директорий
    os.makedirs(project_name, exist_ok=True)
    os.makedirs(os.path.join(project_name, "skills"), exist_ok=True)

    # ==================== ЯДРО АГЕНТА (agent.py) ====================
    agent_code = f'''
import urllib.request
import json
import sys
import os
import importlib.util
from typing import Any, Callable

class Skill:
    ""$${'"'}
    Навык (Skills) — модульная единица функциональности агента.
    Соответствует архитектуре Microsoft Agent Framework и Semantic Kernel.
    ""$${'"'}
    def __init__(self, name: str, description: str, func: Callable[..., str]):
        self.name = name
        self.description = description
        self.func = func

    def execute(self, *args, **kwargs) -> str:
        return self.func(*args, **kwargs)


class Agent:
    ""$${'"'}
    Базовый AI-агент, соответствующий архитектуре:
    - LangChain: model + tools + system_prompt
    - AutoGen: AssistantAgent + UserProxyAgent
    - CrewAI: Agent + Task + Crew
    - Semantic Kernel: ChatCompletionAgent + Plugins

    Не требует внешних библиотек (использует urllib для HTTP).
    ""$${'"'}
    def __init__(
        self,
        api_key: str,
        model: str = "{model}",
        temperature: float = {temperature},
        system_prompt: str = "You are a helpful assistant."
    ):
        self.api_key = api_key
        self.model = model
        self.temperature = temperature
        self.system_prompt = system_prompt
        self.skills: dict[str, Skill] = {{}}
        self.messages: list[dict[str, str]] = [
            {{"role": "system", "content": system_prompt}}
        ]

    def add_skill(self, skill: Skill):
        ""$${'"'}Добавляет навык в агента (аналог register_for_execution в AutoGen).""$${'"'}
        self.skills[skill.name] = skill

    def _call_llm(self, prompt: str) -> str:
        ""$${'"'}
        Вызывает LLM API (OpenAI). Использует стандартный urllib.
        ""$${'"'}
        url = "https://api.openai.com/v1/chat/completions"
        headers = {{
            "Content-Type": "application/json",
            "Authorization": f"Bearer {{self.api_key}}"
        }}
        # Добавляем текущее сообщение пользователя
        messages = self.messages + [{{"role": "user", "content": prompt}}]
        data = {{
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature
        }}
        req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers)
        with urllib.request.urlopen(req) as response:
            result = json.loads(response.read().decode('utf-8'))
            return result['choices'][0]['message']['content']

    def run(self, task: str) -> str:
        ""$${'"'}
        Основной цикл агента (аналог ReAct-цикла в LangChain).
        1. Проверяет, есть ли навык, соответствующий задаче.
        2. Если навык найден, выполняет его.
        3. Иначе обращается к LLM.
        ""$${'"'}
        # Простейшая маршрутизация по ключевым словам (можно расширить)
        for skill_name, skill in self.skills.items():
            if skill_name in task.lower():
                return skill.execute(task)

        # Если навык не найден, используем LLM
        return self._call_llm(task)
        
        
    def load_skills_from_directory(self, directory: str = "skills"):
        {"\"\"\""}Загружает навыки из Python-файлов в указанной директории.{"\"\"\""}
        skill_dir = os.path.join(os.path.dirname(__file__), directory)
        if not os.path.isdir(skill_dir):
            return

        root_dir = os.path.dirname(skill_dir)

        sys.path.insert(0, root_dir)
        sys.path.insert(0, skill_dir)

        for filename in os.listdir(skill_dir):
            if filename.endswith(".py") and filename != "__init__.py":
                module_name = filename[:-3]
                try:
                    module = importlib.import_module(module_name)
                    if hasattr(module, "register_skills"):
                        getattr(module, "register_skills")(self)
                except Exception as e:
                    print(f"Ошибка загрузки навыка из {{filename}}: {{e}}")

        sys.path.pop(0)
        sys.path.pop(0)


if __name__ == "__main__":
    # Пример использования (замените на свой API ключ)
    API_KEY = os.getenv("OPENAI_API_KEY", "your-api-key-here")
    agent = Agent(api_key=API_KEY)

    # Загрузка навыков из папки skills
    agent.load_skills_from_directory()

    # Тестовый запрос
    result = agent.run("Hello, what can you do?")
    print("Агент отвечает:")
    print(result)
'''
    with open(os.path.join(project_name, "agent.py"), "w", encoding="utf-8") as f:
        f.write(agent_code)

    # ==================== ПРИМЕР НАВЫКА (example_skill.py) ====================
    skill_code = '''
""$${'"'}
Пример навыка (Skill) для агента.
Каждый навык должен определять функцию register_skills(agent),
которая добавляет одну или более Skill в переданный объект Agent.
Архитектура соответствует Microsoft Agent Framework (FileSkill/InlineSkill).
""$${'"'}
import sys
from agent import Skill

def calculate(expression: str) -> str:
    ""$${'"'}Вычисляет арифметическое выражение.""$${'"'}
    try:
        # Игнорируем всё, кроме выражения (очень упрощённо)
        expr = expression.replace("посчитай", "").replace("вычисли", "").strip()
        result = eval(expr)
        return f"Результат: {result}"
    except Exception as e:
        return f"Ошибка вычисления: {e}"

def register_skills(agent):
    agent.add_skill(Skill(
        name="калькулятор",
        description="Вычисление арифметических выражений",
        func=calculate
    ))
    # Здесь можно добавить несколько навыков
'''
    with open(os.path.join(project_name, "skills", "example_skill.py"), "w", encoding="utf-8") as f:
        f.write(skill_code)
        # === ДОПОЛНИТЕЛЬНЫЕ НАВЫКИ ===
        # {{EXTRA_SKILL_FILES_PLACEHOLDER}}

    # ==================== СЦЕНАРИЙ СБОРКИ EXE ====================
    build_exe_script = '''
""$${'"'}
Скрипт для упаковки агента в EXE-файл с помощью PyInstaller.
Требуется установленный PyInstaller: pip install pyinstaller
""$${'"'}
import subprocess
import sys

def build_exe():
    subprocess.check_call([
        sys.executable, "-m", "PyInstaller",
        "--onefile",           # Создать один EXE-файл
        "--name", "MyAgent",   # Имя выходного файла
        "--distpath", "./dist",# Папка для готового EXE
        "agent.py"
    ])
    print("EXE-файл создан в папке ./dist")

if __name__ == "__main__":
    build_exe()
'''
    with open(os.path.join(project_name, "build_exe.py"), "w", encoding="utf-8") as f:
        f.write(build_exe_script)

    # ==================== ВЫВОД ИНСТРУКЦИЙ ====================
    print(f"\nПроект успешно создан в папке: {project_name}")
    print("\nСтруктура проекта:")
    print(f"  {project_name}/")
    print(f"    agent.py          - базовый агент (без зависимостей)")
    print(f"    skills/           - папка с навыками")
    print(f"      example_skill.py - пример навыка-калькулятора")
    print(f"    build_exe.py      - скрипт сборки EXE")
    print("\nИнструкции:")
    print("1. Перейдите в папку проекта:")
    print(f"   cd {project_name}")
    print("2. Установите переменную окружения OPENAI_API_KEY (или укажите ключ в agent.py)")
    print("3. Запустите агента:")
    print("   python agent.py")
    print("4. Для создания автономного EXE-файла (без Python и внешних библиотек):")
    print("   pip install pyinstaller  (однократно на вашей машине)")
    print("   python build_exe.py")
    print("   После сборки EXE-файл появится в папке dist/MyAgent.exe.")
    print("\nВы можете копировать папку проекта и использовать её на любой системе с Python 3.8+.")

if __name__ == "__main__":
    # Можно запустить с параметрами: python agent_generator.py MyProject gpt-4 0.5
    project_name = sys.argv[1] if len(sys.argv) > 1 else "my_agent_project"
    model = sys.argv[2] if len(sys.argv) > 2 else "gpt-3.5-turbo"
    temperature = float(sys.argv[3]) if len(sys.argv) > 3 else 0.7
    generate_agent_project(project_name, model, temperature)

""".trimIndent()
}