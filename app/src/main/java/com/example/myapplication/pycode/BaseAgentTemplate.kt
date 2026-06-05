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
    
    # ==================== REQUIREMENTS.TXT ====================
    # {{REQUIREMENTS_TXT}}
    with open(os.path.join(project_name, "requirements.txt"), "w", encoding="utf-8") as f:
        f.write(""${'"'}{{REQUIREMENTS_CONTENT}}""${'"'})


    # ==================== ЯДРО АГЕНТА (agent.py) ====================
    agent_code = f'''
import urllib.request
import json
import sys
import os
import importlib.util
from typing import Any, Callable, Dict, List

class Skill:
    ""${'"'}Модульный навык агента""${'"'}
    def __init__(self, name: str, description: str, func: Callable[..., str]):
        self.name = name
        self.description = description
        self.func = func

    def execute(self, *args, **kwargs) -> str:
        try:
            return self.func(*args, **kwargs)
        except Exception as e:
            return f"Ошибка выполнения навыка {self.name}: {e}"


class Agent:
    ""${'"'}Улучшенный AI-агент с поддержкой Tool Use / ReAct""${'"'}
    def __init__(
        self,
        api_key: str,
        model: str = "{model}",
        temperature: float = {temperature},
        system_prompt: str = "Ты — полезный, автономный AI-агент с множеством реальных навыков."
    ):
        self.api_key = api_key
        self.model = model
        self.temperature = temperature
        self.system_prompt = system_prompt
        self.skills: Dict[str, Skill] = {{}}
        self.messages: List[Dict[str, str]] = [
            {{"role": "system", "content": system_prompt}}
        ]

    def add_skill(self, skill: Skill):
        self.skills[skill.name] = skill

    def _call_llm(self, prompt: str) -> str:
        url = "https://api.openai.com/v1/chat/completions"
        headers = {{
            "Content-Type": "application/json",
            "Authorization": f"Bearer {{self.api_key}}"
        }}
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
        ""${'"'}Улучшенный ReAct-подобный цикл""${'"'}
        self.messages.append({{"role": "user", "content": task}})
        
        for _ in range(8):  # Максимум итераций
            prompt = f""${'"'}Текущая задача: {task}
Доступные навыки: {list(self.skills.keys())}
Подумай шаг за шагом. Если нужен навык — используй его.""${'"'}
            
            response = self._call_llm(prompt)
            
            # Простая маршрутизация по названию навыка
            for skill_name, skill in self.skills.items():
                if skill_name.lower() in response.lower() or skill_name in task.lower():
                    observation = skill.execute(task)
                    self.messages.append({{"role": "observation", "content": observation}})
                    break
            else:
                # Финальный ответ
                return response
                
        return self._call_llm("Дай финальный ответ на исходную задачу.")

    def load_skills_from_directory(self, directory: str = "skills"):
        ""${'"'}Загружает все навыки из папки skills""${'"'}
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
                    print(f"Ошибка загрузки {{filename}}: {{e}}")

        sys.path.pop(0)
        sys.path.pop(0)


if __name__ == "__main__":
    API_KEY = os.getenv("OPENAI_API_KEY", "your-api-key-here")
    agent = Agent(api_key=API_KEY, model="{model}", temperature={temperature})
    agent.load_skills_from_directory()
    
    print("Агент готов. Введите задачу или 'exit' для выхода.")
    while True:
        task = input("> ")
        if task.lower() in ["exit", "quit"]:
            break
        result = agent.run(task)
        print("Агент:", result)
'''
    with open(os.path.join(project_name, "agent.py"), "w", encoding="utf-8") as f:
        f.write(agent_code)

# ==================== ДОПОЛНИТЕЛЬНЫЕ НАВЫКИ ====================
# {{EXTRA_SKILL_FILES_PLACEHOLDER}}

# ==================== СКРИПТ СБОРКИ EXE ====================
    build_exe_script = '''
import subprocess
import sys

def build_exe():
    subprocess.check_call([
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--name", "MyAgent",
        "--distpath", "./dist",
        "--clean",
        "agent.py"
    ])
    print("✅ EXE создан в ./dist")

if __name__ == "__main__":
    build_exe()
'''
    with open(os.path.join(project_name, "build_exe.py"), "w", encoding="utf-8") as f:
        f.write(build_exe_script)


# ==================== ИНСТРУКЦИИ ====================
    print(f"\n✅ Проект успешно создан: {project_name}")
    print("\nСтруктура проекта:")
    print(f"  {project_name}/")
    print("    ├── agent.py")
    print("    ├── requirements.txt")
    print("    ├── build_exe.py")
    print("    └── skills/")
    print("\nИнструкции:")
    print("1. cd " + project_name)
    print("2. pip install -r requirements.txt")
    print("3. export OPENAI_API_KEY=sk-...")
    print("4. python agent.py")
    print("5. Для EXE: python build_exe.py")
    
""".trimIndent()
}