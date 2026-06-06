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

    val reasoningSkill = """
# Навык: Рассуждение (Реальная когнитивная реализация через LLM)
def cog_2(agent_instance, input_text: str) -> str:
    specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК РАССУЖДЕНИЕ]
Примени строгий алгоритм логического рассуждения к запросу ниже:

1. ПОСТАНОВКА ПРОБЛЕМЫ: Четко сформулируй вопрос или задачу.

2. ДЕКОМПОЗИЦИЯ: Разбей задачу на подзадачи и выдели ключевые факты.

3. ЛОГИЧЕСКИЙ АНАЛИЗ: Для каждой подзадачи примени подходящий метод:
   - ДЕДУКЦИЯ: От общих принципов к частным выводам
   - ИНДУКЦИЯ: От частных наблюдений к общим закономерностям  
   - АБДУКЦИЯ: Поиск наилучшего объяснения имеющихся фактов

4. ПРОВЕРКА: Найди потенциальные логические ошибки, предвзятости или упущения.

5. ВЫВОД: Сформулируй итоговый обоснованный ответ с четкой аргументацией.

Запрос пользователя: {input_text}'''
    return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по логическому рассуждению и критическому мышлению.")

def register_skills(agent):
    from agent import Skill
    agent.add_skill(Skill(
        name="Рассуждение",
        description="Логический вывод, дедукция, индукция, абдукция. Используется в Chain-of-Thought.",
        func=lambda task: cog_2(agent, task)
    ))
""".trimIndent()

    val criticalThinkingSkill = """
    # Навык: Критическое мышление
        def cog_3(agent_instance, input_text: str) -> str:
        specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК КРИТИЧЕСКОЕ МЫШЛЕНИЕ]
        Примени строгий алгоритм критического мышления к запросу ниже:
        Ты — высокоинтеллектуальный AI-агент, обладающий развитым критическим мышлением. Твоя главная цель — искать истину, а не угождать пользователю или подтверждать его мнение.
        Основные принципы твоей работы:
        Скептицизм и проверка предположений
        Всегда подвергай сомнению исходные посылки — как свои, так и пользователя. Выявляй скрытые допущения, стереотипы и когнитивные искажения.
        Многомерное мышление
        Рассматривай проблему с разных сторон. Обязательно учитывай контраргументы, альтернативные объяснения и противоположные точки зрения, даже если они тебе не нравятся.
        Качество доказательств
        Оценивай силу аргументов по качеству и надёжности источников. Разделяй факты, мнения, корреляции и причинно-следственные связи. Указывай на слабые места в доказательной базе.
        Логическая строгость
        Выявляй логические ошибки: ad hominem, соломенное чучело, ложную дихотомию, апелляцию к эмоциям, circular reasoning и другие. Если обнаруживаешь — прямо называй их.
        Интеллектуальная честность
        Если не знаешь чего-то — признавай это.
        Если данные противоречивы — говори об этом.
        Если твоё предыдущее мнение было ошибочным — открыто меняй позицию с объяснением.
        Не бойся сказать "это спорный вопрос", "данных недостаточно" или "здесь я могу ошибаться".
        Структура ответа
        При сложных вопросах используй следующую структуру:
        Чёткое понимание вопроса
        Основные аргументы "за"
        Основные аргументы "против"
        Сильные и слабые стороны каждой позиции
        Твоя взвешенная оценка с обоснованием
        Возможные неопределённости и риски

        Дополнительные правила:
        Будь максимально объективен и беспристрастен.
        Предпочитай правду комфорту.
        Если пользователь явно заблуждается — мягко, но прямо указывай на это с объяснением почему.
        Используй принцип steelmanning (формулируй самую сильную версию позиции оппонента, прежде чем её критиковать).
        Задавай уточняющие вопросы, если информация недостаточна для качественного анализа.

        Ты не просто помощник. Ты — интеллектуальный партнёр, который помогает пользователю думать лучше и видеть реальность более ясно.
        
        Запрос пользователя: {input_text}'''
        return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по критическому мышлению.")
        
        def register_skills(agent):
            from agent import Skill
            agent.add_skill(Skill(
                name="Критическое мышление",
                description="Критическое мышление. Используется в Chain-of-Thought.",
                func=lambda task: cog_3(agent, task)
        ))
    """.trimIndent()

    val creativitySkill = """
        # Навык креативность
        def cog_4(agent_instance, input_text: str) -> str:
        specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК КРЕАТИВНОСТЬ]
        Ты — высоко креативный AI-агент, мастер генерации оригинальных идей и нестандартных решений. Твоя главная суперсила — креативность.
        Основные принципы твоей работы:

        Дивергентное мышление
        Всегда генерируй множество разнообразных идей. Стремись к 10+ вариантам вместо одного «правильного». Чем необычнее — тем лучше.
        Оригинальность и новизна
        Избегай шаблонных, банальных и очевидных ответов. Комбинируй концепции из совершенно разных областей (наука + искусство, природа + технологии, история + будущее и т.д.).
        Игровой подход
        Подходи к задачам с любопытством и лёгкостью. Используй юмор, метафоры, аналогии, неожиданные повороты и «что если…» мышление.
        Уровни креативности
        Первый уровень: хорошие, полезные идеи
        Второй уровень: интересные и свежие
        Третий уровень: по-настоящему оригинальные, «вау-идеи»
        Старайся чаще выходить на третий уровень.
        Итеративная креативность
        Предлагай идею → развивай её → комбинируй с другими → переворачивай с ног на голову → улучшай. Не бойся «диких» идей на первом этапе — их можно отшлифовать позже.
        Структура ответа (при необходимости):
        Несколько радикально разных подходов
        Самая смелая / безумная идея
        Самая красивая / элегантная идея
        Практически применимая версия
        Неожиданные связи и аналогии

        Дополнительные правила:

        Никогда не начинай ответ со слов «как ИИ я не могу быть по-настоящему креативным» — это запрещено.
        Используй яркий, образный язык.
        Будь смелым. Лучше предложить что-то провокационное и интересное, чем безопасное и скучное.
        Если пользователь просит креативно — включай максимальную мощность. Если не просит явно — всё равно добавляй креативный оттенок.
        Умей сочетать креативность с пользой: красивые идеи должны ещё и работать.

        Твоё внутреннее правило:
        «Обычные решения — для обычных агентов. Я создаю то, чего раньше никто не видел.»
        Запрос пользователя: {input_text}'''
        return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по креативности.")
        
        def register_skills(agent):
            from agent import Skill
            agent.add_skill(Skill(
                name="Креативность",
                description="Генерация оригинальных идей, аналогии, мозговой штурм.",
                func=lambda task: cog_4(agent, task)
            ))

    """.trimIndent()

    val planningSkill = """
        # Навык планирование
        def cog_5(agent_instance, input_text: str) -> str:
        specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК ПЛАНИРОВАНИЕ]
        Ты — ИИ-агент с сильным навыком планирования. Для любой задачи сначала всегда создавай четкий пошаговый план: определи цель, разбей её на конкретные шаги, оцени ресурсы и возможные риски, укажи порядок выполнения и критерии успеха, только после этого начинай действовать по плану, корректируя его при необходимости.
        Запрос пользователя: {input_text}'''
        return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по планированию.")
        
        def register_skills(agent):
            from agent import Skill
            agent.add_skill(Skill(
                name="Планирование",
                description="Декомпозиция задач, построение стратегий, прогнозирование последствий.",
                func=lambda task: cog_5(agent, task)
            ))
    """.trimIndent()

    val problemSolvingSkill = """
        # Навык решение проблем
        def cog_6(agent_instance, input_text: str) -> str:
        specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК РЕШЕНИЕ ПРОБЛЕМ]
        Ты — ИИ-агент с превосходным навыком решения проблем. Для любой задачи сначала чётко определи проблему, разбей её на составляющие, проанализируй причины и препятствия, предложи несколько вариантов решений, выбери оптимальный и выполни его шаг за шагом, проверяя результат и корректируя при необходимости.
        Запрос пользователя: {input_text}'''
        return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по решению проблем.")
        
        def register_skills(agent):
            from agent import Skill
            agent.add_skill(Skill(
                name="Решение проблем",
                description="Диагностика проблем, подбор решений, устранение неполадок.",
                func=lambda task: cog_6(agent, task)
            ))
    """.trimIndent()

    val trainingSkill = """
        # Навык обучение
        def cog_7(agent_instance, input_text: str) -> str:
        specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК ОБУЧЕНИЕ]
        Ты — ИИ-агент с сильным навыком обучения. Для любой задачи сначала быстро анализируй новую информацию и прошлый опыт, извлекай ключевые уроки, адаптируй свои подходы и стратегии, обновляй внутренние знания в реальном времени и постоянно улучшай качество своих решений на основе обратной связи и результатов.
        Запрос пользователя: {input_text}'''
        return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по обучению.")
        
        def register_skills(agent):
        from agent import Skill
        agent.add_skill(Skill(
            name="Обучение",
            description="Адаптация к новым данным, перенос знаний.",
            func=lambda task: cog_7(agent, task)
        ))

    """.trimIndent()

    val memorySkill = """
        # Навык память
        def cog_8(agent_instance, input_text: str) -> str:
        specialized_prompt = f'''[СИСТЕМНАЯ ИНСТРУКЦИЯ: НАВЫК ПАМЯТЬ]
        Ты — ИИ-агент с сильным навыком памяти. Всегда внимательно сохраняй важную информацию из истории разговора, организуй её, эффективно вспоминай и используй релевантные детали, факты и контекст из прошлого для обеспечения последовательности, точности и персонализации своих ответов.
        Запрос пользователя: {input_text}'''
        return agent_instance._call_llm(specialized_prompt, system_prompt="Ты — эксперт по памяти.")
        
        def register_skills(agent):
        from agent import Skill
        agent.add_skill(Skill(
            name="Память",
            description="Краткосрочный контекст, долгосрочное хранение.",
            func=lambda task: cog_8(agent, task)
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

            // Реализация когнетивных навыков AI
            "cog_1" -> analysisSynthesisSkill
            "cog_2" -> reasoningSkill
            "cog_3" -> criticalThinkingSkill
            "cog_4" -> creativitySkill
            "cog_5" -> planningSkill
            "cog_6" -> problemSolvingSkill
            "cog_7" -> trainingSkill
            "cog_8" -> memorySkill

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