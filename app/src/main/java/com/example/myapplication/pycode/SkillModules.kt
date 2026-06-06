package com.example.myapplication.pycode
import com.example.myapplication.data.SkillRepository
object SkillModules {
    val contractSkill = """
# Навык: составление договоров купли-продажи
from langchain.tools import Tool
from langchain.prompts import PromptTemplate
from langchain.chains import LLMChain

contract_template = PromptTemplate(
    input_variables=["item", "price", "buyer", "seller"],
    template="Составь договор купли-продажи на {item} за {price} между {buyer} и {seller}."
)
contract_chain = LLMChain(llm=llm, prompt=contract_template)

def generate_contract(item: str, price: str, buyer: str, seller: str) -> str:
    return contract_chain.run(item=item, price=price, buyer=buyer, seller=seller)

contract_tool = Tool(
    name="ContractGenerator",
    func=generate_contract,
    description="Генерирует договор купли-продажи"
)
""".trimIndent()

    val phoneCallSkill = """
# Навык: ответы на телефонные звонки (заглушка)
# Требуется интеграция с Twilio или аналогичным сервисом
def answer_call(phone_number: str) -> str:
    # Здесь подключается голосовой шлюз
    return f"Ответ на звонок с номера {phone_number}"
""".trimIndent()

    val stockAnalysisSkill = """
# Навык: анализ фондового рынка
import yfinance as yf
from langchain.tools import Tool

def get_stock_price(ticker: str) -> str:
    stock = yf.Ticker(ticker)
    hist = stock.history(period="1d")
    if not hist.empty:
        return f"Цена {ticker}: $""" + """{hist['Close'].iloc[-1]:.2f}""" + """"
    return f"Не удалось получить данные для {ticker}"

stock_tool = Tool(
    name="StockPrice",
    func=get_stock_price,
    description="Получает текущую цену акции по тикеру"
)
""".trimIndent()

    val weatherForecastSkill = """
# Навык: прогнозирование погоды
import requests
from langchain.tools import Tool

def get_weather(city: str) -> str:
    api_key = os.getenv("OPENWEATHER_API_KEY", "")
    url = f"http://api.openweathermap.org/data/2.5/weather?q={city}&appid={api_key}&units=metric"
    resp = requests.get(url)
    if resp.status_code == 200:
        data = resp.json()
        return f"В {city} сейчас {data['main']['temp']}°C, {data['weather'][0]['description']}"
    return "Не удалось получить погоду"

weather_tool = Tool(
    name="Weather",
    func=get_weather,
    description="Показывает текущую погоду в городе"
)
""".trimIndent()

    val videoAnalysisSkill = """
# Навык: анализ видеопотока (заглушка)
# Требуется OpenCV, numpy и т.д.
def analyze_rtsp_stream(rtsp_url: str) -> str:
    # Здесь должен быть код захвата и анализа кадров
    return "Анализ видеопотока ещё не реализован"
""".trimIndent()

    val documentationSkill = """
# Навык: базовая документация
def generate_readme(project_name: str) -> str:
    return f"# {project_name}\n\nЭтот проект создан с помощью AiGen."
""".trimIndent()

    val analysisSynthesisSkill = """
# Навык: Анализ и синтез (Реальная когнитивная реализация через LLM)
def cog_1(agent_instance, input_text: str) -> str:
    specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК АНАЛИЗ И СИНТЕЗ]
Примени строгий алгоритм обработки информации к запросу ниже:
1. АНАЛИЗ: Декомпозируй данные на ключевые факты и скрытые паттерны.
2. СВЯЗИ: Установи логические и причинно-следственные связи.
3. СИНТЕЗ: Сформируй целостную картину и итоговый вывод.

Запрос пользователя: {input_text}'''
    return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по глубокому анализу и синтезу информации.")

def register_skills(agent):
    from agent import Skill
    agent.add_skill(Skill(
        name="Анализ и синтез",
        description="Разбор сложной информации, выделение паттернов, обобщение данных.",
        func=lambda task: cog_1(agent, task)
    ))
""".trimIndent()

    // Новая функция: возвращает код навыка по его ID
    fun skillCodeById(skillId: String): String {
        return when (skillId) {
            // Старые ID
            SkillRepository.ID_CONTRACT -> contractSkill
            SkillRepository.ID_PHONE -> phoneCallSkill
            SkillRepository.ID_STOCK -> stockAnalysisSkill
            SkillRepository.ID_WEATHER -> weatherForecastSkill
            SkillRepository.ID_VIDEO -> videoAnalysisSkill
            SkillRepository.ID_DOCS -> documentationSkill

            // Реализация навыка "Анализ и синтез"
            "cog_1" -> analysisSynthesisSkill

            // Новые навыки – генерируем шаблонную реализацию
            else -> generateGenericSkillModule(skillId)
        }
    }


    private fun generateGenericSkillModule(skillId: String): String {
        val skill = SkillRepository.getSkillCategories()
            .flatMap { it.skills }
            .firstOrNull { it.id == skillId }
            ?: return "# Неизвестный навык $skillId\n"
        val funcName = skillId.replace("[^a-zA-Z0-9_]".toRegex(), "_")
        return """
# Навык: ${skill.name}
import sys


def ${funcName}(input_text: str = "") -> str:
    return f"Выполнен навык: ${skill.name}"

def register_skills(agent):
    from agent import Skill
    agent.add_skill(Skill(
        name="${skill.name}",
        description="${skill.description}",
        func=${funcName}
    ))
""".trimIndent()
    }

}