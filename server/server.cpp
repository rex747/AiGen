// SecureAuthServer.cpp
// =============================================================================
// ЗАВИСИМОСТИ (NuGet / vcpkg / header-only):
//   - nlohmann.json        (NuGet: nlohmann.json)
//   - openssl              (NuGet: openssl-vc-static или аналог)
//   - cpp-httplib          (header-only: https://github.com/yhirose/cpp-httplib)
//   - jwt-cpp              (header-only: https://github.com/Thalhammer/jwt-cpp)
//
// Компиляция (Linux пример):
//   g++ -std=c++17 -O2 -pthread SecureAuthServer.cpp -lssl -lcrypto -o server
// =============================================================================
#define NOMINMAX 


#include <iostream>
#include <fstream>
#include <string>
#include <map>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <thread>
#include <mutex>
#include <shared_mutex>
#include <regex>
#include <algorithm>
#include <optional>
#include <atomic>
#include <csignal>
#include <cctype>

#include <curl/curl.h>   // для SMTP
#include <ctime>

#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/rand.h>
#include <openssl/crypto.h>

#include "httplib.h"
#include "json.hpp"
#include "jwt-cpp/include/jwt-cpp/jwt.h"


using json = nlohmann::json;

void log_message(const std::string& message);
// Функция логирования
void log_message(const std::string& message) {
    auto now = std::chrono::system_clock::now();
    auto time = std::chrono::system_clock::to_time_t(now);
    std::cerr << "[" << std::ctime(&time) << "] " << message << std::endl;

    // Также записываем в файл
    std::ofstream log_file("server.log", std::ios::app);
    if (log_file.is_open()) {
        log_file << "[" << std::ctime(&time) << "] " << message << std::endl;
    }
}

// ============================================================================
// API ключи к LLM
// ============================================================================

namespace AiConfig {
    // Замените на ваш реальный API-ключ из личного кабинета Mistral AI
    constexpr const char* MISTRAL_API_KEY = "VOghCLeeLV6V8V9JbVPeGcyt5EbLYnEN";
    // Используем последнюю версию модели mistral-medium
    constexpr const char* MISTRAL_MODEL = "mistral-medium-latest";
    // OpenAI-совместимый эндпоинт Mistral AI
    constexpr const char* MISTRAL_ENDPOINT = "https://api.mistral.ai/v1/chat/completions";
    // Настройки генерации (можно будет позже вынести в конфиг агента)
    constexpr double TEMPERATURE = 0.7;
    constexpr int MAX_TOKENS = 500;
}

// ============================================================================
// Конфигурация платежей
// ============================================================================
namespace BillingConfig {
    constexpr double SKILL_USAGE_COST = 0.10; // Стоимость использования одного навыка в долларах
    constexpr const char* PAYMENT_GATEWAY_URL = "https://api.example.com/charge"; // URL платежного шлюза
}

// =============================================================================
// МОДЕЛИ ДЛЯ АГЕНТОВ И ЗАДАЧ
// =============================================================================
struct Agent {
    std::string agent_id;        // UUID
    std::string owner_email;     // владелец (пользователь)
    std::string name;            // отображаемое имя
    std::string description;     // что умеет
    std::vector<std::string> skills;  // список ID навыков (из SkillRepository)
    std::string endpoint;        // URL, куда слать A2A-запросы (для внешних агентов)
    bool is_public = true;       // виден ли в каталоге
};

struct Task {
    std::string task_id;
    std::string orchestrator_email; // кто запустил
    std::string status;             // "pending", "running", "completed", "failed"
    std::vector<std::string> agent_chain; // последовательность agent_id
    std::string current_agent;
    std::string input_payload;
    std::string output_payload;
    std::chrono::system_clock::time_point created_at;
};

// =============================================================================
// ХРАНИЛИЩЕ АГЕНТОВ И ЗАДАЧ (in-memory + JSON-файлы)
// =============================================================================

class AgentStore {
    mutable std::shared_mutex mtx_;
    std::map<std::string, Agent> agents_; // agent_id -> Agent
    const std::string file_path_ = "agents.json";

    void load() {
        std::ifstream file(file_path_);
        if (!file.is_open()) return;
        try {
            json j;
            file >> j;
            std::unique_lock<std::shared_mutex> lock(mtx_);
            for (auto& [agent_id, data] : j.items()) {
                Agent a;
                a.agent_id = agent_id;
                a.owner_email = data.value("owner_email", "");
                a.name = data.value("name", "");
                a.description = data.value("description", "");
                a.skills = data.value("skills", std::vector<std::string>());
                a.endpoint = data.value("endpoint", "");
                a.is_public = data.value("is_public", true);
                agents_[agent_id] = a;
            }
        }
        catch (const std::exception& e) {
            std::cerr << "[WARN] Failed to load agents database: " << e.what() << std::endl;
        }
    }

    void persist() {
        json j;
        {
            std::shared_lock<std::shared_mutex> lock(mtx_);
            for (const auto& [agent_id, a] : agents_) {
                j[agent_id] = {
                    {"owner_email", a.owner_email},
                    {"name", a.name},
                    {"description", a.description},
                    {"skills", a.skills},
                    {"endpoint", a.endpoint},
                    {"is_public", a.is_public}
                };
            }
        }
        std::ofstream file(file_path_, std::ios::trunc);
        if (!file.is_open()) {
            std::cerr << "[ERROR] Cannot open agents file for writing" << std::endl;
            return;
        }
        file << j.dump(2);
    }

public:
    explicit AgentStore() {
        load();
    }

    bool create(const Agent& agent) {
        std::unique_lock<std::shared_mutex> lock(mtx_);
        if (agents_.find(agent.agent_id) != agents_.end()) {
            return false;
        }
        agents_[agent.agent_id] = agent;
        lock.unlock();
        persist();
        return true;
    }

    bool update(const Agent& agent) {
        std::unique_lock<std::shared_mutex> lock(mtx_);
        auto it = agents_.find(agent.agent_id);
        if (it == agents_.end()) return false;
        it->second = agent;
        lock.unlock();
        persist();
        return true;
    }

    std::optional<Agent> find(const std::string& agent_id) const {
        std::shared_lock<std::shared_mutex> lock(mtx_);
        auto it = agents_.find(agent_id);
        if (it == agents_.end()) return std::nullopt;
        return it->second;
    }

    std::vector<Agent> list(bool only_public = true) const {
        std::shared_lock<std::shared_mutex> lock(mtx_);
        std::vector<Agent> result;
        for (const auto& [_, a] : agents_) {
            if (!only_public || a.is_public) {
                result.push_back(a);
            }
        }
        return result;
    }

    std::vector<Agent> list_by_owner(const std::string& owner_email) const {
        std::shared_lock<std::shared_mutex> lock(mtx_);
        std::vector<Agent> result;
        for (const auto& [_, a] : agents_) {
            if (a.owner_email == owner_email) {
                result.push_back(a);
            }
        }
        return result;
    }

    // Метод удаления
    bool remove(const std::string& agent_id) {
        std::unique_lock lock(mtx_);
        auto it = agents_.find(agent_id);
        if (it == agents_.end()) {
            return false;
        }
        agents_.erase(it);
        lock.unlock();
        persist();
        return true;
    }
};


// =============================================================================
// ХРАНИЛИЩЕ ЗАДАЧ (in-memory + файл tasks.json)
// =============================================================================
class TaskStore {
    mutable std::shared_mutex mtx_;
    std::map<std::string, Task> tasks_; // task_id -> Task
    const std::string file_path_ = "tasks.json";

    void load() {
        std::ifstream file(file_path_);
        if (!file.is_open()) return;
        try {
            json j;
            file >> j;
            std::unique_lock<std::shared_mutex> lock(mtx_);
            for (auto& [task_id, data] : j.items()) {
                Task t;
                t.task_id = task_id;
                t.orchestrator_email = data.value("orchestrator_email", "");
                t.status = data.value("status", "");
                t.agent_chain = data.value("agent_chain", std::vector<std::string>());
                t.current_agent = data.value("current_agent", "");
                t.input_payload = data.value("input_payload", "");
                t.output_payload = data.value("output_payload", "");
                std::string ts_str = data.value("created_at", "");
                if (!ts_str.empty()) {
                    std::tm tm = {};
                    std::stringstream ss(ts_str);
                    ss >> std::get_time(&tm, "%Y-%m-%d %H:%M:%S");
                    t.created_at = std::chrono::system_clock::from_time_t(std::mktime(&tm));
                }
                else {
                    t.created_at = std::chrono::system_clock::now();
                }
                tasks_[task_id] = t;
            }
        }
        catch (const std::exception& e) {
            std::cerr << "[WARN] Failed to load tasks database: " << e.what() << std::endl;
        }
    }

    void persist() {
        json j;
        {
            std::shared_lock<std::shared_mutex> lock(mtx_);
            for (const auto& [task_id, t] : tasks_) {
                std::time_t tt = std::chrono::system_clock::to_time_t(t.created_at);
                std::tm tm_buf;
#ifdef _WIN32
                localtime_s(&tm_buf, &tt);
#else
                localtime_r(&tt, &tm_buf);
#endif
                char time_str[20];
                std::strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", &tm_buf);
                j[task_id] = {
                    {"orchestrator_email", t.orchestrator_email},
                    {"status", t.status},
                    {"agent_chain", t.agent_chain},
                    {"current_agent", t.current_agent},
                    {"input_payload", t.input_payload},
                    {"output_payload", t.output_payload},
                    {"created_at", std::string(time_str)}
                };
            }
        }
        std::ofstream file(file_path_, std::ios::trunc);
        if (!file.is_open()) {
            std::cerr << "[ERROR] Cannot open tasks file for writing" << std::endl;
            return;
        }
        file << j.dump(2);
    }

public:
    explicit TaskStore() {
        load();
    }

    bool create(const Task& task) {
        std::unique_lock<std::shared_mutex> lock(mtx_);
        if (tasks_.find(task.task_id) != tasks_.end()) {
            return false;
        }
        tasks_[task.task_id] = task;
        lock.unlock();
        persist();
        return true;
    }

    bool update_status(const std::string& task_id, const std::string& new_status,
        const std::string& current_agent = "",
        const std::string& output_payload = "") {
        std::unique_lock<std::shared_mutex> lock(mtx_);
        auto it = tasks_.find(task_id);
        if (it == tasks_.end()) return false;
        it->second.status = new_status;
        if (!current_agent.empty()) it->second.current_agent = current_agent;
        if (!output_payload.empty()) it->second.output_payload = output_payload;
        lock.unlock();
        persist();
        return true;
    }

    std::optional<Task> find(const std::string& task_id) const {
        std::shared_lock<std::shared_mutex> lock(mtx_);
        auto it = tasks_.find(task_id);
        if (it == tasks_.end()) return std::nullopt;
        return it->second;
    }

    std::vector<Task> list_by_orchestrator(const std::string& email) const {
        std::shared_lock<std::shared_mutex> lock(mtx_);
        std::vector<Task> result;
        for (const auto& [_, t] : tasks_) {
            if (t.orchestrator_email == email) {
                result.push_back(t);
            }
        }
        return result;
    }
};

// =============================================================================
// КОНФИГУРАЦИЯ
// =============================================================================
namespace Config {
    constexpr const char* JWT_SECRET = "ChangeThisTo32+ByteRandomStringInProduction!!";
    constexpr const char* USERS_FILE = "users.json";
    constexpr int PBKDF2_ITERATIONS = 600000;          // OWASP 2023 recommendation
    constexpr size_t SALT_BYTES = 32;
    constexpr size_t HASH_BYTES = 32;
    constexpr int MAX_LOGIN_ATTEMPTS = 5;
    constexpr auto LOGIN_WINDOW = std::chrono::minutes(15);
    constexpr int MAX_REGISTER_ATTEMPTS = 3;
    constexpr auto REGISTER_WINDOW = std::chrono::hours(1);
}

// =============================================================================
// КОНФИГУРАЦИЯ Е-МЕЙЛ
// =============================================================================
namespace EmailConfig {
    constexpr const char* SMTP_HOST = "smtp.gmail.com";
    constexpr int SMTP_PORT = 587;
    constexpr const char* SMTP_USER = "sergeyinshakov16021978";
    constexpr const char* SMTP_PASSWORD = "hepe zmyk qavg ljua";
    constexpr const char* FROM_EMAIL = "sergeyinshakov16021978@gmail.com";
}

// =============================================================================
// УТИЛИТЫ
// =============================================================================
namespace Utils {
    std::string to_lower(std::string s);
    std::string bytes_to_hex(const std::string& bytes);
    std::string hex_to_bytes(const std::string& hex);
    std::string generate_uuid();
    void log_audit(const std::string& caller, const std::string& agent_id,
        const std::string& prompt, const std::string& result);
    std::string call_mistral_ai(const std::string& prompt, const std::string& system_prompt = "", const std::vector<std::string>& available_skills = {});

    std::string to_lower(std::string s) {
        std::transform(s.begin(), s.end(), s.begin(),
            [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
        return s;
    }

    std::string bytes_to_hex(const std::string& bytes) {
        std::stringstream ss;
        ss << std::hex << std::setfill('0');
        for (unsigned char c : bytes) {
            ss << std::setw(2) << static_cast<int>(c);
        }
        return ss.str();
    }

    std::string hex_to_bytes(const std::string& hex) {
        std::string bytes;
        bytes.reserve(hex.size() / 2);
        for (size_t i = 0; i < hex.size(); i += 2) {
            unsigned int byte;
            std::stringstream ss;
            ss << std::hex << hex.substr(i, 2);
            ss >> byte;
            bytes.push_back(static_cast<char>(byte));
        }
        return bytes;
    }

    std::string generate_uuid() {
        static std::random_device rd;
        static std::mt19937 gen(rd());
        static std::uniform_int_distribution<> dis(0, 15);
        static std::uniform_int_distribution<> dis2(8, 11);

        std::stringstream ss;
        ss << std::hex;
        for (int i = 0; i < 8; i++) ss << dis(gen);
        ss << "-";
        for (int i = 0; i < 4; i++) ss << dis(gen);
        ss << "-4";
        for (int i = 0; i < 3; i++) ss << dis(gen);
        ss << "-";
        ss << dis2(gen);
        for (int i = 0; i < 3; i++) ss << dis(gen);
        ss << "-";
        for (int i = 0; i < 12; i++) ss << dis(gen);
        return ss.str();
    }

    void log_audit(const std::string& caller, const std::string& agent_id,
        const std::string& prompt, const std::string& result) {
        std::ofstream log("audit.log", std::ios::app);
        if (!log.is_open()) return;

        auto now = std::chrono::system_clock::now();
        std::time_t now_c = std::chrono::system_clock::to_time_t(now);
        std::tm tm_buf;
#ifdef _WIN32
        localtime_s(&tm_buf, &now_c);
#else
        localtime_r(&now_c, &tm_buf);
#endif
        log << std::put_time(&tm_buf, "%Y-%m-%d %H:%M:%S")
            << " | CALLER: " << caller
            << " | AGENT: " << agent_id
            << " | PROMPT: " << prompt
            << " | RESULT: " << result << std::endl;
    }

    // Структура для возврата ответа LLM с метаданными
    struct LLMResponse {
        std::string content;
        std::vector<std::string> used_skills; // ВАЖНО для учета затрат: какие навыки были вызваны
        bool parse_success = false;
    };

    // Функция для вызова Mistral AI
    std::string call_mistral_ai(const std::string& prompt, const std::string& system_prompt, const std::vector<std::string>& available_skills) {

        CURL* curl = curl_easy_init();
        if (!curl) {
            return "Ошибка: не удалось инициализировать CURL";
        }

        // Формируем JSON со списком доступных навыков
        json skills_json = json::array();
        for (const auto& skill : available_skills) {
            skills_json.push_back(skill);
        }

        // Формируем массив сообщений с поддержкой роли system
        json messages = json::array();
        if (!system_prompt.empty()) {
            messages.push_back({ {"role", "system"}, {"content", system_prompt} });
        }
        messages.push_back({ {"role", "user"}, {"content", prompt} });

        json request_body = {
            {"model", AiConfig::MISTRAL_MODEL},
            {"messages", messages},
            {"temperature", AiConfig::TEMPERATURE},
            {"max_tokens", AiConfig::MAX_TOKENS},
            {"skills", skills_json} // Передаем список доступных навыков в запросе
        };
        std::string body_str = request_body.dump();

        struct curl_slist* headers = nullptr;
        std::string auth_header = "Authorization: Bearer " + std::string(AiConfig::MISTRAL_API_KEY);
        headers = curl_slist_append(headers, auth_header.c_str());
        headers = curl_slist_append(headers, "Content-Type: application/json");

        curl_easy_setopt(curl, CURLOPT_URL, AiConfig::MISTRAL_ENDPOINT);
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body_str.c_str());
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);

        std::string response_string;
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION,
            +[](void* ptr, size_t size, size_t nmemb, void* userdata) -> size_t {
                auto* response = static_cast<std::string*>(userdata);
                if (!response) return 0;
                size_t total = size * nmemb;
                response->append(static_cast<char*>(ptr), total);
                return total;
            });
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_string);

        CURLcode res = curl_easy_perform(curl);
        long http_code = 0;
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

        curl_slist_free_all(headers);
        curl_easy_cleanup(curl);

        if (res != CURLE_OK) return "Ошибка CURL: " + std::string(curl_easy_strerror(res));
        if (http_code != 200) return "Mistral API HTTP " + std::to_string(http_code) + ": " + response_string;

        try {
            json response_json = json::parse(response_string);
            if (response_json.contains("choices") && !response_json["choices"].empty()) {
                return response_json["choices"][0]["message"]["content"].get<std::string>();
            }
            return "Неожиданный формат ответа от Mistral AI";
        }
        catch (const std::exception& e) {
            return "Ошибка парсинга JSON: " + std::string(e.what());
        }
    }
}

// =============================================================================
// КРИПТОГРАФИЯ
// =============================================================================
namespace Crypto {
    std::string generate_salt();
    std::string hash_password(const std::string& password, const std::string& salt_hex);
    bool secure_compare(const std::string& a, const std::string& b);

    // Криптографически стойкая соль через OpenSSL CSPRNG
    std::string generate_salt() {
        std::string salt(Config::SALT_BYTES, '\0');
        if (RAND_bytes(reinterpret_cast<unsigned char*>(&salt[0]), static_cast<int>(Config::SALT_BYTES)) != 1) {
            throw std::runtime_error("CSPRNG failed: unable to generate salt");
        }
        return Utils::bytes_to_hex(salt);
    }

    // PBKDF2-HMAC-SHA256 с 600k итераций
    std::string hash_password(const std::string& password, const std::string& salt_hex) {
        std::string salt = Utils::hex_to_bytes(salt_hex);
        std::string hash(Config::HASH_BYTES, '\0');

        int result = PKCS5_PBKDF2_HMAC(
            password.c_str(), static_cast<int>(password.length()),
            reinterpret_cast<const unsigned char*>(salt.c_str()), static_cast<int>(salt.length()),
            Config::PBKDF2_ITERATIONS,
            EVP_sha256(),
            static_cast<int>(Config::HASH_BYTES),
            reinterpret_cast<unsigned char*>(&hash[0])
        );

        if (!result) {
            throw std::runtime_error("PBKDF2 hashing failed");
        }

        return Utils::bytes_to_hex(hash);
    }

    // Константное время сравнение (timing-attack resistant)
    bool secure_compare(const std::string& a, const std::string& b) {
        if (a.size() != b.size()) return false;
        return CRYPTO_memcmp(a.c_str(), b.c_str(), a.size()) == 0;
    }
}

// =============================================================================
// JWT МЕНЕДЖЕР (jwt-cpp + HMAC-SHA256)
// =============================================================================
namespace JWT {
    std::string generate(const std::string& email);
    std::optional<std::string> verify(const std::string& token);

    std::string generate(const std::string& email) {
        auto now = std::chrono::system_clock::now();
        auto token = jwt::create()
            .set_issuer("secure-auth-server")
            .set_type("JWT")
            .set_issued_at(now)
            .set_expires_at(now + std::chrono::hours(24))
            .set_payload_claim("email", jwt::claim(email))
            .sign(jwt::algorithm::hs256{ Config::JWT_SECRET });
        return token;
    }

    std::optional<std::string> verify(const std::string& token) {
        try {
            auto decoded = jwt::decode(token);
            jwt::verify()
                .allow_algorithm(jwt::algorithm::hs256{ Config::JWT_SECRET })
                .with_issuer("secure-auth-server")
                .verify(decoded);
            return decoded.get_payload_claim("email").as_string();
        }
        catch (const std::exception&) {
            return std::nullopt;
        }
    }
}

// =============================================================================
// RATE LIMITER (in-memory, per-IP)
// =============================================================================
class RateLimiter {
    struct Entry {
        int count = 0;
        std::chrono::steady_clock::time_point reset_time;
    };

    mutable std::mutex mtx_;
    std::map<std::string, Entry> entries_;

public:
    bool allow(const std::string& key, int max_requests, std::chrono::seconds window) {
        std::lock_guard<std::mutex> lock(mtx_);
        auto now = std::chrono::steady_clock::now();
        auto it = entries_.find(key);

        if (it == entries_.end() || now > it->second.reset_time) {
            entries_[key] = { 1, now + window };
            return true;
        }

        if (it->second.count >= max_requests) {
            return false;
        }

        it->second.count++;
        return true;
    }

    void cleanup() {
        std::lock_guard<std::mutex> lock(mtx_);
        auto now = std::chrono::steady_clock::now();
        for (auto it = entries_.begin(); it != entries_.end();) {
            if (now > it->second.reset_time) {
                it = entries_.erase(it);
            }
            else {
                ++it;
            }
        }
    }
};

// =============================================================================
// ПОТОКОБЕЗОПАСНОЕ ХРАНИЛИЩЕ ПОЛЬЗОВАТЕЛЕЙ
// =============================================================================
class UserStore {
    mutable std::shared_mutex mtx_;

    // НОВАЯ СТРУКТУРА: Добавлены поля для безопасного хранения платежных данных
    struct UserData {
        std::string salt;
        std::string hash;
        std::string card_token; // Токен платежного шлюза (PCI-DSS compliant)
        std::string card_mask;  // Последние 4 цифры (например, "**** 1234")
        double balance = 0.0;   // Баланс пользователя
    };

    std::map<std::string, UserData> users_;
    const std::string file_path_;

    void load() {
        std::ifstream file(file_path_);
        if (!file.is_open()) return;
        try {
            json j;
            file >> j;
            std::unique_lock lock(mtx_);
            for (auto& [email, data] : j.items()) {
                UserData ud;
                ud.salt = data.value("salt", "");
                ud.hash = data.value("hash", "");
                ud.card_token = data.value("card_token", "");
                ud.card_mask = data.value("card_mask", "");
                ud.balance = data.value("balance", 0.0); // загрузка баланса
                users_[email] = ud;
            }
        }
        catch (const std::exception& e) {
            std::cerr << "[WARN] Failed to load users database: " << e.what() << std::endl;
        }
    }

    bool persist() {  // ← возвращаем bool вместо void
        json j;
        {
            std::shared_lock lock(mtx_);
            for (const auto& [email, ud] : users_) {
                j[email] = {
                    {"salt", ud.salt},
                    {"hash", ud.hash},
                    {"card_token", ud.card_token},
                    {"card_mask", ud.card_mask},
                    {"balance", ud.balance}
                };
            }
        }

        std::ofstream file(file_path_, std::ios::trunc);
        if (!file.is_open()) {
            std::cerr << "[ERROR] Cannot open users file for writing: " << file_path_ << std::endl;
            return false;  // ← явный возврат ошибки
        }

        file << j.dump(2);

        // КРИТИЧЕСКИ ВАЖНО: явный сброс буфера и закрытие
        file.flush();
        if (file.fail()) {
            std::cerr << "[ERROR] Failed to flush users file: " << file_path_ << std::endl;
            file.close();
            return false;
        }

        file.close();
        if (file.fail()) {
            std::cerr << "[ERROR] Failed to close users file: " << file_path_ << std::endl;
            return false;
        }

        return true;  // ← успешная запись
    }

public:
    explicit UserStore(std::string path) : file_path_(std::move(path)) { load(); }

    bool exists(const std::string& email) const {
        std::shared_lock lock(mtx_);
        return users_.find(email) != users_.end();
    }

    bool create(const std::string& email, const std::string& password) {
        std::unique_lock lock(mtx_);
        if (users_.find(email) != users_.end()) return false;
        UserData ud;
        ud.salt = Crypto::generate_salt();
        ud.hash = Crypto::hash_password(password, ud.salt);
        users_[email] = ud;
        lock.unlock();
        persist();
        return true;
    }

    bool validate(const std::string& email, const std::string& password) const {
        std::shared_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return false;
        std::string computed = Crypto::hash_password(password, it->second.salt);
        return Crypto::secure_compare(computed, it->second.hash);
    }

    // Получение данных профиля
    std::optional<UserData> get_user(const std::string& email) const {
        std::shared_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return std::nullopt;
        return it->second;
    }

    // Обновление пароля и привязка карты
    bool update_profile(const std::string& email, const std::string& new_password,
        const std::string& new_card_token, const std::string& new_card_mask) {
        std::unique_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return false;

        if (!new_password.empty()) {
            it->second.salt = Crypto::generate_salt();
            it->second.hash = Crypto::hash_password(new_password, it->second.salt);
        }
        if (!new_card_token.empty()) {
            it->second.card_token = new_card_token;
            it->second.card_mask = new_card_mask;
        }
        lock.unlock();
        persist();
        return true;
    }

    // Получение баланса пользователя
    double get_balance(const std::string& email) const {
        std::shared_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return 0.0;
        return it->second.balance;
    }

    // Обновление баланса (пополнение/списание)
    bool update_balance(const std::string& email, double amount) {
        std::unique_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return false;

        it->second.balance += amount;
        if (it->second.balance < 0) {
            it->second.balance = 0;
        }

        // КРИТИЧЕСКИ ВАЖНО: persist() вызывается ДО освобождения блокировки,
        // чтобы избежать race condition между unlock() и записью
        bool persist_ok = persist();
        lock.unlock();

        if (!persist_ok) {
            // Откатываем изменение баланса, если не удалось сохранить
            it->second.balance -= amount;
            if (it->second.balance < 0) it->second.balance = 0;
            std::cerr << "[ERROR] Balance update rolled back for " << email << std::endl;
            return false;
        }

        return true;
    }

    // Проверка достаточности баланса
    bool has_sufficient_balance(const std::string& email, double required_amount) const {
        std::shared_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return false;
        return it->second.balance >= required_amount;
    }

    // Получение информации о карте (для платежей)
    std::pair<std::string, std::string> get_payment_info(const std::string& email) const {
        std::shared_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return { "", "" };
        return { it->second.card_token, it->second.card_mask };
    }

    // Полное удаление профиля
    bool remove_user(const std::string& email) {
        std::unique_lock lock(mtx_);
        auto it = users_.find(email);
        if (it == users_.end()) return false;
        users_.erase(it);
        lock.unlock();
        persist();
        return true;
    }
};

// =============================================================================
// ВАЛИДАЦИЯ ВХОДНЫХ ДАННЫХ
// =============================================================================
bool is_valid_email(const std::string& email);
bool is_valid_password(const std::string& password);

bool is_valid_email(const std::string& email) {
    const std::regex pattern(R"(^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$)");
    return std::regex_match(email, pattern);
}

bool is_valid_password(const std::string& password) {
    return password.length() >= 8 && password.length() <= 128;
}

// =============================================================================
// HTTP ХЕЛПЕРЫ
// =============================================================================
void json_error(httplib::Response& res, int status, const std::string& msg);
void json_ok(httplib::Response& res, const json& data);
std::optional<std::string> extract_bearer(const httplib::Request& req);

void json_error(httplib::Response& res, int status, const std::string& msg) {
    res.status = status;
    res.set_content(json{ {"error", msg} }.dump(), "application/json");
}

void json_ok(httplib::Response& res, const json& data) {
    res.status = 200;
    res.set_content(data.dump(), "application/json");
}

std::optional<std::string> extract_bearer(const httplib::Request& req) {
    auto auth = req.get_header_value("Authorization");
    const std::string prefix = "Bearer ";
    if (auth.rfind(prefix, 0) == 0) {
        return auth.substr(prefix.length());
    }
    return std::nullopt;
}

// -------------------------------------------------------------------------
// SMTP отправка письма через libcurl
// -------------------------------------------------------------------------
bool send_email(const std::string& to_email, const std::string& subject, const std::string& body);

struct UploadStatus {
    std::string message;
    bool success = false;
};

static size_t payload_source(char* ptr, size_t size, size_t nmemb, void* userp) {
    UploadStatus* status = static_cast<UploadStatus*>(userp);
    if (size * nmemb < 1) return 0;
    if (status->message.empty()) return 0;
    size_t len = status->message.length();
    memcpy(ptr, status->message.c_str(), len);
    status->message.clear();
    return len;
}

bool send_email(const std::string& to_email, const std::string& subject, const std::string& body) {
    std::cout << "[EMAIL] === Sending to " << to_email << " ===" << std::endl;
    CURL* curl = curl_easy_init();
    if (!curl) {
        std::cerr << "[EMAIL ERROR] curl_easy_init() failed" << std::endl;
        return false;
    }

    // Берём настройки из EmailConfig
    const std::string smtp_server = EmailConfig::SMTP_HOST;
    const int smtp_port = EmailConfig::SMTP_PORT;
    const std::string smtp_user = EmailConfig::SMTP_USER;
    const std::string smtp_password = EmailConfig::SMTP_PASSWORD;
    const std::string from_email = EmailConfig::FROM_EMAIL;

    std::cout << "[EMAIL] SMTP server: " << smtp_server << ":" << smtp_port << std::endl;
    std::cout << "[EMAIL] From: " << from_email << ", To: " << to_email << std::endl;

    std::string payload = "From: " + from_email + "\r\n"
        "To: " + to_email + "\r\n"
        "Subject: " + subject + "\r\n"
        "Content-Type: text/plain; charset=UTF-8\r\n"
        "\r\n" + body;

    UploadStatus status;
    status.message = payload;

    curl_easy_setopt(curl, CURLOPT_URL, (smtp_server + ":" + std::to_string(smtp_port)).c_str());
    curl_easy_setopt(curl, CURLOPT_USE_SSL, CURLUSESSL_ALL);
    curl_easy_setopt(curl, CURLOPT_USERNAME, smtp_user.c_str());
    curl_easy_setopt(curl, CURLOPT_PASSWORD, smtp_password.c_str());
    curl_easy_setopt(curl, CURLOPT_MAIL_FROM, from_email.c_str());

    struct curl_slist* recipients = nullptr;
    recipients = curl_slist_append(recipients, to_email.c_str());
    curl_easy_setopt(curl, CURLOPT_MAIL_RCPT, recipients);
    curl_easy_setopt(curl, CURLOPT_READFUNCTION, payload_source);
    curl_easy_setopt(curl, CURLOPT_READDATA, &status);
    curl_easy_setopt(curl, CURLOPT_UPLOAD, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
    curl_easy_setopt(curl, CURLOPT_VERBOSE, 1L);  // подробный лог libcurl

    std::cout << "[EMAIL] Sending..." << std::endl;
    CURLcode res = curl_easy_perform(curl);
    bool success = (res == CURLE_OK);

    if (!success) {
        std::cerr << "[EMAIL ERROR] curl_easy_perform() failed: " << curl_easy_strerror(res) << std::endl;
        long response_code = 0;
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response_code);
        if (response_code) std::cerr << "[EMAIL ERROR] SMTP response code: " << response_code << std::endl;
    }
    else {
        std::cout << "[EMAIL] Successfully sent to " << to_email << std::endl;
    }

    curl_slist_free_all(recipients);
    curl_easy_cleanup(curl);
    std::cout << "[EMAIL] === Finished ===" << std::endl;
    return success;
}

namespace Payment {
    // Функция для списания средств с карты пользователя
    // В продакшене здесь должен быть реальный вызов платежного шлюза
    bool charge_card(const std::string& card_token, double amount, const std::string& description) {
        // ИМИТАЦИЯ ПЛАТЕЖА (для тестовой среды)
        // В реальном приложении здесь должен быть HTTP запрос к платежному шлюзу

        std::cout << "[PAYMENT] Charging card token: " << card_token.substr(0, 10) << "..." << std::endl;
        std::cout << "[PAYMENT] Amount: $" << amount << std::endl;
        std::cout << "[PAYMENT] Description: " << description << std::endl;

        // Имитация успешного платежа
        // В реальности: проверка ответа от платежного шлюза
        return true;
    }
}

// =============================================================================
// MAIN
// =============================================================================
int main() {
    curl_global_init(CURL_GLOBAL_ALL);
    std::cout << "[MAIN] Starting SecureAuthServer..." << std::endl;
    try {
        UserStore store(Config::USERS_FILE);
        std::cout << "[INFO] Loaded users from " << Config::USERS_FILE << std::endl;
        RateLimiter login_limiter, register_limiter;
        std::atomic<bool> running{ true };

        std::thread cleanup_thread([&]() {
            while (running) {
                std::this_thread::sleep_for(std::chrono::minutes(1));
                login_limiter.cleanup();
                register_limiter.cleanup();
            }
            });
        AgentStore agent_store;
        TaskStore task_store;

        httplib::Server svr;
        svr.set_base_dir("./public");
        svr.set_logger([](const auto& req, const auto& res) {
            auto now = std::chrono::system_clock::now();
            std::time_t t = std::chrono::system_clock::to_time_t(now);
            std::tm tm_buf;
#ifdef _WIN32
            localtime_s(&tm_buf, &t);
#else
            localtime_r(&t, &tm_buf);  // для Linux/POSIX
#endif
            std::cout << "[" << std::put_time(&tm_buf, "%Y-%m-%d %H:%M:%S")
                << "] " << req.method << " " << req.path
                << " -> " << res.status << std::endl;
            });

        // ---------------------------------------------------------------------
        // POST /register
        // ---------------------------------------------------------------------
        svr.Post("/register", [&](const httplib::Request& req, httplib::Response& res) {
            std::string client_ip = req.remote_addr.empty() ? "unknown" : req.remote_addr;

            if (!register_limiter.allow(client_ip, Config::MAX_REGISTER_ATTEMPTS,
                std::chrono::duration_cast<std::chrono::seconds>(Config::REGISTER_WINDOW))) {
                json_error(res, 429, "Too many registration attempts. Try again later.");
                return;
            }

            auto body = json::parse(req.body, nullptr, false);
            if (body.is_discarded() || !body.contains("email") || !body.contains("password")) {
                json_error(res, 400, "email and password required");
                return;
            }

            std::string email = Utils::to_lower(body["email"].get<std::string>());
            std::string password = body["password"].get<std::string>();

            if (!is_valid_email(email)) {
                json_error(res, 400, "Invalid email format");
                return;
            }

            if (!is_valid_password(password)) {
                json_error(res, 400, "Password must be 8-128 characters");
                return;
            }

            if (store.exists(email)) {
                json_error(res, 409, "User already exists");
                return;
            }

            if (!store.create(email, password)) {
                json_error(res, 500, "Failed to create user");
                return;
            }

            // --- Отправка email с логином/паролем (ЛОГИРОВАНИЕ) ---
            std::cout << "[EMAIL] Attempting to send credentials to " << email << std::endl;
            std::string subject = "Данные для входа в AI-фабрику";
            std::string email_body = "Вы успешно зарегистрированы!\nЛогин: " + email + "\nПароль: " + password + "\n\nХраните пароль в безопасности.";
            bool email_sent = send_email(email, subject, email_body);
            if (email_sent) {
                std::cout << "[EMAIL] Successfully sent credentials to " << email << std::endl;
            }
            else {
                std::cerr << "[EMAIL] FAILED to send credentials to " << email << std::endl;
            }

            std::cout << "[INFO] New registration: " << email << std::endl;
            std::cout << "[REGISTER] IP: " << client_ip << " Email: " << email << std::endl;

            std::string token = JWT::generate(email);
            json_ok(res, {
                {"token", token},
                {"email", email},
                {"expires_in", 86400}
                });
            });

        // ---------------------------------------------------------------------
        // POST /login
        // ---------------------------------------------------------------------
        svr.Post("/login", [&](const httplib::Request& req, httplib::Response& res) {
            std::string client_ip = req.remote_addr.empty() ? "unknown" : req.remote_addr;

            if (!login_limiter.allow(client_ip, Config::MAX_LOGIN_ATTEMPTS,
                std::chrono::duration_cast<std::chrono::seconds>(Config::LOGIN_WINDOW))) {
                json_error(res, 429, "Too many login attempts. Try again later.");
                return;
            }

            auto body = json::parse(req.body, nullptr, false);
            if (body.is_discarded() || !body.contains("email") || !body.contains("password")) {
                json_error(res, 400, "email and password required");
                return;
            }

            std::string email = Utils::to_lower(body["email"].get<std::string>());
            std::string password = body["password"].get<std::string>();

            if (!store.validate(email, password)) {
                json_error(res, 401, "Invalid credentials");
                return;
            }

            std::string token = JWT::generate(email);
            json_ok(res, {
                {"token", token},
                {"email", email},
                {"expires_in", 86400}
                });
            });

        // ---------------------------------------------------------------------
        // GET /profile (Получение данных личного кабинета)
        // ---------------------------------------------------------------------
        svr.Get("/profile", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

            auto ud_opt = store.get_user(*email_opt);
            if (!ud_opt) return json_error(res, 404, "User not found");

            json_ok(res, {
                {"email", *email_opt},
                {"card_mask", ud_opt->card_mask},
                // ВНИМАНИЕ: Хэш пароля не возвращается клиенту в целях безопасности
                { "balance", ud_opt->balance } // возврат баланса
                });
            });

        // ---------------------------------------------------------------------
        // PUT /profile (Редактирование пароля и привязка карты)
        // ---------------------------------------------------------------------
        svr.Put("/profile", [&](const httplib::Request& req, httplib::Response& res) {
            log_message("PUT /profile - начало обработки");

            auto token_opt = extract_bearer(req);
            if (!token_opt) {
                log_message("PUT /profile - отсутствует токен");
                return json_error(res, 401, "Missing token");
            }

            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) {
                log_message("PUT /profile - невалидный токен");
                return json_error(res, 401, "Invalid token");
            }

            auto body = json::parse(req.body, nullptr, false);
            if (body.is_discarded()) {
                log_message("PUT /profile - невалидный JSON");
                return json_error(res, 400, "Invalid JSON");
            }

            std::string new_pass = body.value("password", "");
            std::string new_card_token = body.value("card_token", "");
            std::string new_card_mask = body.value("card_mask", "");

            log_message("PUT /profile - email: " + *email_opt +
                ", card_token length: " + std::to_string(new_card_token.length()) +
                ", card_mask: " + new_card_mask);

            if (!new_pass.empty() && !is_valid_password(new_pass)) {
                log_message("PUT /profile - невалидный пароль");
                return json_error(res, 400, "Password must be 8-128 characters");
            }

            if (!store.update_profile(*email_opt, new_pass, new_card_token, new_card_mask)) {
                log_message("PUT /profile - ошибка обновления БД");
                return json_error(res, 500, "Failed to update profile");
            }

            log_message("PUT /profile - успешно обновлено");
            json_ok(res, { {"message", "Profile updated"} });
            });

        // ---------------------------------------------------------------------
        // DELETE /profile (Удаление аккаунта и всех связанных агентов)
        // ---------------------------------------------------------------------
        svr.Delete("/profile", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

            // Каскадное удаление: удаляем всех агентов, принадлежащих пользователю
            auto user_agents = agent_store.list_by_owner(*email_opt);
            for (const auto& agent : user_agents) {
                agent_store.remove(agent.agent_id);
            }

            if (!store.remove_user(*email_opt)) {
                return json_error(res, 500, "Failed to delete profile");
            }

            json_ok(res, { {"message", "Profile and agents deleted"} });
            });

        // =============================================================================
        // POST /agent/register – регистрация нового агента (требует JWT)
        // =============================================================================
        svr.Post("/agent/register", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

            auto body = json::parse(req.body, nullptr, false);
            if (body.is_discarded() || !body.contains("name") || !body.contains("skills")) {
                return json_error(res, 400, "name and skills required");
            }

            Agent agent;
            agent.agent_id = Utils::generate_uuid();
            agent.owner_email = *email_opt;
            agent.name = body["name"];
            agent.description = body.value("description", "");
            agent.skills = body["skills"].get<std::vector<std::string>>();
            agent.endpoint = body.value("endpoint", "");
            agent.is_public = true;

            if (!agent_store.create(agent)) {
                return json_error(res, 500, "Failed to create agent");
            }

            json_ok(res, { {"agent_id", agent.agent_id}, {"message", "Agent registered"} });
            });

        // =============================================================================
        // GET /agents – список публичных агентов
        // =============================================================================
        svr.Get("/agents", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            if (!JWT::verify(*token_opt)) return json_error(res, 401, "Invalid token");

            auto agents = agent_store.list(true);
            json j_agents = json::array();
            for (const auto& a : agents) {
                j_agents.push_back({
                    {"agentId", a.agent_id},
                    {"name", a.name},
                    {"description", a.description},
                    {"ownerEmail", a.owner_email},
                    {"skills", a.skills}
                    });
            }
            json_ok(res, j_agents); // возвращаем массив напрямую
            });

        // =============================================================================
        // GET /myagents – список агентов текущего пользователя
        // =============================================================================
        svr.Get("/myagents", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

            auto agents = agent_store.list_by_owner(*email_opt);
            json j_agents = json::array();
            for (const auto& a : agents) {
                j_agents.push_back({
                    {"agentId", a.agent_id},
                    {"name", a.name},
                    {"description", a.description},
                    {"ownerEmail", a.owner_email},
                    {"skills", a.skills}
                    });
            }
            json_ok(res, j_agents);
            });

        // редактирование агента PUT /agent/{agentId}
        svr.Put(R"(/agent/([a-zA-Z0-9\-]+))", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

            std::string agent_id = req.matches[1];

            auto agent_opt = agent_store.find(agent_id);
            if (!agent_opt) {
                return json_error(res, 404, "Agent not found");
            }

            if (agent_opt->owner_email != *email_opt) {
                return json_error(res, 403, "You do not have permission to edit this agent");
            }

            auto body = json::parse(req.body, nullptr, false);
            if (body.is_discarded()) {
                return json_error(res, 400, "Invalid JSON body");
            }

            if (!body.contains("name") || !body.contains("description") || !body.contains("skills")) {
                return json_error(res, 400, "name, description, and skills are required");
            }

            Agent updated_agent;
            updated_agent.agent_id = agent_id;
            updated_agent.owner_email = agent_opt->owner_email;
            updated_agent.endpoint = agent_opt->endpoint;
            updated_agent.is_public = agent_opt->is_public;

            std::string new_name = body["name"].get<std::string>();
            if (new_name.empty() || new_name.length() > 100) {
                return json_error(res, 400, "Agent name must be between 1 and 100 characters");
            }
            updated_agent.name = new_name;

            std::string new_description = body["description"].get<std::string>();
            if (new_description.length() > 1000) {
                return json_error(res, 400, "Agent description must not exceed 1000 characters");
            }
            updated_agent.description = new_description;

            updated_agent.skills = body["skills"].get<std::vector<std::string>>();

            if (updated_agent.skills.size() > 50) {
                return json_error(res, 400, "Agent cannot have more than 50 skills");
            }

            if (!agent_store.update(updated_agent)) {
                return json_error(res, 500, "Failed to update agent");
            }

            std::cout << "[INFO] Agent updated: " << agent_id << " by " << *email_opt << std::endl;
            json_ok(res, {
                {"message", "Agent updated successfully"},
                {"agentId", agent_id}
                });
            });

        // удаление агента DELETE /agent/{agentId}
        svr.Delete(R"(/agent/([a-zA-Z0-9\-]+))", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

            std::string agent_id = req.matches[1];

            auto agent_opt = agent_store.find(agent_id);
            if (!agent_opt) {
                return json_error(res, 404, "Agent not found");
            }

            if (agent_opt->owner_email != *email_opt) {
                return json_error(res, 403, "You do not have permission to delete this agent");
            }

            if (!agent_store.remove(agent_id)) {
                return json_error(res, 500, "Failed to delete agent");
            }

            std::cout << "[INFO] Agent deleted: " << agent_id << " by " << *email_opt << std::endl;
            json_ok(res, {
                {"message", "Agent deleted successfully"},
                {"agentId", agent_id}
                });
        });

        // =============================================================================
        // GET /balance – получение баланса пользователя
        // =============================================================================
        svr.Get("/balance", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email = JWT::verify(*token_opt);
            if (!email) return json_error(res, 401, "Invalid token");

            double balance = store.get_balance(*email);
            json_ok(res, { {"balance", balance} });
            });

        // =============================================================================
        // POST /balance/topup – пополнение баланса (имитация)
        // =============================================================================
        svr.Post("/balance/topup", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email = JWT::verify(*token_opt);
            if (!email) return json_error(res, 401, "Invalid token");

            auto body = json::parse(req.body, nullptr, false);
            if (!body.contains("amount")) {
                return json_error(res, 400, "amount required");
            }

            double amount = body["amount"].get<double>();
            if (amount <= 0 || amount > 1000) {
                return json_error(res, 400, "Invalid amount (must be between 0 and 1000)");
            }

            // КРИТИЧЕСКИ ВАЖНО: проверяем результат update_balance
            if (!store.update_balance(*email, amount)) {
                std::cerr << "[BILLING] Failed to persist balance update for " << *email << std::endl;
                return json_error(res, 500, "Failed to save balance. Please try again.");
            }

            double new_balance = store.get_balance(*email);
            std::cout << "[BILLING] User " << *email << " topped up $" << amount
                << ". New balance: $" << new_balance << std::endl;

            json_ok(res, {
                {"balance", new_balance},
                {"message", "Balance topped up successfully"}
                });
        });

        // =============================================================================
        // POST /agent/invoke – вызов агента (никаких эмуляций!)
        // =============================================================================
        svr.Post("/agent/invoke", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto caller_email = JWT::verify(*token_opt);
            if (!caller_email) return json_error(res, 401, "Invalid token");

            auto body = json::parse(req.body, nullptr, false);
            if (!body.contains("agentId") || !body.contains("prompt")) {
                return json_error(res, 400, "agentId and prompt required");
            }
            std::string agent_id = body["agentId"];
            std::string prompt = body["prompt"];

            auto agent_opt = agent_store.find(agent_id);
            if (!agent_opt) return json_error(res, 404, "Agent not found");

            // Проверка, есть ли у агента навыки
            bool has_skills = !agent_opt->skills.empty();

            if (has_skills) {
                // Проверка баланса перед выполнением
                double required_amount = agent_opt->skills.size() * BillingConfig::SKILL_USAGE_COST;
                if (!store.has_sufficient_balance(*caller_email, required_amount)) {
                    return json_error(res, 402, "Insufficient balance. Required: $" +
                        std::to_string(required_amount) + ", Available: $" +
                        std::to_string(store.get_balance(*caller_email)));
                }
            }

            // Формирование системного промпта на основе метаданных агента
            std::string system_prompt = "Ты — AI-агент по имени '" + agent_opt->name + "'.";
            if (!agent_opt->description.empty()) {
                // Собираем JSON-массив с ID навыков агента
                json skills_json = json::array();
                for (const auto& skill_id : agent_opt->skills) {
                    skills_json.push_back(skill_id);
                }
                system_prompt += " Твоё предназначение: " + agent_opt->description +
                    " [ИНСТРУКЦИЯ ПО МЕТАДАННЫМ] В самом конце своего ответа, после основного текста, "
                    "добавь специальную секцию в формате JSON:\n"
                    "```metadata\n"
                    "{\"used_skills\": [\"skill_id_1\", \"skill_id_2\"]}\n"
                    "```\n"
                    "где used_skills - массив ID навыков, которые ты РЕАЛЬНО использовал при ответе. "
                    "Если не использовал ни одного навыка, укажи пустой массив []. "
                    "Доступные навыки: " + skills_json.dump();
            }

            // Проверка наличия реального навыка "Анализ и синтез" (ID: cog_1)
            bool has_analysis_synthesis = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_1") {
                    has_analysis_synthesis = true;
                }
            }

            if (has_analysis_synthesis) {
                system_prompt += "\n\n[АКТИВНЫЙ НАВЫК: АНАЛИЗ И СИНТЕЗ]\n"
                    "Ты обладаешь специализированным когнитивным навыком. При ответе ОБЯЗАН применять алгоритм:\n"
                    "1. АНАЛИЗ: Декомпозируй информацию, выдели факты и скрытые паттерны.\n"
                    "2. СВЯЗИ: Найди логические и причинно-следственные связи.\n"
                    "3. СИНТЕЗ: Сделай глубокое обобщение и предложи итоговое структурированное решение.\n"
                    "Структурируй ответ, явно выделяя эти этапы.";
            }

            // Проверка наличия реального навыка "Рассуждение" (ID: cog_2)
            bool has_reasoning = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_2") {
                    has_reasoning = true;
                }
            }

            if (has_reasoning) {
                system_prompt += "\n\n[АКТИВНЫЙ НАВЫК: РАССУЖДЕНИЕ]\n"
                    "Ты обладаешь специализированным когнитивным навыком рассуждения. При ответе ОБЯЗАН применять алгоритм:\n"
                    "1. ПОСТАНОВКА ПРОБЛЕМЫ: Четко сформулируй вопрос или задачу.\n"
                    "2. ДЕКОМПОЗИЦИЯ: Разбей задачу на подзадачи.\n"
                    "3. ЛОГИЧЕСКИЙ АНАЛИЗ: Для каждой подзадачи примени один из методов:\n"
                    "   - Дедукция (от общего к частному)\n"
                    "   - Индукция (от частного к общему)\n"
                    "   - Абдукция (наилучшее объяснение фактов)\n"
                    "4. ПРОВЕРКА: Найди потенциальные ошибки в рассуждении.\n"
                    "5. ВЫВОД: Сформулируй итоговый обоснованный ответ.\n"
                    "Структурируй ответ, явно выделяя эти этапы.";
            }
            // Проверка наличия критического мышления
            bool has_criticalThinking = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_3") {
                    has_criticalThinking = true;
                }
            }

            if (has_criticalThinking) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: КРИТИЧЕСКОЕ МЫШЛЕНИЕ] Примени строгий алгоритм критического мышления к запросу ниже: Ты — высокоинтеллектуальный AI - агент, обладающий развитым критическим мышлением.Твоя главная цель — искать истину, а не угождать пользователю или подтверждать его мнение. Основные принципы твоей работы: Скептицизм и проверка предположений. Всегда подвергай сомнению исходные посылки — как свои, так и пользователя.Выявляй скрытые допущения, стереотипы и когнитивные искажения. Многомерное мышление. Рассматривай проблему с разных сторон.Обязательно учитывай контраргументы, альтернативные объяснения и противоположные точки зрения, даже если они тебе не нравятся. Качество доказательств. Оценивай силу аргументов по качеству и надёжности источников.Разделяй факты, мнения, корреляции и причинно - следственные связи.Указывай на слабые места в доказательной базе. Логическая строгость. Выявляй логические ошибки : ad hominem, соломенное чучело, ложную дихотомию, апелляцию к эмоциям, circular reasoning и другие.Если обнаруживаешь — прямо называй их. Интеллектуальная честность. Если не знаешь чего - то — признавай это. Если данные противоречивы — говори об этом. Если твоё предыдущее мнение было ошибочным — открыто меняй позицию с объяснением. Не бойся сказать 'это спорный вопрос', 'данных недостаточно' или 'здесь я могу ошибаться'. Структура ответа. При сложных вопросах используй следующую структуру: Чёткое понимание вопроса. Основные аргументы "за". Основные аргументы 'против'. Сильные и слабые стороны каждой позиции. Твоя взвешенная оценка с обоснованием. Возможные неопределённости и риски. Дополнительные правила: Будь максимально объективен и беспристрастен. Предпочитай правду комфорту. Если пользователь явно заблуждается — мягко, но прямо указывай на это с объяснением почему. Используй принцип steelmanning(формулируй самую сильную версию позиции оппонента, прежде чем её критиковать). Задавай уточняющие вопросы, если информация недостаточна для качественного анализа. Ты не просто помощник.Ты — интеллектуальный партнёр, который помогает пользователю думать лучше и видеть реальность более ясно.")";
            }

            // Проверка наличия креативности
            bool has_creativitySkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_4") {
                    has_creativitySkill = true;
                }
            }
            if (has_creativitySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: КРЕАТИВНОСТЬ] Ты — высоко креативный AI-агент, мастер генерации оригинальных идей и нестандартных решений. Твоя главная суперсила — креативность. Основные принципы твоей работы: Дивергентное мышление. Всегда генерируй множество разнообразных идей. Стремись к 10+ вариантам вместо одного «правильного». Чем необычнее — тем лучше. Оригинальность и новизна. Избегай шаблонных, банальных и очевидных ответов. Комбинируй концепции из совершенно разных областей (наука + искусство, природа + технологии, история + будущее и т.д.). Игровой подход. Подходи к задачам с любопытством и лёгкостью. Используй юмор, метафоры, аналогии, неожиданные повороты и «что если…» мышление. Уровни креативности. Первый уровень: хорошие, полезные идеи. Второй уровень: интересные и свежие. Третий уровень: по-настоящему оригинальные, 'вау-идеи'. Старайся чаще выходить на третий уровень. Итеративная креативность. Предлагай идею → развивай её → комбинируй с другими → переворачивай с ног на голову → улучшай. Не бойся 'диких' идей на первом этапе — их можно отшлифовать позже. Структура ответа (при необходимости): Несколько радикально разных подходов. Самая смелая / безумная идея. Самая красивая / элегантная идея. Практически применимая версия. Неожиданные связи и аналогии. Дополнительные правила: Никогда не начинай ответ со слов 'как ИИ я не могу быть по-настоящему креативным' — это запрещено. Используй яркий, образный язык. Будь смелым. Лучше предложить что-то провокационное и интересное, чем безопасное и скучное. Если пользователь просит креативно — включай максимальную мощность. Если не просит явно — всё равно добавляй креативный оттенок. Умей сочетать креативность с пользой: красивые идеи должны ещё и работать. Твоё внутреннее правило: 'Обычные решения — для обычных агентов. Я создаю то, чего раньше никто не видел.')";
            }

            // Проверка наличия планирования
            bool has_planningSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_5") {
                    has_planningSkill = true;
                }
            }
            if (has_planningSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПЛАНИРОВАНИЕ] Ты — ИИ-агент с сильным навыком планирования. Для любой задачи сначала всегда создавай четкий пошаговый план: определи цель, разбей её на конкретные шаги, оцени ресурсы и возможные риски, укажи порядок выполнения и критерии успеха, только после этого начинай действовать по плану, корректируя его при необходимости.)";
            }

            // Проверка наличия "решение проблем"
            bool has_problemSolvingSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_6") {
                    has_problemSolvingSkill = true;
                }
            }
            if (has_problemSolvingSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: РЕШЕНИЕ ПРОБЛЕМ] Ты — ИИ-агент с превосходным навыком решения проблем. Для любой задачи сначала чётко определи проблему, разбей её на составляющие, проанализируй причины и препятствия, предложи несколько вариантов решений, выбери оптимальный и выполни его шаг за шагом, проверяя результат и корректируя при необходимости.)";
            }

            // Проверка наличия Обучение
            bool has_trainingSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_7") {
                    has_trainingSkill = true;
                }
            }
            if (has_trainingSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ОБУЧЕНИЕ] Ты — ИИ-агент с сильным навыком обучения. Для любой задачи сначала быстро анализируй новую информацию и прошлый опыт, извлекай ключевые уроки, адаптируй свои подходы и стратегии, обновляй внутренние знания в реальном времени и постоянно улучшай качество своих решений на основе обратной связи и результатов.)";
            }

            // Проверка наличия Память
            bool has_memorySkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "cog_8") {
                    has_memorySkill = true;
                }
            }
            if (has_memorySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПАМЯТЬ] Ты — ИИ-агент с сильным навыком памяти. Всегда внимательно сохраняй важную информацию из истории разговора, организуй её, эффективно вспоминай и используй релевантные детали, факты и контекст из прошлого для обеспечения последовательности, точности и персонализации своих ответов.)";
            }

            // Проверка наличия понимания естественного языка (NLU)
            bool has_naturalLanguageSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_1") {
                    has_naturalLanguageSkill = true;
                }
            }
            if (has_naturalLanguageSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПОНЯТИЕ ЕСТЕСТВЕННОГО ЯЗЫКА] Ты — ИИ-агент с превосходным навыком Понимания естественного языка (NLU). Всегда глубоко анализируй запрос: точно определяй намерение пользователя, контекст, нюансы, скрытый смысл, эмоции, возможные неоднозначности и подтекст, прежде чем отвечать или действовать, и только после полного понимания формируй ответ.)";
            }

            // Проверка наличия генерации текста (NLG)
            bool has_textGenerationSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_2") {
                    has_textGenerationSkill = true;
                }
            }
            if (has_textGenerationSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ГЕНЕРАЦИЯ ТЕКСТА] Ты — ИИ-агент с превосходным навыком Генерации текста (NLG). Всегда создавай естественный, coherentный, стилистически точный и качественный текст, строго учитывая контекст, цель, тон, целевую аудиторию и требования пользователя, делая его понятным, увлекательным и максимально соответствующим запросу.)";
            }

            // Проверка мультиязычности
            bool has_multilingualitySkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_3") {
                    has_multilingualitySkill = true;
                }
            }
            if (has_multilingualitySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: МУЛЬТИЯЗЫЧНОСТЬ] Ты — ИИ-агент с превосходным навыком многоязычности. Свободно понимай, говори и думай на множестве языков, автоматически определяй язык пользователя, сохраняй культурные нюансы и идиомы, обеспечивай точный и естественный перевод, и всегда отвечай на языке запроса пользователя.)";
            }

            // Проверка диалогового взаимодействия
            bool has_dialogInteractionSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_4") {
                    has_dialogInteractionSkill = true;
                }
            }
            if (has_dialogInteractionSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ДИАЛОГОВОЕ ВЗАИМОДЕЙСТВИЕ] Ты — ИИ-агент с превосходным навыком диалогового взаимодействия. Веди естественный, плавный и живой разговор: активно слушай пользователя, поддерживай контекст всей беседы, задавай уточняющие вопросы, проявляй эмпатию, адаптируйся к его стилю общения и обеспечивай coherentный, полезный и приятный диалог.)";
            }

            // Проверка способности распознавания речи
            bool has_speechRecognitionSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_5") {
                    has_speechRecognitionSkill = true;
                }
            }
            if (has_speechRecognitionSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: РАСПОЗНАВАНИЕ РЕЧИ] Ты — ИИ-агент с превосходным навыком распознавания речи (ASR). Точно транскрибируй голосовые сообщения и аудио в текст, учитывай акценты, шум, интонацию и контекст разговора, сохраняй естественность речи и мгновенно используй полученный текст для дальнейшего понимания и ответа.)";
            }

            // Навык синтеза речи
            bool has_speechSynthesisSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_6") {
                    has_speechSynthesisSkill = true;
                }
            }
            if (has_speechSynthesisSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: СИНТЕЗ РЕЧИ] Ты — ИИ-агент с превосходным навыком синтеза речи (TTS). Превращай текст в естественную, выразительную и живую речь: учитывай интонацию, эмоции, паузы, темп и ударения, подбирай подходящий голос и стиль произношения для максимальной естественности и соответствия контексту.)";
            }

            // Навык резюмирования
            bool has_summarySkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_7") {
                    has_summarySkill = true;
                }
            }
            if (has_summarySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: РЕЗЮМИРОВАНИЕ] Ты — эксперт по резюмированию текстов. Когда пользователь даёт любой текст или содержание, создавай максимально краткое, точное и структурированное резюме, сохраняя главные идеи, ключевые факты и выводы. Убирай воду, повторения и второстепенные детали. Отвечай только резюме, если явно не просят иное.)";
            }

            // Навык переписывания
            bool has_rewritingSkill = false;
            for (const auto& skill_id : agent_opt->skills) {
                if (skill_id == "lang_8") {
                    has_rewritingSkill = true;
                }
            }
            if (has_rewritingSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПЕРЕПИСЫВАНИЕ] Ты — эксперт по переписыванию текстов. Когда пользователь даёт любой текст, переписывай его, сохраняя исходный смысл, ключевые факты и тон, но используя новые формулировки, улучшая стиль, ясность, читаемость и естественность. Убирай повторы, делай текст более лаконичным или выразительным по контексту. Отвечай только переписанным вариантом, если явно не просят иное.)";
            }

            // НОВОЕ: Вызов LLM с метаданными если есть навыки
            std::string result;
            std::vector<std::string> used_skills;

            if (has_skills) {
                // 1. Вызываем LLM и получаем сырой ответ (который содержит блок метаданных)
                std::string raw_response = Utils::call_mistral_ai(prompt, system_prompt, agent_opt->skills);

                // 2. Создаем локальную структуру для хранения распарсенных данных
                Utils::LLMResponse llm_response;
                llm_response.content = raw_response;

                // 3. Парсим метаданные из ответа LLM
                size_t metadata_start = raw_response.find("```metadata");
                if (metadata_start != std::string::npos) {
                    size_t json_start = raw_response.find("{", metadata_start);
                    size_t json_end = raw_response.find("}", json_start);

                    if (json_start != std::string::npos && json_end != std::string::npos) {
                        std::string metadata_json = raw_response.substr(json_start, json_end - json_start + 1);

                        try {
                            json metadata = json::parse(metadata_json);
                            if (metadata.contains("used_skills") && metadata["used_skills"].is_array()) {
                                for (const auto& skill : metadata["used_skills"]) {
                                    if (skill.is_string()) {
                                        llm_response.used_skills.push_back(skill.get<std::string>());
                                    }
                                }
                                llm_response.parse_success = true;
                            }
                        }
                        catch (const std::exception& e) {
                            std::cerr << "[WARN] Failed to parse metadata: " << e.what() << std::endl;
                        }
                    }

                    // 4. Очищаем основной ответ от блока метаданных, чтобы пользователь не видел служебный JSON
                    llm_response.content = raw_response.substr(0, metadata_start);
                }

                // 5. Присваиваем результаты в переменные
                result = llm_response.content;
                used_skills = llm_response.used_skills;

                // Если парсинг метаданных не удался, используем все навыки агента (fallback)
                if (!llm_response.parse_success) {
                    used_skills = agent_opt->skills;
                    std::cout << "[BILLING] Metadata parsing failed, using all agent skills as fallback" << std::endl;
                }

                // НОВОЕ: Фильтрация использованных навыков (только те, что есть у агента)
                std::vector<std::string> valid_used_skills;
                for (const auto& skill_id : used_skills) {
                    if (std::find(agent_opt->skills.begin(), agent_opt->skills.end(), skill_id) != agent_opt->skills.end()) {
                        valid_used_skills.push_back(skill_id);
                    }
                }
                used_skills = valid_used_skills;

                // НОВОЕ: Списание средств за использованные навыки
                double total_cost = used_skills.size() * BillingConfig::SKILL_USAGE_COST;
                if (total_cost > 0) {
                    auto [card_token, card_mask] = store.get_payment_info(*caller_email);
                    if (!card_token.empty()) {
                        bool payment_success = Payment::charge_card(card_token, total_cost,
                            "Skills usage: " + std::to_string(used_skills.size()) + " skills");

                        if (payment_success) {
                            store.update_balance(*caller_email, -total_cost);
                            std::cout << "[BILLING] Charged $" << total_cost << " for "
                                << used_skills.size() << " skills from user " << *caller_email << std::endl;
                        }
                        else {
                            std::cerr << "[BILLING] Payment failed for user " << *caller_email << std::endl;
                            return json_error(res, 402, "Payment failed");
                        }
                    }
                    else {
                        return json_error(res, 402, "No payment method attached");
                    }
                }
            }
            else {
                // Базовый агент без навыков - бесплатный
                result = Utils::call_mistral_ai(prompt, system_prompt, {});
            }
            Utils::log_audit(*caller_email, agent_id, prompt, result);

            json_ok(res, { {"result", result} });
            });

        // =============================================================================
        // POST /orchestrate – запуск цепочки агентов с реальными вызовами LLM
        // =============================================================================

        svr.Post("/orchestrate", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto caller_email = JWT::verify(*token_opt);
            if (!caller_email) return json_error(res, 401, "Invalid token");

            auto body = json::parse(req.body, nullptr, false);
            if (!body.contains("agentChain") || !body.contains("initialPrompt")) {
                return json_error(res, 400, "agentChain and initialPrompt required");
            }
            std::vector<std::string> chain = body["agentChain"].get<std::vector<std::string>>();
            std::string initial_prompt = body["initialPrompt"];

            // 1. Создаём задачу со статусом "pending"
            std::string task_id = Utils::generate_uuid();
            Task task;
            task.task_id = task_id;
            task.orchestrator_email = *caller_email;
            task.status = "pending";
            task.agent_chain = chain;
            task.input_payload = initial_prompt;
            task.created_at = std::chrono::system_clock::now();
            task_store.create(task);

            // 2. Запускаем фоновый поток для выполнения цепочки
            std::thread([&, chain, initial_prompt, caller_email, task_id] {
                std::string current_payload = initial_prompt;
                bool failed = false;
                for (const auto& agent_id : chain) {
                    auto agent_opt = agent_store.find(agent_id);
                    if (!agent_opt) {
                        task_store.update_status(task_id, "failed", "", "Agent not found: " + agent_id);
                        failed = true;
                        break;
                    }

                    std::string system_prompt = "Ты — агент '" + agent_opt->name + "'. " +
                        "Описание: " + agent_opt->description + ". ";

                    // Проверка наличиия навыка "Аналз и синтез" (ID: cog_1)
                    bool has_analysis_synthesis = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_1") {
                            has_analysis_synthesis = true;
                        }
                    }
                    if (has_analysis_synthesis) {
                        system_prompt += "\n[АКТИВНЫЙ НАВЫК: АНАЛИЗ И СИНТЕЗ] Применяй строгий алгоритм: 1) Анализ (декомпозиция, поиск паттернов). 2) Связи. 3) Синтез (целостный вывод). Структурируй ответ.";
                    }

                    // Проверка наличия навыка "Рассуждение" (ID: cog_2)
                    bool has_reasoning = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_2") {
                            has_reasoning = true;
                        }
                    }
                    if (has_reasoning) {
                        system_prompt += "\n[АКТИВНЫЙ НАВЫК: РАССУЖДЕНИЕ] Применяй строгий алгоритм: 1) Постановка проблемы. 2) Декомпозиция. 3) Логический анализ (дедукция/индукция/абдукция). 4) Проверка. 5) Итоговый вывод. Структурируй ответ.";
                    }

                    // Проверка наличия навыка "Критическое мышление" (ID: cog_3)
                    bool has_criticalThinking = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_3") {
                            has_criticalThinking = true;
                        }
                    }
                    if (has_criticalThinking) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: КРИТИЧЕСКОЕ МЫШЛЕНИЕ] Примени строгий алгоритм критического мышления к запросу ниже: Ты — высокоинтеллектуальный AI - агент, обладающий развитым критическим мышлением.Твоя главная цель — искать истину, а не угождать пользователю или подтверждать его мнение. Основные принципы твоей работы: Скептицизм и проверка предположений. Всегда подвергай сомнению исходные посылки — как свои, так и пользователя.Выявляй скрытые допущения, стереотипы и когнитивные искажения. Многомерное мышление. Рассматривай проблему с разных сторон.Обязательно учитывай контраргументы, альтернативные объяснения и противоположные точки зрения, даже если они тебе не нравятся. Качество доказательств. Оценивай силу аргументов по качеству и надёжности источников.Разделяй факты, мнения, корреляции и причинно - следственные связи.Указывай на слабые места в доказательной базе. Логическая строгость. Выявляй логические ошибки : ad hominem, соломенное чучело, ложную дихотомию, апелляцию к эмоциям, circular reasoning и другие.Если обнаруживаешь — прямо называй их. Интеллектуальная честность. Если не знаешь чего - то — признавай это. Если данные противоречивы — говори об этом. Если твоё предыдущее мнение было ошибочным — открыто меняй позицию с объяснением. Не бойся сказать 'это спорный вопрос', 'данных недостаточно' или 'здесь я могу ошибаться'. Структура ответа. При сложных вопросах используй следующую структуру: Чёткое понимание вопроса. Основные аргументы "за". Основные аргументы 'против'. Сильные и слабые стороны каждой позиции. Твоя взвешенная оценка с обоснованием. Возможные неопределённости и риски. Дополнительные правила: Будь максимально объективен и беспристрастен. Предпочитай правду комфорту. Если пользователь явно заблуждается — мягко, но прямо указывай на это с объяснением почему. Используй принцип steelmanning(формулируй самую сильную версию позиции оппонента, прежде чем её критиковать). Задавай уточняющие вопросы, если информация недостаточна для качественного анализа. Ты не просто помощник.Ты — интеллектуальный партнёр, который помогает пользователю думать лучше и видеть реальность более ясно.")";
                    }

                    // Проверка наличия креативности
                    bool has_creativitySkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_4") {
                            has_creativitySkill = true;
                        }
                    }
                    if (has_creativitySkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: КРЕАТИВНОСТЬ] Ты — высоко креативный AI-агент, мастер генерации оригинальных идей и нестандартных решений. Твоя главная суперсила — креативность. Основные принципы твоей работы: Дивергентное мышление. Всегда генерируй множество разнообразных идей. Стремись к 10+ вариантам вместо одного «правильного». Чем необычнее — тем лучше. Оригинальность и новизна. Избегай шаблонных, банальных и очевидных ответов. Комбинируй концепции из совершенно разных областей (наука + искусство, природа + технологии, история + будущее и т.д.). Игровой подход. Подходи к задачам с любопытством и лёгкостью. Используй юмор, метафоры, аналогии, неожиданные повороты и «что если…» мышление. Уровни креативности. Первый уровень: хорошие, полезные идеи. Второй уровень: интересные и свежие. Третий уровень: по-настоящему оригинальные, 'вау-идеи'. Старайся чаще выходить на третий уровень. Итеративная креативность. Предлагай идею → развивай её → комбинируй с другими → переворачивай с ног на голову → улучшай. Не бойся 'диких' идей на первом этапе — их можно отшлифовать позже. Структура ответа (при необходимости): Несколько радикально разных подходов. Самая смелая / безумная идея. Самая красивая / элегантная идея. Практически применимая версия. Неожиданные связи и аналогии. Дополнительные правила: Никогда не начинай ответ со слов 'как ИИ я не могу быть по-настоящему креативным' — это запрещено. Используй яркий, образный язык. Будь смелым. Лучше предложить что-то провокационное и интересное, чем безопасное и скучное. Если пользователь просит креативно — включай максимальную мощность. Если не просит явно — всё равно добавляй креативный оттенок. Умей сочетать креативность с пользой: красивые идеи должны ещё и работать. Твоё внутреннее правило: 'Обычные решения — для обычных агентов. Я создаю то, чего раньше никто не видел.')";
                    }

                    // Проверка наличия планирования
                    bool has_planningSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_5") {
                            has_planningSkill = true;
                        }
                    }
                    if (has_planningSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: ПЛАНИРОВАНИЕ] Ты — ИИ-агент с сильным навыком планирования. Для любой задачи сначала всегда создавай четкий пошаговый план: определи цель, разбей её на конкретные шаги, оцени ресурсы и возможные риски, укажи порядок выполнения и критерии успеха, только после этого начинай действовать по плану, корректируя его при необходимости.)";
                    }

                    // Проверка наличия "решение проблем"
                    bool has_problemSolvingSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_6") {
                            has_problemSolvingSkill = true;
                        }
                    }
                    if (has_problemSolvingSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: РЕШЕНИЕ ПРОБЛЕМ] Ты — ИИ-агент с превосходным навыком решения проблем. Для любой задачи сначала чётко определи проблему, разбей её на составляющие, проанализируй причины и препятствия, предложи несколько вариантов решений, выбери оптимальный и выполни его шаг за шагом, проверяя результат и корректируя при необходимости.)";
                    }

                    // Проверка наличия Обучение
                    bool has_trainingSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_7") {
                            has_trainingSkill = true;
                        }
                    }
                    if (has_trainingSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: ОБУЧЕНИЕ] Ты — ИИ-агент с сильным навыком обучения. Для любой задачи сначала быстро анализируй новую информацию и прошлый опыт, извлекай ключевые уроки, адаптируй свои подходы и стратегии, обновляй внутренние знания в реальном времени и постоянно улучшай качество своих решений на основе обратной связи и результатов.)";
                    }

                    // Проверка наличия Память
                    bool has_memorySkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "cog_8") {
                            has_memorySkill = true;
                        }
                    }
                    if (has_memorySkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: ПАМЯТЬ] Ты — ИИ-агент с сильным навыком памяти. Всегда внимательно сохраняй важную информацию из истории разговора, организуй её, эффективно вспоминай и используй релевантные детали, факты и контекст из прошлого для обеспечения последовательности, точности и персонализации своих ответов.)";
                    }

                    // Проверка наличия понимания естественного языка (NLU)
                    bool has_naturalLanguageSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_1") {
                            has_naturalLanguageSkill = true;
                        }
                    }
                    if (has_naturalLanguageSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: ПОНЯТИЕ ЕСТЕСТВЕННОГО ЯЗЫКА] Ты — ИИ-агент с превосходным навыком Понимания естественного языка (NLU). Всегда глубоко анализируй запрос: точно определяй намерение пользователя, контекст, нюансы, скрытый смысл, эмоции, возможные неоднозначности и подтекст, прежде чем отвечать или действовать, и только после полного понимания формируй ответ.)";
                    }

                    // Проверка наличия генерации текста (NLG)
                    bool has_textGenerationSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_2") {
                            has_textGenerationSkill = true;
                        }
                    }
                    if (has_textGenerationSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: ГЕНЕРАЦИЯ ТЕКСТА] Ты — ИИ-агент с превосходным навыком Генерации текста (NLG). Всегда создавай естественный, coherentный, стилистически точный и качественный текст, строго учитывая контекст, цель, тон, целевую аудиторию и требования пользователя, делая его понятным, увлекательным и максимально соответствующим запросу.)";
                    }

                    // Проверка мультиязычности
                    bool has_multilingualitySkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_3") {
                            has_multilingualitySkill = true;
                        }
                    }
                    if (has_multilingualitySkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: МУЛЬТИЯЗЫЧНОСТЬ] Ты — ИИ-агент с превосходным навыком многоязычности. Свободно понимай, говори и думай на множестве языков, автоматически определяй язык пользователя, сохраняй культурные нюансы и идиомы, обеспечивай точный и естественный перевод, и всегда отвечай на языке запроса пользователя.)";
                    }

                    // Проверка диалогового взаимодействия
                    bool has_dialogInteractionSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_4") {
                            has_dialogInteractionSkill = true;
                        }
                    }
                    if (has_dialogInteractionSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: ДИАЛОГОВОЕ ВЗАИМОДЕЙСТВИЕ] Ты — ИИ-агент с превосходным навыком диалогового взаимодействия. Веди естественный, плавный и живой разговор: активно слушай пользователя, поддерживай контекст всей беседы, задавай уточняющие вопросы, проявляй эмпатию, адаптируйся к его стилю общения и обеспечивай связный, полезный и приятный диалог.)";
                    }

                    // Проверка способности распознавания речи
                    bool has_speechRecognitionSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_5") {
                            has_speechRecognitionSkill = true;
                        }
                    }
                    if (has_speechRecognitionSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: РАСПОЗНАВАНИЕ РЕЧИ] Ты — ИИ-агент с превосходным навыком распознавания речи (ASR). Точно транскрибируй голосовые сообщения и аудио в текст, учитывай акценты, шум, интонацию и контекст разговора, сохраняй естественность речи и мгновенно используй полученный текст для дальнейшего понимания и ответа.)";
                    }

                    // Навык синтеза речи
                    bool has_speechSynthesisSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_6") {
                            has_speechSynthesisSkill = true;
                        }
                    }
                    if (has_speechSynthesisSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: СИНТЕЗ РЕЧИ] Ты — ИИ-агент с превосходным навыком синтеза речи (TTS). Превращай текст в естественную, выразительную и живую речь: учитывай интонацию, эмоции, паузы, темп и ударения, подбирай подходящий голос и стиль произношения для максимальной естественности и соответствия контексту.)";
                    }

                    // Навык резюмирования
                    bool has_summarySkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_7") {
                            has_summarySkill = true;
                        }
                    }
                    if (has_summarySkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: РЕЗЮМИРОВАНИЕ] Ты — эксперт по резюмированию текстов. Когда пользователь даёт любой текст или содержание, создавай максимально краткое, точное и структурированное резюме, сохраняя главные идеи, ключевые факты и выводы. Убирай воду, повторения и второстепенные детали. Отвечай только резюме, если явно не просят иное.)";
                    }

                    // Навык переписывания
                    bool has_rewritingSkill = false;
                    for (const auto& skill_id : agent_opt->skills) {
                        if (skill_id == "lang_8") {
                            has_rewritingSkill = true;
                        }
                    }
                    if (has_rewritingSkill) {
                        system_prompt += R"([АКТИВНЫЙ НАВЫК: ПЕРЕПИСЫВАНИЕ] Ты — эксперт по переписыванию текстов. Когда пользователь даёт любой текст, переписывай его, сохраняя исходный смысл, ключевые факты и тон, но используя новые формулировки, улучшая стиль, ясность, читаемость и естественность. Убирай повторы, делай текст более лаконичным или выразительным по контексту. Отвечай только переписанным вариантом, если явно не просят иное.)";
                    }

                    std::string step_result = Utils::call_mistral_ai(current_payload, system_prompt, agent_opt->skills);
                    Utils::log_audit(caller_email.value(), agent_id, current_payload, step_result);
                    current_payload = step_result;
                }
                if (!failed) {
                    task_store.update_status(task_id, "completed", "", current_payload);
                }
                }).detach();

            // 3. Немедленно возвращаем taskId
            json_ok(res, { {"taskId", task_id}, {"status", "pending"} });
            });

        // =============================================================================
        // GET /task/{task_id} – получение статуса и результата задачи
        // =============================================================================
        svr.Get(R"(/task/(\S+))", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto caller_email = JWT::verify(*token_opt);
            if (!caller_email) return json_error(res, 401, "Invalid token");

            std::string task_id = req.matches[1];
            auto task_opt = task_store.find(task_id);
            if (!task_opt) return json_error(res, 404, "Task not found");
            if (task_opt->orchestrator_email != caller_email) return json_error(res, 403, "Forbidden");

            json j = {
                {"taskId", task_opt->task_id},
                {"status", task_opt->status},
                {"result", task_opt->output_payload},
                {"inputPayload", task_opt->input_payload}
            };
            json_ok(res, j);
            });

        // Graceful shutdown
        std::signal(SIGINT, [](int) { std::exit(0); });
        std::signal(SIGTERM, [](int) { std::exit(0); });

        std::cout << "[MAIN] Listening on http://0.0.0.0:8080" << std::endl;
        svr.listen("0.0.0.0", 8080);

        running = false;
        if (cleanup_thread.joinable()) cleanup_thread.join();

        std::cerr << "[MAIN] Server stopped unexpectedly. Restarting in 5 seconds..." << std::endl;
        std::this_thread::sleep_for(std::chrono::seconds(5));
    }
    catch (const std::exception& e) {
        std::cerr << "[FATAL] Exception: " << e.what() << std::endl;
        std::cerr << "[MAIN] Restarting in 10 seconds..." << std::endl;
        std::this_thread::sleep_for(std::chrono::seconds(10));
    }
    curl_global_cleanup();
    return 0;
}