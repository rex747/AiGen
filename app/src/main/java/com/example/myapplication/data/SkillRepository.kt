package com.example.myapplication.data

import com.example.myapplication.model.Skill
import com.example.myapplication.model.SkillCategory

object SkillRepository {
    // Старые константы остаются для обратной совместимости
    const val ID_CONTRACT = "1"
    const val ID_PHONE = "2"
    const val ID_STOCK = "3"
    const val ID_WEATHER = "4"
    const val ID_VIDEO = "5"
    const val ID_DOCS = "6"

    // Старый список (премиум) – оставляем, если нужно
    fun getAllSkills(): List<Skill> = listOf(
        Skill(
            id = ID_CONTRACT,
            name = "Составление договоров купли-продажи",
            description = "Генерация юридических документов с проверкой на соответствие ГК РФ",
            isPremium = true,
            category = "domain",
            requiredPackages = listOf(),
            implementationType = "generic"
        ),
        Skill(
            id = ID_PHONE,
            name = "Ответы на телефонные звонки",
            description = "Голосовой ассистент с синтезом речи (TTS) и распознаванием (ASR)",
            isPremium = true,
            category = "language",
            requiredPackages = listOf("pyttsx3", "faster-whisper"),
            implementationType = "heavy"
        ),
        Skill(
            id = ID_STOCK,
            name = "Анализ фондового рынка",
            description = "Получение и анализ биржевых данных (yfinance, alpha vantage)",
            isPremium = true,
            category = "external",
            requiredPackages = listOf("yfinance"),
            implementationType = "tool"
        ),
        Skill(
            id = ID_WEATHER,
            name = "Прогнозирование погоды",
            description = "Запрос к OpenWeatherMap / Yandex.Weather + прогноз",
            isPremium = true,
            category = "external",
            requiredPackages = listOf("requests"),
            implementationType = "tool"
        ),
        Skill(
            id = ID_VIDEO,
            name = "Анализ видеопотока",
            description = "Обработка RTSP-потоков с OpenCV (opencv-python)",
            isPremium = true,
            category = "visual",
            requiredPackages = listOf("opencv-python"),
            implementationType = "heavy"
        ),
        Skill(
            id = ID_DOCS,
            name = "Базовая документация",
            description = "Генерация README, docstrings, комментариев",
            isPremium = false,
            category = "technical",
            requiredPackages = listOf(),
            implementationType = "generic"
        )
    )

    // Новые навыки – все бесплатны
    private val cognitiveSkills = listOf(
        Skill("cog_1", "Анализ и синтез", "Разбор сложной информации, выделение паттернов, обобщение данных. Пример: анализ отчёта компании.",
            category = "cognitive", requiredPackages = listOf(), implementationType = "generic"),
        Skill("cog_2", "Рассуждение", "Логический вывод, дедукция, индукция, абдукция. Используется в Chain-of-Thought.",
            category = "cognitive", requiredPackages = listOf(), implementationType = "generic"),
        Skill("cog_3", "Критическое мышление", "Оценка аргументов, выявление логических ошибок, проверка фактов (fact-checking).",
            category = "cognitive", requiredPackages = listOf("requests", "beautifulsoup4"), implementationType = "tool"),
        Skill("cog_4", "Креативность", "Генерация оригинальных идей, аналогии, мозговой штурм.",
            category = "cognitive", requiredPackages = listOf(), implementationType = "generic"),
        Skill("cog_5", "Планирование", "Декомпозиция задач, построение стратегий, прогнозирование последствий (используется в ReAct).",
            category = "cognitive", requiredPackages = listOf(), implementationType = "generic"),
        Skill("cog_6", "Решение проблем", "Диагностика проблем, подбор решений, устранение неполадок (debugging).",
            category = "cognitive", requiredPackages = listOf(), implementationType = "tool"),
        Skill("cog_7", "Обучение", "Адаптация к новым данным, перенос знаний (few-shot + RAG).",
            category = "cognitive", requiredPackages = listOf("chromadb", "sentence-transformers"), implementationType = "rag"),
        Skill("cog_8", "Память", "Краткосрочный контекст, долгосрочное хранение (vector DB), ассоциативный поиск.",
            category = "cognitive", requiredPackages = listOf("chromadb"), implementationType = "memory")
    )

    private val languageSkills = listOf(
        Skill("lang_1", "Понимание естественного языка (NLU)", "Семантический анализ, извлечение намерений, распознавание тональности.",
            category = "language", requiredPackages = listOf(), implementationType = "generic"),
        Skill("lang_2", "Генерация текста (NLG)", "Написание статей, писем, кода, поэзии, документации.",
            category = "language", requiredPackages = listOf(), implementationType = "llm"),
        Skill("lang_3", "Многоязычность", "Перевод, переключение языков, культурная адаптация (googletrans или deep-translator).",
            category = "language", requiredPackages = listOf("deep-translator"), implementationType = "tool"),
        Skill("lang_4", "Диалоговое взаимодействие", "Ведение разговора, уточнение, управление контекстом.",
            category = "language", requiredPackages = listOf(), implementationType = "generic"),
        Skill("lang_5", "Распознавание речи (ASR)", "Аудио → текст (whisper или faster-whisper).",
            category = "language", requiredPackages = listOf("faster-whisper"), implementationType = "heavy"),
        Skill("lang_6", "Синтез речи (TTS)", "Текст → речь (pyttsx3 или gTTS).",
            category = "language", requiredPackages = listOf("pyttsx3"), implementationType = "tool"),
        Skill("lang_7", "Резюмирование", "Сжатие текстов, выделение ключевых тезисов (map-reduce).",
            category = "language", requiredPackages = listOf(), implementationType = "generic"),
        Skill("lang_8", "Переписывание", "Адаптация стиля, упрощение, перефразирование.",
            category = "language", requiredPackages = listOf(), implementationType = "generic")
    )

    private val visualSkills = listOf(
        Skill("vis_1", "Распознавание изображений", "Классификация, детекция объектов (YOLO или CLIP).",
            category = "visual", requiredPackages = listOf("opencv-python", "ultralytics"), implementationType = "heavy"),
        Skill("vis_2", "Описание изображений", "Генерация captions, VQA (LLaVA или BLIP).",
            category = "visual", requiredPackages = listOf("pillow"), implementationType = "heavy"),
        Skill("vis_3", "Генерация изображений", "Создание изображений по описанию (diffusers + Stable Diffusion). **Тяжёлый навык** — требует GPU.",
            category = "visual", requiredPackages = listOf("diffusers", "torch", "accelerate"), implementationType = "heavy"),
        Skill("vis_4", "Редактирование изображений", "Инпейнтинг, стилизация (Pillow + diffusers).",
            category = "visual", requiredPackages = listOf("pillow", "diffusers"), implementationType = "heavy"),
        Skill("vis_5", "Распознавание видео", "Анализ действий, трекинг (OpenCV + supervision).",
            category = "visual", requiredPackages = listOf("opencv-python"), implementationType = "heavy"),
        Skill("vis_6", "Генерация видео", "Короткие ролики (diffusers video или moviepy). **Очень тяжёлый**.",
            category = "visual", requiredPackages = listOf("moviepy", "diffusers"), implementationType = "heavy"),
        Skill("vis_7", "OCR", "Распознавание текста (pytesseract — лёгкий и проверенный).",
            category = "visual", requiredPackages = listOf("pytesseract", "pillow"), implementationType = "tool"),
        Skill("vis_8", "Мультимодальное понимание", "Связь текста+изображения+аудио (LLaVA или GPT-4o-like).",
            category = "visual", requiredPackages = listOf("pillow"), implementationType = "heavy")
    )

    private val technicalSkills = listOf(
        Skill("tech_1", "Написание кода", "Генерация кода на разных языках, алгоритмы.",
            category = "technical", requiredPackages = listOf(), implementationType = "llm"),
        Skill("tech_2", "Отладка кода", "Поиск багов, объяснение ошибок.",
            category = "technical", requiredPackages = listOf(), implementationType = "tool"),
        Skill("tech_3", "Рефакторинг", "Улучшение структуры кода.",
            category = "technical", requiredPackages = listOf(), implementationType = "generic"),
        Skill("tech_4", "Работа с API", "Вызов внешних сервисов (requests).",
            category = "technical", requiredPackages = listOf("requests"), implementationType = "tool"),
        Skill("tech_5", "Базы данных", "SQL/NoSQL запросы, проектирование (sqlite3, pymongo).",
            category = "technical", requiredPackages = listOf("pymongo"), implementationType = "tool"),
        Skill("tech_6", "DevOps", "Docker, Kubernetes, CI/CD (docker python client).",
            category = "technical", requiredPackages = listOf("docker"), implementationType = "tool"),
        Skill("tech_7", "Анализ данных", "pandas, визуализация (matplotlib/seaborn).",
            category = "technical", requiredPackages = listOf("pandas", "matplotlib"), implementationType = "tool"),
        Skill("tech_8", "Машинное обучение", "Построение моделей (scikit-learn, huggingface).",
            category = "technical", requiredPackages = listOf("scikit-learn"), implementationType = "heavy"),
        Skill("tech_9", "Работа с файлами", "Парсинг PDF/Excel/CSV (pymupdf, openpyxl, pandas).",
            category = "technical", requiredPackages = listOf("pymupdf", "openpyxl", "pandas"), implementationType = "tool"),
        Skill("tech_10", "Веб-скрапинг", "requests + BeautifulSoup или Playwright.",
            category = "technical", requiredPackages = listOf("beautifulsoup4", "playwright"), implementationType = "tool")
    )

    private val externalSkills = listOf(
        Skill("ext_1", "Веб-поиск", "Поиск + парсинг (requests + bs4 или tavily-python).",
            category = "external", requiredPackages = listOf("requests", "beautifulsoup4"), implementationType = "tool"),
        Skill("ext_2", "Браузерная автоматизация", "Playwright (рекомендуется в 2026 — быстрее Selenium).",
            category = "external", requiredPackages = listOf("playwright"), implementationType = "tool"),
        Skill("ext_3", "Работа с электронной почтой", "smtplib + imaplib или yagmail.",
            category = "external", requiredPackages = listOf("yagmail"), implementationType = "tool"),
        Skill("ext_4", "Календарное планирование", "Google Calendar API или icalendar.",
            category = "external", requiredPackages = listOf("google-api-python-client"), implementationType = "api"),
        Skill("ext_5", "Управление задачами", "Интеграция с Todoist / Trello API.",
            category = "external", requiredPackages = listOf("requests"), implementationType = "tool"),
        Skill("ext_6", "Работа с CRM/ERP", "API Salesforce / Bitrix (requests).",
            category = "external", requiredPackages = listOf("requests"), implementationType = "api"),
        Skill("ext_7", "Управление документами", "Google Docs API или docx/pypdf.",
            category = "external", requiredPackages = listOf("python-docx", "pymupdf"), implementationType = "tool"),
        Skill("ext_8", "Финансовые операции", "yfinance, alpha_vantage.",
            category = "external", requiredPackages = listOf("yfinance"), implementationType = "tool")
    )

    private val agenticSkills = listOf(
        Skill("ag_1", "Автономное принятие решений", "Действие без контроля пользователя (ReAct loop).",
            category = "agentic", requiredPackages = listOf(), implementationType = "agent"),
        Skill("ag_2", "Использование инструментов (Tool Use)", "Динамический выбор и вызов инструментов.",
            category = "agentic", requiredPackages = listOf(), implementationType = "tool"),
        Skill("ag_3", "Цепочечное мышление (Chain-of-Thought)", "Пошаговое рассуждение.",
            category = "agentic", requiredPackages = listOf(), implementationType = "generic"),
        Skill("ag_4", "Рефлексия", "Самоанализ ошибок и коррекция.",
            category = "agentic", requiredPackages = listOf(), implementationType = "generic"),
        Skill("ag_5", "Делегирование", "Распределение задач между суб-агентами.",
            category = "agentic", requiredPackages = listOf(), implementationType = "multiagent"),
        Skill("ag_6", "Параллельное выполнение", "Async / ThreadPoolExecutor.",
            category = "agentic", requiredPackages = listOf(), implementationType = "generic"),
        Skill("ag_7", "Самообучение", "Накопление опыта в векторной БД.",
            category = "agentic", requiredPackages = listOf("chromadb"), implementationType = "memory"),
        Skill("ag_8", "Мета-рассуждение", "Выбор стратегии решения.",
            category = "agentic", requiredPackages = listOf(), implementationType = "generic")
    )

    private val socialSkills = listOf(
        Skill("soc_1", "Эмпатия", "Распознавание эмоций по тексту (VADER или transformer).",
            category = "social", requiredPackages = listOf("textblob"), implementationType = "tool"),
        Skill("soc_2", "Персонализация", "Адаптация под историю пользователя (memory).",
            category = "social", requiredPackages = listOf(), implementationType = "memory"),
        Skill("soc_3", "Убеждение и переговоры", "Стратегии диалога.",
            category = "social", requiredPackages = listOf(), implementationType = "generic"),
        Skill("soc_4", "Медиация", "Разрешение конфликтов.",
            category = "social", requiredPackages = listOf(), implementationType = "generic"),
        Skill("soc_5", "Наставничество", "Обучение пользователя.",
            category = "social", requiredPackages = listOf(), implementationType = "generic"),
        Skill("soc_6", "Социальная осведомлённость", "Соблюдение этики и культурных норм.",
            category = "social", requiredPackages = listOf(), implementationType = "safety")
    )

    private val safetySkills = listOf(
        Skill("safe_1", "Фильтрация контента", "Блокировка вредоносного контента (moderation API или regex).",
            category = "safety", requiredPackages = listOf(), implementationType = "safety"),
        Skill("safe_2", "Проверка фактов", "Фактчекинг через поиск.",
            category = "safety", requiredPackages = listOf("requests", "beautifulsoup4"), implementationType = "tool"),
        Skill("safe_3", "Защита конфиденциальности", "Маскировка PII (presidio).",
            category = "safety", requiredPackages = listOf("presidio-analyzer"), implementationType = "tool"),
        Skill("safe_4", "Обнаружение манипуляций", "Защита от prompt injection.",
            category = "safety", requiredPackages = listOf(), implementationType = "safety"),
        Skill("safe_5", "Прозрачность", "Объяснение решений + источники.",
            category = "safety", requiredPackages = listOf(), implementationType = "generic"),
        Skill("safe_6", "Соблюдение этических норм", "Отказ от вредных запросов.",
            category = "safety", requiredPackages = listOf(), implementationType = "safety")
    )

    private val domainSkills = listOf(
        Skill("dom_1", "Юриспруденция", "Анализ договоров, прецеденты (работа с текстом + поиск).",
            category = "domain", requiredPackages = listOf(), implementationType = "generic"),
        Skill("dom_2", "Медицина", "Анализ симптомов (предупреждение: только справочно!).",
            category = "domain", requiredPackages = listOf(), implementationType = "generic"),
        Skill("dom_3", "Финансы", "yfinance + анализ.",
            category = "domain", requiredPackages = listOf("yfinance"), implementationType = "tool"),
        Skill("dom_4", "Образование", "Учебные планы, тестирование.",
            category = "domain", requiredPackages = listOf(), implementationType = "generic"),
        Skill("dom_5", "Маркетинг", "Анализ аудитории, контент.",
            category = "domain", requiredPackages = listOf(), implementationType = "generic"),
        Skill("dom_6", "Дизайн", "UI/UX + генерация изображений.",
            category = "domain", requiredPackages = listOf("pillow"), implementationType = "visual"),
        Skill("dom_7", "Музыка", "Генерация (audiocraft или musicgen).",
            category = "domain", requiredPackages = listOf(), implementationType = "heavy"),
        Skill("dom_8", "Игры", "Стратегии, баланс.",
            category = "domain", requiredPackages = listOf(), implementationType = "generic")
    )

    private val metaSkills = listOf(
        Skill("meta_1", "Контекстное управление", "RAG, выбор релевантной информации (chromadb).",
            category = "meta", requiredPackages = listOf("chromadb", "sentence-transformers"), implementationType = "rag"),
        Skill("meta_2", "Prompt-инжиниринг", "Оптимизация промптов.",
            category = "meta", requiredPackages = listOf(), implementationType = "generic"),
        Skill("meta_3", "Оркестрация", "LangGraph / custom workflow.",
            category = "meta", requiredPackages = listOf(), implementationType = "orchestration"),
        Skill("meta_4", "Мульти-агентная координация", "CrewAI-like или custom.",
            category = "meta", requiredPackages = listOf(), implementationType = "multiagent"),
        Skill("meta_5", "Адаптация архитектуры", "Динамическое поведение.",
            category = "meta", requiredPackages = listOf(), implementationType = "generic"),
        Skill("meta_6", "Оценка качества", "Самооценка ответов.",
            category = "meta", requiredPackages = listOf(), implementationType = "generic")
    )

    fun getSkillCategories(): List<SkillCategory> = listOf(
        SkillCategory("1. Когнитивные и интеллектуальные навыки", cognitiveSkills),
        SkillCategory("2. Языковые и коммуникативные навыки", languageSkills),
        SkillCategory("3. Визуальные и мультимодальные навыки", visualSkills),
        SkillCategory("4. Технические и инструментальные навыки", technicalSkills),
        SkillCategory("5. Навыки взаимодействия с внешним миром", externalSkills),
        SkillCategory("6. Агентные и автономные навыки", agenticSkills),
        SkillCategory("7. Социальные и эмоциональные навыки", socialSkills),
        SkillCategory("8. Безопасность и этика", safetySkills),
        SkillCategory("9. Специализированные доменные навыки", domainSkills),
        SkillCategory("10. Мета-навыки и архитектурные", metaSkills)
    )
    // Функция для получения всех навыков, включая новые
    fun getAllSkillsWithDeps(): List<Skill> {
        val newSkills = getSkillCategories().flatMap { it.skills }
        return getAllSkills() + newSkills
    }
}