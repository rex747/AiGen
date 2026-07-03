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
#include <filesystem>

// ============================================================================
// КРОССПЛАТФОРМЕННАЯ БЛОКИРОВКА ФАЙЛОВ (OS-LEVEL FILE LOCKING)
// ============================================================================
#ifdef _WIN32
#include <windows.h>
#include <io.h>
#else
#include <sys/file.h>
#include <unistd.h>
#include <fcntl.h>
#endif

struct FileLock {
#ifdef _WIN32
    HANDLE hFile;
    bool locked;
    FileLock(const std::string& path) : hFile(INVALID_HANDLE_VALUE), locked(false) {
        hFile = CreateFileA(path.c_str(), GENERIC_READ | GENERIC_WRITE,
            0, NULL, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        if (hFile != INVALID_HANDLE_VALUE) {
            OVERLAPPED overlapped = { 0 };
            if (LockFileEx(hFile, LOCKFILE_EXCLUSIVE_LOCK, 0, MAXDWORD, MAXDWORD, &overlapped)) {
                locked = true;
            }
        }
    }
    ~FileLock() {
        if (locked) {
            OVERLAPPED overlapped = { 0 };
            UnlockFileEx(hFile, 0, MAXDWORD, MAXDWORD, &overlapped);
        }
        if (hFile != INVALID_HANDLE_VALUE) {
            CloseHandle(hFile);
        }
    }
    bool is_locked() const { return locked; }
#else
    int fd;
    bool locked;
    FileLock(const std::string& path) : fd(-1), locked(false) {
        fd = open(path.c_str(), O_RDWR | O_CREAT, 0666);
        if (fd != -1) {
            if (flock(fd, LOCK_EX) == 0) {
                locked = true;
            }
            else {
                close(fd);
                fd = -1;
            }
        }
    }
    ~FileLock() {
        if (locked) {
            flock(fd, LOCK_UN);
        }
        if (fd != -1) {
            close(fd);
        }
    }
    bool is_locked() const { return locked; }
#endif
};

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
    constexpr const char* MISTRAL_API_KEY = "VOghCLeeLV6V8V9JbVPeGcyt5EbLYnEN";
    constexpr const char* MISTRAL_MODEL = "mistral-medium-latest";
    constexpr const char* MISTRAL_ENDPOINT = "https://api.mistral.ai/v1/chat/completions";
    constexpr double TEMPERATURE = 0.7;
    constexpr int MAX_TOKENS = 500;
}

// ============================================================================
// Конфигурация платежей
// ============================================================================
namespace BillingConfig {
    constexpr double SKILL_USAGE_COST = 0.10;
    constexpr const char* PAYMENT_GATEWAY_URL = "https://api.example.com/charge";
}

// =============================================================================
// МОДЕЛИ ДЛЯ АГЕНТОВ И ЗАДАЧ
// =============================================================================
struct Agent {
    std::string agent_id;
    std::string owner_email;
    std::string name;
    std::string description;
    std::vector<std::string> skills;
    std::string endpoint;
    bool is_public = true;
};

struct Task {
    std::string task_id;
    std::string orchestrator_email;
    std::string status;
    std::vector<std::string> agent_chain;
    std::string current_agent;
    std::string input_payload;
    std::string output_payload;
    std::chrono::system_clock::time_point created_at;
};

// Структура для хранения данных онбординга
struct OnboardingData {
    std::string ai_purpose;
    std::string industry;
    std::vector<std::string> required_skills;
    int current_task_duration_hours = 0;
    int estimated_ai_duration_hours = 0;
    std::string usage_frequency;
    std::string usage_time_of_day;
    long long completed_at = 0;
    bool demo_selected = false;
    std::string plan_selected;
};

// Структура для статуса онбординга
struct OnboardingStatus {
    bool onboarding_completed = false;
    bool demo_active = false;
    long long demo_expires_at = 0;
    bool subscription_active = false;
};

// =============================================================================
// ХРАНИЛИЩЕ АГЕНТОВ И ЗАДАЧ (in-memory + JSON-файлы)
// =============================================================================
class AgentStore {
    mutable std::shared_mutex mtx_;
    std::map<std::string, Agent> agents_;
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
    explicit AgentStore(std::string path) : file_path_(std::move(path)) { load(); }

    bool create(const Agent& agent) {
        std::unique_lock<std::shared_mutex> lock(mtx_);
        if (agents_.find(agent.agent_id) != agents_.end()) return false;
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
            if (!only_public || a.is_public) result.push_back(a);
        }
        return result;
    }

    std::vector<Agent> list_by_owner(const std::string& owner_email) const {
        std::shared_lock<std::shared_mutex> lock(mtx_);
        std::vector<Agent> result;
        for (const auto& [_, a] : agents_) {
            if (a.owner_email == owner_email) result.push_back(a);
        }
        return result;
    }

    bool remove(const std::string& agent_id) {
        std::unique_lock lock(mtx_);
        auto it = agents_.find(agent_id);
        if (it == agents_.end()) return false;
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
    std::map<std::string, Task> tasks_;
    const std::string file_path_;

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
#ifdef WIN32
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
    explicit TaskStore(std::string path) : file_path_(std::move(path)) { load(); }

    bool create(const Task& task) {
        std::unique_lock<std::shared_mutex> lock(mtx_);
        if (tasks_.find(task.task_id) != tasks_.end()) return false;
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
            if (t.orchestrator_email == email) result.push_back(t);
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
    constexpr int PBKDF2_ITERATIONS = 600000;
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
    constexpr const char* SMTP_PASSWORD = "hepezmykqavgljua";
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
    std::string call_mistral_ai(const std::string& prompt, const std::string& system_prompt);

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

    struct LLMResponse {
        std::string content;
        std::vector<std::string> used_skills;
        bool parse_success = false;
    };

    std::string call_mistral_ai(const std::string& prompt, const std::string& system_prompt) {
        CURL* curl = curl_easy_init();
        if (!curl) return "Ошибка: не удалось инициализировать CURL";

        json messages = json::array();
        if (!system_prompt.empty()) {
            messages.push_back({ {"role", "system"}, {"content", system_prompt} });
        }
        messages.push_back({ {"role", "user"}, {"content", prompt} });

        json request_body = {
            {"model", AiConfig::MISTRAL_MODEL},
            {"messages", messages},
            {"temperature", AiConfig::TEMPERATURE},
            {"max_tokens", AiConfig::MAX_TOKENS}
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

    std::string generate_salt() {
        std::string salt(Config::SALT_BYTES, '\0');
        if (RAND_bytes(reinterpret_cast<unsigned char*>(&salt[0]), static_cast<int>(Config::SALT_BYTES)) != 1) {
            throw std::runtime_error("CSPRNG failed: unable to generate salt");
        }
        return Utils::bytes_to_hex(salt);
    }

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

        if (!result) throw std::runtime_error("PBKDF2 hashing failed");
        return Utils::bytes_to_hex(hash);
    }

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
                .leeway(60)
                .verify(decoded);
            // проверка expiration вручную на всякий случай
            if (decoded.get_expires_at() < std::chrono::system_clock::now()) {
                return std::nullopt;
            }
            return decoded.get_payload_claim("email").as_string();
        }
        catch (const std::exception& e) {
            std::cerr << "[JWT] Verify failed: " << e.what() << std::endl;
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

        if (it->second.count >= max_requests) return false;

        it->second.count++;
        return true;
    }

    void cleanup() {
        std::lock_guard<std::mutex> lock(mtx_);
        auto now = std::chrono::steady_clock::now();
        for (auto it = entries_.begin(); it != entries_.end();) {
            if (now > it->second.reset_time) it = entries_.erase(it);
            else ++it;
        }
    }
};

// =============================================================================
// ПОТОКОБЕЗОПАСНОЕ ХРАНИЛИЩЕ ПОЛЬЗОВАТЕЛЕЙ (БЕЗ КЭШИРОВАНИЯ В RAM)
// =============================================================================
class UserStore {

private:
    std::string db_path;
    std::mutex mtx;

    // Добавить поля для онбординга
    std::map<std::string, OnboardingData> onboarding_data;
    std::map<std::string, OnboardingStatus> onboarding_status;

    mutable std::mutex persist_mtx_;
    const std::string file_path_;

    struct UserData {
        std::string salt;
        std::string hash;
        std::string card_token;
        std::string card_mask;
        double balance = 0.0;
        uint64_t version = 0;
        std::string subscription_type;  // "monthly", "yearly", ""
        uint64_t subscription_end_date = 0;  // timestamp в миллисекундах
    };

    UserData parse_user(const json& data) const {
        UserData ud;
        ud.salt = data.value("salt", "");
        ud.hash = data.value("hash", "");
        ud.card_token = data.value("card_token", "");
        ud.card_mask = data.value("card_mask", "");
        ud.balance = data.value("balance", 0.0);
        ud.version = data.value("version", 0);
        ud.subscription_type = data.value("subscription_type", "");
        ud.subscription_end_date = data.value("subscription_end_date", 0);
        return ud;
    }

    json user_to_json(const UserData& ud) const {
        return {
            {"salt", ud.salt},
            {"hash", ud.hash},
            {"card_token", ud.card_token},
            {"card_mask", ud.card_mask},
            {"balance", ud.balance},
            {"version", ud.version},
            {"subscription_type", ud.subscription_type},
			{"subscription_end_date", ud.subscription_end_date}
        };
    }

    json read_users_json() const {
        std::ifstream file(file_path_);
        if (!file.is_open()) return json::object();
        try {
            json j;
            file >> j;
            return j;
        }
        catch (const std::exception& e) {
            std::cerr << "[WARN] Failed to parse users database: " << e.what() << std::endl;
            return json::object();
        }
    }

    bool write_users_json(const json& j) const {
        std::string temp_path = file_path_ + ".tmp";
        std::ofstream file(temp_path, std::ios::trunc);
        if (!file.is_open()) {
            std::cerr << "[ERROR] Cannot open temp file for writing: " << temp_path << std::endl;
            return false;
        }

        file << j.dump(2);
        file.flush();
        if (file.fail()) {
            std::cerr << "[ERROR] Failed to flush temp file: " << temp_path << std::endl;
            file.close();
            return false;
        }
        file.close();

        try {
            std::filesystem::rename(temp_path, file_path_);
        }
        catch (const std::exception& e) {
            std::cerr << "[ERROR] Failed to rename temp file: " << e.what() << std::endl;
            return false;
        }
        return true;
    }

public:
    explicit UserStore(std::string path) : file_path_(std::move(path)) {}

    bool exists(const std::string& email) const {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return false;
        json j = read_users_json();
        return j.contains(email);
    }

    bool create(const std::string& email, const std::string& password) {
        // Тяжелые криптографические операции выполняем ДО блокировок
        std::string salt = Crypto::generate_salt();
        std::string hash = Crypto::hash_password(password, salt);

        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return false;

        json j = read_users_json();
        if (j.contains(email)) return false;

        UserData ud;
        ud.salt = salt;
        ud.hash = hash;

        j[email] = user_to_json(ud);
        return write_users_json(j);
    }

    bool validate(const std::string& email, const std::string& password) const {
        std::string salt;
        std::string stored_hash;

        {
            std::lock_guard<std::mutex> lock(persist_mtx_);
            FileLock flock(file_path_);
            if (!flock.is_locked()) return false;

            json j = read_users_json();
            if (!j.contains(email)) return false;

            UserData ud = parse_user(j[email]);
            salt = ud.salt;
            stored_hash = ud.hash;
        } // Блокировки снимаются здесь

        // Хэширование без удержания файловых锁
        std::string computed = Crypto::hash_password(password, salt);
        return Crypto::secure_compare(computed, stored_hash);
    }

    std::optional<UserData> get_user(const std::string& email) const {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return std::nullopt;

        json j = read_users_json();
        if (!j.contains(email)) return std::nullopt;
        return parse_user(j[email]);
    }

    bool update_profile(const std::string& email, const std::string& new_password,
        const std::string& new_card_token, const std::string& new_card_mask) {
        std::string new_salt;
        std::string new_hash;
        bool password_changed = false;

        if (!new_password.empty()) {
            new_salt = Crypto::generate_salt();
            new_hash = Crypto::hash_password(new_password, new_salt);
            password_changed = true;
        }

        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return false;

        json j = read_users_json();
        if (!j.contains(email)) return false;

        UserData ud = parse_user(j[email]);
        if (password_changed) {
            ud.salt = new_salt;
            ud.hash = new_hash;
        }
        if (!new_card_token.empty()) {
            ud.card_token = new_card_token;
            ud.card_mask = new_card_mask;
        }

        j[email] = user_to_json(ud);
        return write_users_json(j);
    }

    double get_balance(const std::string& email) const {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return 0.0;

        json j = read_users_json();
        if (!j.contains(email)) return 0.0;
        return j[email].value("balance", 0.0);
    }

    bool update_balance(const std::string& email, double amount) {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return false;

        json j = read_users_json();
        if (!j.contains(email)) return false;

        UserData ud = parse_user(j[email]);
        double old_balance = ud.balance;
        uint64_t old_version = ud.version;

        double new_balance = old_balance + amount;
        if (new_balance < 0) new_balance = 0;
        uint64_t new_version = old_version + 1;

        ud.balance = new_balance;
        ud.version = new_version;

        j[email] = user_to_json(ud);
        bool ok = write_users_json(j);

        if (ok) {
            std::cout << "[BALANCE_UPDATE] email=" << email
                << " old=" << old_balance << " new=" << new_balance
                << " delta=" << amount << " version=" << new_version << std::endl;
        }
        return ok;
    }

    bool has_sufficient_balance(const std::string& email, double required_amount) const {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return false;

        json j = read_users_json();
        if (!j.contains(email)) return false;
        return j[email].value("balance", 0.0) >= required_amount;
    }

    std::pair<std::string, std::string> get_payment_info(const std::string& email) const {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return { "", "" };

        json j = read_users_json();
        if (!j.contains(email)) return { "", "" };
        return { j[email].value("card_token", ""), j[email].value("card_mask", "") };
    }

    std::string getSubscriptionType(const std::string& email) const {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        auto user = get_user(email);
        return user ? user->subscription_type : "";
    }

    uint64_t getSubscriptionEndDate(const std::string& email) const {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        auto user = get_user(email);
        return user ? user->subscription_end_date : 0;
    }

    bool remove_user(const std::string& email) {
        std::lock_guard<std::mutex> lock(persist_mtx_);
        FileLock flock(file_path_);
        if (!flock.is_locked()) return false;

        json j = read_users_json();
        if (!j.contains(email)) return false;

        j.erase(email);
        return write_users_json(j);
    }
    // Сохранение данных онбординга
    bool saveOnboardingData(const std::string& email, const OnboardingData& data) {
        std::lock_guard<std::mutex> lock(mtx);
        try {
            onboarding_data[email] = data;

            // Обновляем статус онбординга
            onboarding_status[email].onboarding_completed = true;
            onboarding_status[email].demo_active = data.demo_selected;

            if (data.demo_selected) {
                // Демо-версия на 3 дня
                auto now = std::chrono::system_clock::now();
                auto demo_expires = now + std::chrono::hours(72); // 3 дня
                onboarding_status[email].demo_expires_at =
                    std::chrono::duration_cast<std::chrono::seconds>(
                        demo_expires.time_since_epoch()
                    ).count();
            }

            // Сохраняем в файл
            saveToDisk();
            return true;
        }
        catch (const std::exception& e) {
            std::cerr << "[ERROR] Failed to save onboarding data: " << e.what() << std::endl;
            return false;
        }
    }

    // Получение статуса онбординга
    OnboardingStatus getOnboardingStatus(const std::string& email) {
        std::lock_guard<std::mutex> lock(mtx);
        if (onboarding_status.find(email) != onboarding_status.end()) {
            auto status = onboarding_status[email];

            // Проверяем, не истекла ли демо-версия
            if (status.demo_active) {
                auto now = std::chrono::system_clock::now();
                auto now_ts = std::chrono::duration_cast<std::chrono::seconds>(
                    now.time_since_epoch()
                ).count();

                if (now_ts > status.demo_expires_at) {
                    status.demo_active = false;
                }
            }

            return status;
        }
        return OnboardingStatus{};
    }

    // Активация демо-версии
    bool activateDemo(const std::string& email) {
        std::lock_guard<std::mutex> lock(mtx);
        try {
            onboarding_status[email].demo_active = true;

            auto now = std::chrono::system_clock::now();
            auto demo_expires = now + std::chrono::hours(72); // 3 дня
            onboarding_status[email].demo_expires_at =
                std::chrono::duration_cast<std::chrono::seconds>(
                    demo_expires.time_since_epoch()
                ).count();

            saveToDisk();
            return true;
        }
        catch (const std::exception& e) {
            std::cerr << "[ERROR] Failed to activate demo: " << e.what() << std::endl;
            return false;
        }
    }
    
    // Реализация активации подписки:
    bool activateSubscription(const std::string& email, const std::string& plan_type) {
        std::lock_guard<std::mutex> lock(mtx);
        auto j = read_users_json();
        if (!j.contains(email)) return false;

        auto ud = parse_user(j[email]);
        ud.subscription_type = plan_type;

        // Вычисляем дату окончания подписки
        auto now = std::chrono::system_clock::now();
        auto duration = (plan_type == "yearly")
            ? std::chrono::hours(24 * 365)   // 1 год
            : std::chrono::hours(24 * 30);   // 1 месяц

        auto end_time = now + duration;
        ud.subscription_end_date = std::chrono::duration_cast<std::chrono::milliseconds>(
            end_time.time_since_epoch()
        ).count();

        j[email] = user_to_json(ud);
        return write_users_json(j);
    }

private:
    void saveToDisk() {
        // Сохранение в JSON файл
        nlohmann::json j;

        // Сохраняем данные онбординга
        nlohmann::json onboarding_j;
        for (const auto& [email, data] : onboarding_data) {
            onboarding_j[email] = {
                {"ai_purpose", data.ai_purpose},
                {"industry", data.industry},
                {"required_skills", data.required_skills},
                {"current_task_duration_hours", data.current_task_duration_hours},
                {"estimated_ai_duration_hours", data.estimated_ai_duration_hours},
                {"usage_frequency", data.usage_frequency},
                {"usage_time_of_day", data.usage_time_of_day},
                {"completed_at", data.completed_at},
                {"demo_selected", data.demo_selected},
                {"plan_selected", data.plan_selected}
            };
        }
        j["onboarding_data"] = onboarding_j;

        // Сохраняем статусы онбординга
        nlohmann::json status_j;
        for (const auto& [email, status] : onboarding_status) {
            status_j[email] = {
                {"onboarding_completed", status.onboarding_completed},
                {"demo_active", status.demo_active},
                {"demo_expires_at", status.demo_expires_at},
                {"subscription_active", status.subscription_active}
            };
        }
        j["onboarding_status"] = status_j;

        std::ofstream file("onboarding.json");
        file << j.dump(4);
    }

    void loadFromDisk() {
        // Загрузка из JSON файла
        std::ifstream file("onboarding.json");
        if (file.is_open()) {
            nlohmann::json j;
            file >> j;

            if (j.contains("onboarding_data")) {
                for (auto& [email, data] : j["onboarding_data"].items()) {
                    OnboardingData od;
                    od.ai_purpose = data.value("ai_purpose", "");
                    od.industry = data.value("industry", "");
                    od.required_skills = data.value("required_skills", std::vector<std::string>{});
                    od.current_task_duration_hours = data.value("current_task_duration_hours", 0);
                    od.estimated_ai_duration_hours = data.value("estimated_ai_duration_hours", 0);
                    od.usage_frequency = data.value("usage_frequency", "");
                    od.usage_time_of_day = data.value("usage_time_of_day", "");
                    od.completed_at = data.value("completed_at", 0LL);
                    od.demo_selected = data.value("demo_selected", false);
                    od.plan_selected = data.value("plan_selected", "");
                    onboarding_data[email] = od;
                }
            }

            if (j.contains("onboarding_status")) {
                for (auto& [email, status] : j["onboarding_status"].items()) {
                    OnboardingStatus os;
                    os.onboarding_completed = status.value("onboarding_completed", false);
                    os.demo_active = status.value("demo_active", false);
                    os.demo_expires_at = status.value("demo_expires_at", 0LL);
                    os.subscription_active = status.value("subscription_active", false);
                    onboarding_status[email] = os;
                }
            }
        }
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
    curl_easy_setopt(curl, CURLOPT_VERBOSE, 1L);

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
    bool charge_card(const std::string& card_token, double amount, const std::string& description) {
        std::cout << "[PAYMENT] Charging card token: " << card_token.substr(0, 10) << "..." << std::endl;
        std::cout << "[PAYMENT] Amount: $" << amount << std::endl;
        std::cout << "[PAYMENT] Description: " << description << std::endl;
        return true;
    }
}

// =============================================================================
// MAIN
// =============================================================================
int main()
{
    // =====================================================================
    // ВЫЧИСЛЕНИЕ АБСОЛЮТНЫХ ПУТЕЙ (РЕШАЕТ ПРОБЛЕМУ РАЗНЫХ ФАЙЛОВ)
    // =====================================================================
    std::filesystem::path base_dir;
    try {
        base_dir = std::filesystem::canonical("/proc/self/exe").parent_path();
    }
    catch (const std::exception& e) {
        std::cerr << "[FATAL] Cannot resolve executable path: " << e.what() << std::endl;
        return 1;
    }

    std::string users_db = (base_dir / "users.json").string();
    std::string agents_db = (base_dir / "agents.json").string();
    std::string tasks_db = (base_dir / "tasks.json").string();

    std::cout << "========================================================\n";
    std::cout << "[MAIN] ABSOLUTE PATHS FORCED:\n";
    std::cout << "[MAIN] Base Dir : " << base_dir << "\n";
    std::cout << "[MAIN] Users DB : " << users_db << "\n";
    std::cout << "[MAIN] Agents DB: " << agents_db << "\n";
    std::cout << "[MAIN] Tasks DB : " << tasks_db << "\n";
    std::cout << "========================================================\n";
    // =====================================================================

    curl_global_init(CURL_GLOBAL_ALL);
    std::cout << "[MAIN] Starting SecureAuthServer (Direct Disk I/O Mode)..." << std::endl;

    try {
        // Передаем абсолютные пути в хранилища
        UserStore store(users_db);
        AgentStore agent_store(agents_db);
        TaskStore task_store(tasks_db);

        std::cout << "[INFO] Stores initialized with absolute paths." << std::endl;

        RateLimiter login_limiter, register_limiter;
        std::atomic<bool> running{ true };

        std::thread cleanup_thread([&]() {
            while (running) {
                std::this_thread::sleep_for(std::chrono::minutes(1));
                login_limiter.cleanup();
                register_limiter.cleanup();
            }
        });
        
        httplib::Server svr;
        svr.set_base_dir("./public");
        svr.set_logger([](const auto& req, const auto& res) {
            auto now = std::chrono::system_clock::now();
            std::time_t t = std::chrono::system_clock::to_time_t(now);
            std::tm tm_buf;
#ifdef _WIN32
            localtime_s(&tm_buf, &t);
#else
            localtime_r(&t, &tm_buf);
#endif
            std::cout << "[" << std::put_time(&tm_buf, "%Y-%m-%d %H:%M:%S")
                << "] " << req.method << " " << req.path
                << " -> " << res.status << std::endl;
        });

        // POST /onboarding/save - Сохранение данных онбординга
        svr.Post("/onboarding/save", [&](const httplib::Request& req, httplib::Response& res) {
            try {
                // Проверка авторизации
                auto token_opt = extract_bearer(req);
                if (!token_opt) return json_error(res, 401, "Missing token");

                auto email_opt = JWT::verify(*token_opt);
                if (!email_opt) return json_error(res, 401, "Invalid token");

                std::string email = *email_opt;

                // Парсинг JSON
                auto body = json::parse(req.body, nullptr, false);
                if (body.is_discarded()) {
                    return json_error(res, 400, "Invalid JSON body");
                }

                OnboardingData data;
                data.ai_purpose = body.value("ai_purpose", "");
                data.industry = body.value("industry", "");
                if (body.contains("required_skills") && body["required_skills"].is_array()) {
                    data.required_skills = body["required_skills"].get<std::vector<std::string>>();
                }
                data.current_task_duration_hours = body.value("current_task_duration_hours", 0);
                data.estimated_ai_duration_hours = body.value("estimated_ai_duration_hours", 0);
                data.usage_frequency = body.value("usage_frequency", "");
                data.usage_time_of_day = body.value("usage_time_of_day", "");
                
                // Время завершения онбординга в миллисекундах
                data.completed_at = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::system_clock::now().time_since_epoch()
                ).count();
                data.demo_selected = body.value("demo_selected", false);
                data.plan_selected = body.value("plan_selected", "");
                
                // Сохраняем данные в хранилище
                if (store.saveOnboardingData(email, data)) {
                    nlohmann::json response;
                    response["success"] = true;
                    response["message"] = "Onboarding data saved successfully";

                    // Если выбрана демо-версия, возвращаем время истечения
                    if (data.demo_selected) {
                        auto status = store.getOnboardingStatus(email);
                        response["demo_expires_at"] = status.demo_expires_at;
                    }

                    res.set_content(response.dump(), "application/json");
                }
                else {
                    res.status = 500;
                    res.set_content(R"({"error": "Failed to save onboarding data"})", "application/json");
                }

            }
            catch (const std::exception& e) {
                std::cerr << "[ERROR] /onboarding/save: " << e.what() << std::endl;
                res.status = 500;
                res.set_content(R"({"error": "Internal server error"})", "application/json");
            }
            });

        // GET /onboarding/status - Получение статуса онбординга
        svr.Get("/onboarding/status", [&](const httplib::Request& req, httplib::Response& res) {
            try {
                // используем существующий паттерн extract_bearer + JWT::verify
                auto token_opt = extract_bearer(req);
                if (!token_opt) return json_error(res, 401, "Missing token");

                auto email_opt = JWT::verify(*token_opt);
                if (!email_opt) return json_error(res, 401, "Invalid token");

                std::string email = *email_opt;

                auto status = store.getOnboardingStatus(email);

                // Устанавливаем заголовки кэширования (как в /profile и /balance)
                res.set_header("Cache-Control", "no-cache, no-store, must-revalidate");
                res.set_header("Pragma", "no-cache");
                res.set_header("Expires", "0");

                nlohmann::json response;
                response["onboarding_completed"] = status.onboarding_completed;
                response["demo_active"] = status.demo_active;
                response["demo_expires_at"] = status.demo_expires_at;
                response["subscription_active"] = status.subscription_active;
                response["subscription_type"] = store.getSubscriptionType(email);  // новый метод
                response["subscription_end_date"] = store.getSubscriptionEndDate(email);  // новый метод

                res.set_content(response.dump(), "application/json");

            }
            catch (const std::exception& e) {
                std::cerr << "[ERROR] /onboarding/status: " << e.what() << std::endl;
                res.status = 500;
                res.set_content(R"({"error": "Internal server error"})", "application/json");
            }
            });

        // POST /onboarding/activate-demo - Активация демо-версии
        svr.Post("/onboarding/activate-demo", [&](const httplib::Request& req, httplib::Response& res) {
            try {
                // используем существующий паттерн extract_bearer + JWT::verify
                auto token_opt = extract_bearer(req);
                if (!token_opt) return json_error(res, 401, "Missing token");

                auto email_opt = JWT::verify(*token_opt);
                if (!email_opt) return json_error(res, 401, "Invalid token");

                std::string email = *email_opt;

                if (store.activateDemo(email)) {
                    auto status = store.getOnboardingStatus(email);

                    nlohmann::json response;
                    response["success"] = true;
                    response["message"] = "Demo activated successfully";
                    response["demo_expires_at"] = status.demo_expires_at;

                    res.set_content(response.dump(), "application/json");
                }
                else {
                    res.status = 500;
                    res.set_content(R"({"error": "Failed to activate demo"})", "application/json");
                }

            }
            catch (const std::exception& e) {
                std::cerr << "[ERROR] /onboarding/activate-demo: " << e.what() << std::endl;
                res.status = 500;
                res.set_content(R"({"error": "Internal server error"})", "application/json");
            }
        });

        // =============================================================================
        // POST /onboarding/ask-ai - Бесплатный вопрос к AI во время онбординга
        // ПУБЛИЧНЫЙ ЭНДПОИНТ - не требует авторизации (онбординг до регистрации)
        // =============================================================================
        svr.Post("/onboarding/ask-ai", [&](const httplib::Request& req, httplib::Response& res) {
            try {
                // Парсинг JSON (без проверки токена - публичный эндпоинт)
                auto body = json::parse(req.body, nullptr, false);
                if (body.is_discarded()) {
                    return json_error(res, 400, "Invalid JSON body");
                }

                if (!body.contains("question")) {
                    return json_error(res, 400, "Question required");
                }

                std::string question = body["question"];

                // Проверка длины вопроса (защита от злоупотреблений)
                if (question.length() > 1000) {
                    return json_error(res, 400, "Question too long (max 1000 characters)");
                }

                // IP-адрес для логирования (вместо email)
                std::string client_ip = req.remote_addr;

                // Системный промпт для онбординга
                std::string system_prompt = "Ты — AI-ассистент платформы AiGen. "
                    "Помогай пользователям понять возможности платформы во время онбординга. "
                    "Отвечай кратко, дружелюбно и по существу на том языке, на котором задан вопрос.";

                // Rate limiting: не более 5 запросов с одного IP за минуту
                static std::map<std::string, std::vector<std::chrono::steady_clock::time_point>> ip_request_times;
                auto now = std::chrono::steady_clock::now();
                auto& times = ip_request_times[client_ip];

                // Удаляем запросы старше 1 минуты
                times.erase(
                    std::remove_if(times.begin(), times.end(),
                        [&](const auto& t) {
                            return std::chrono::duration_cast<std::chrono::minutes>(now - t).count() > 1;
                        }),
                    times.end()
                );

                if (times.size() >= 5) {
                    return json_error(res, 429, "Too many requests. Please try again later.");
                }

                times.push_back(now);

                // Вызов Mistral API (бесплатный вопрос, без проверки баланса)
                std::string result = Utils::call_mistral_ai(question, system_prompt);

                // Логирование с IP-адресом вместо email
                Utils::log_audit(client_ip, "onboarding-ask-ai-public", question, result);

                nlohmann::json response;
                response["success"] = true;
                response["answer"] = result;

                res.set_content(response.dump(), "application/json");
            }
            catch (const std::exception& e) {
                std::cerr << "[ERROR] /onboarding/ask-ai: " << e.what() << std::endl;
                res.status = 500;
                res.set_content(R"({"error": "Internal server error"})", "application/json");
            }
        });

        // =============================================================================
// POST /onboarding/save - Сохранение данных онбординга
// =============================================================================
        svr.Post("/onboarding/save", [&](const httplib::Request& req, httplib::Response& res) {
            try {
                auto token_opt = extract_bearer(req);
                if (!token_opt) return json_error(res, 401, "Missing token");

                auto email_opt = JWT::verify(*token_opt);
                if (!email_opt) return json_error(res, 401, "Invalid token");

                std::string email = *email_opt;

                auto body = json::parse(req.body, nullptr, false);
                if (body.is_discarded()) {
                    return json_error(res, 400, "Invalid JSON body");
                }

                OnboardingData data;
                data.ai_purpose = body.value("ai_purpose", "");
                data.industry = body.value("industry", "");

                if (body.contains("required_skills") && body["required_skills"].is_array()) {
                    for (const auto& skill : body["required_skills"]) {
                        if (skill.is_string()) {
                            data.required_skills.push_back(skill.get<std::string>());
                        }
                    }
                }

                data.current_task_duration_hours = body.value("current_task_duration_hours", 0);
                data.estimated_ai_duration_hours = body.value("estimated_ai_duration_hours", 0);
                data.usage_frequency = body.value("usage_frequency", "");
                data.usage_time_of_day = body.value("usage_time_of_day", "");
                data.demo_selected = body.value("demo_selected", false);
                data.plan_selected = body.value("plan_selected", "");

                auto now = std::chrono::system_clock::now();
                data.completed_at = std::chrono::duration_cast<std::chrono::milliseconds>(
                    now.time_since_epoch()
                ).count();

                if (!store.saveOnboardingData(email, data)) {
                    return json_error(res, 500, "Failed to save onboarding data");
                }

                Utils::log_audit(email, "onboarding-save",
                    "Purpose: " + data.ai_purpose + ", Industry: " + data.industry,
                    "Onboarding completed");

                nlohmann::json response;
                response["success"] = true;
                response["message"] = "Onboarding data saved successfully";
                response["onboarding_completed"] = true;
                response["demo_active"] = data.demo_selected;

                res.set_content(response.dump(), "application/json");
            }
            catch (const std::exception& e) {
                std::cerr << "[ERROR] /onboarding/save: " << e.what() << std::endl;
                res.status = 500;
                res.set_content(R"({"error": "Internal server error"})", "application/json");
            }
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
        // GET /profile
        // ---------------------------------------------------------------------
        svr.Get("/profile", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

            auto ud_opt = store.get_user(*email_opt);
            if (!ud_opt) return json_error(res, 404, "User not found");

            res.set_header("Cache-Control", "no-cache, no-store, must-revalidate");
            res.set_header("Pragma", "no-cache");
            res.set_header("Expires", "0");

            json_ok(res, {
                {"email", *email_opt},
                {"card_mask", ud_opt->card_mask},
                { "balance", ud_opt->balance }
                });
            });

        // ---------------------------------------------------------------------
        // PUT /profile 
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
        // DELETE /profile 
        // ---------------------------------------------------------------------
        svr.Delete("/profile", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email_opt = JWT::verify(*token_opt);
            if (!email_opt) return json_error(res, 401, "Invalid token");

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
        // POST /agent/register 
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
        // GET /agents 
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
            json_ok(res, j_agents);
            });

        // =============================================================================
        // GET /myagents 
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
            if (!agent_opt) return json_error(res, 404, "Agent not found");

            if (agent_opt->owner_email != *email_opt) {
                return json_error(res, 403, "You do not have permission to edit this agent");
            }

            auto body = json::parse(req.body, nullptr, false);
            if (body.is_discarded()) return json_error(res, 400, "Invalid JSON body");

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
            if (!agent_opt) return json_error(res, 404, "Agent not found");

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
        // GET /balance 
        // =============================================================================
        svr.Get("/balance", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email = JWT::verify(*token_opt);
            if (!email) return json_error(res, 401, "Invalid token");

            res.set_header("Cache-Control", "no-cache, no-store, must-revalidate");
            res.set_header("Pragma", "no-cache");
            res.set_header("Expires", "0");

            double balance = store.get_balance(*email);
            json_ok(res, { {"balance", balance} });
            });

        // =============================================================================
        // POST /balance/topup 
        // =============================================================================
        svr.Post("/balance/topup", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto email = JWT::verify(*token_opt);
            if (!email) return json_error(res, 401, "Invalid token");

            auto body = json::parse(req.body, nullptr, false);
            if (!body.contains("amount")) return json_error(res, 400, "amount required");

            double amount = body["amount"].get<double>();
            if (amount <= 0 || amount > 1000) {
                return json_error(res, 400, "Invalid amount (must be between 0 and 1000)");
            }

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
        // POST /billing/subscribe - Обработка подписки (списание средств + активация)
        // =============================================================================
        svr.Post("/billing/subscribe", [&](const httplib::Request& req, httplib::Response& res) {
            try {
                // Проверка авторизации
                auto token_opt = extract_bearer(req);
                if (!token_opt) return json_error(res, 401, "Missing token");
                auto email_opt = JWT::verify(*token_opt);
                if (!email_opt) return json_error(res, 401, "Invalid token");

                std::string email = *email_opt;

                // Парсинг JSON
                auto body = json::parse(req.body, nullptr, false);
                if (body.is_discarded()) {
                    return json_error(res, 400, "Invalid JSON body");
                }

                if (!body.contains("plan_type")) {
                    return json_error(res, 400, "plan_type required");
                }

                std::string plan_type = body["plan_type"];

                // Определяем стоимость плана
                double plan_cost = 0.0;
                if (plan_type == "monthly") {
                    plan_cost = 9.99;
                }
                else if (plan_type == "yearly") {
                    plan_cost = 110.0;
                }
                else {
                    return json_error(res, 400, "Invalid plan_type. Use 'monthly' or 'yearly'");
                }

                // Проверяем баланс пользователя
                if (!store.has_sufficient_balance(email, plan_cost)) {
                    return json_error(res, 402, "Insufficient balance. Please top up your account.");
                }

                // Списываем средства
                if (!store.update_balance(email, -plan_cost)) {
                    return json_error(res, 500, "Failed to charge balance");
                }

                // Активируем подписку
                if (!store.activateSubscription(email, plan_type)) {
                    // Откатываем списание, если не удалось активировать подписку
                    store.update_balance(email, plan_cost);
                    return json_error(res, 500, "Failed to activate subscription");
                }

                // Логирование
                Utils::log_audit(email, "billing-subscribe",
                    "Plan: " + plan_type + ", Cost: " + std::to_string(plan_cost),
                    "Subscription activated");

                nlohmann::json response;
                response["success"] = true;
                response["message"] = "Subscription activated successfully";
                response["plan_type"] = plan_type;
                response["amount_charged"] = plan_cost;
                response["new_balance"] = store.get_balance(email);

                res.set_content(response.dump(), "application/json");
            }
            catch (const std::exception& e) {
                std::cerr << "[ERROR] /billing/subscribe: " << e.what() << std::endl;
                res.status = 500;
                res.set_content(R"({"error": "Internal server error"})", "application/json");
            }
        });

        // =============================================================================
        // POST /agent/invoke 
        // =============================================================================
        svr.Post("/agent/invoke", [&](const httplib::Request& req, httplib::Response& res) {
            auto token_opt = extract_bearer(req);
            if (!token_opt) return json_error(res, 401, "Missing token");
            auto caller_email = JWT::verify(*token_opt);
            if (!caller_email) return json_error(res, 401, "Invalid token");

            // === Проверка статуса онбординга ===
            auto onboarding_status = store.getOnboardingStatus(*caller_email);

            if (!onboarding_status.onboarding_completed) {
                return json_error(res, 403, "Please complete onboarding first");
            }

            // Если демо-версия истекла и нет активной подписки - блокируем доступ
            if (!onboarding_status.demo_active && !onboarding_status.subscription_active) {
                return json_error(res, 402,
                    "Demo period expired. Please subscribe to continue using the service.");
            }

            auto body = json::parse(req.body, nullptr, false);
            if (!body.contains("agentId") || !body.contains("prompt")) {
                return json_error(res, 400, "agentId and prompt required");
            }
            std::string agent_id = body["agentId"];
            std::string prompt = body["prompt"];

            auto agent_opt = agent_store.find(agent_id);
            if (!agent_opt) return json_error(res, 404, "Agent not found");

            bool has_skills = !agent_opt->skills.empty();

            if (has_skills) {
                auto [card_token, card_mask] = store.get_payment_info(*caller_email);
                if (card_token.empty()) {
                    return json_error(res, 402, "No payment method attached. Please add a card in your profile.");
                }
                double required_amount = agent_opt->skills.size() * BillingConfig::SKILL_USAGE_COST;
                if (!store.has_sufficient_balance(*caller_email, required_amount)) {
                    return json_error(res, 402, "Insufficient balance. Required: $" +
                        std::to_string(required_amount) + ", Available: $" +
                        std::to_string(store.get_balance(*caller_email)));
                }
            }

            std::string system_prompt = "Ты — AI-агент по имени '" + agent_opt->name + "'. ";
            if (!agent_opt->description.empty()) {
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

            bool has_analysis_synthesis = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_1") has_analysis_synthesis = true;
            if (has_analysis_synthesis) {
                system_prompt += "\n\n[АКТИВНЫЙ НАВЫК: АНАЛИЗ И СИНТЕЗ]\n"
                    "Ты обладаешь специализированным когнитивным навыком. При ответе ОБЯЗАН применять алгоритм:\n"
                    "1. АНАЛИЗ: Декомпозируй информацию, выдели факты и скрытые паттерны.\n"
                    "2. СВЯЗИ: Найди логические и причинно-следственные связи.\n"
                    "3. СИНТЕЗ: Сделай глубокое обобщение и предложи итоговое структурированное решение.\n"
                    "Структурируй ответ, явно выделяя эти этапы.";
            }

            bool has_reasoning = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_2") has_reasoning = true;
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

            bool has_criticalThinking = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_3") has_criticalThinking = true;
            if (has_criticalThinking) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: КРИТИЧЕСКОЕ МЫШЛЕНИЕ] Примени строгий алгоритм критического мышления к запросу ниже...)";
            }

            bool has_creativitySkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_4") has_creativitySkill = true;
            if (has_creativitySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: КРЕАТИВНОСТЬ] Ты — высоко креативный AI-агент...)";
            }

            bool has_planningSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_5") has_planningSkill = true;
            if (has_planningSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПЛАНИРОВАНИЕ] Ты — ИИ-агент с сильным навыком планирования...)";
            }

            bool has_problemSolvingSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_6") has_problemSolvingSkill = true;
            if (has_problemSolvingSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: РЕШЕНИЕ ПРОБЛЕМ] Ты — ИИ-агент с превосходным навыком решения проблем...)";
            }

            bool has_trainingSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_7") has_trainingSkill = true;
            if (has_trainingSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ОБУЧЕНИЕ] Ты — ИИ-агент с сильным навыком обучения...)";
            }

            bool has_memorySkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "cog_8") has_memorySkill = true;
            if (has_memorySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПАМЯТЬ] Ты — ИИ-агент с сильным навыком памяти...)";
            }

            bool has_naturalLanguageSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_1") has_naturalLanguageSkill = true;
            if (has_naturalLanguageSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПОНЯТИЕ ЕСТЕСТВЕННОГО ЯЗЫКА] ...)";
            }

            bool has_textGenerationSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_2") has_textGenerationSkill = true;
            if (has_textGenerationSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ГЕНЕРАЦИЯ ТЕКСТА] ...)";
            }

            bool has_multilingualitySkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_3") has_multilingualitySkill = true;
            if (has_multilingualitySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: МУЛЬТИЯЗЫЧНОСТЬ] ...)";
            }

            bool has_dialogInteractionSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_4") has_dialogInteractionSkill = true;
            if (has_dialogInteractionSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ДИАЛОГОВОЕ ВЗАИМОДЕЙСТВИЕ] ...)";
            }

            bool has_speechRecognitionSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_5") has_speechRecognitionSkill = true;
            if (has_speechRecognitionSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: РАСПОЗНАВАНИЕ РЕЧИ] ...)";
            }

            bool has_speechSynthesisSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_6") has_speechSynthesisSkill = true;
            if (has_speechSynthesisSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: СИНТЕЗ РЕЧИ] ...)";
            }

            bool has_summarySkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_7") has_summarySkill = true;
            if (has_summarySkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: РЕЗЮМИРОВАНИЕ] ...)";
            }

            bool has_rewritingSkill = false;
            for (const auto& skill_id : agent_opt->skills) if (skill_id == "lang_8") has_rewritingSkill = true;
            if (has_rewritingSkill) {
                system_prompt += R"([АКТИВНЫЙ НАВЫК: ПЕРЕПИСЫВАНИЕ] ...)";
            }

            std::string result;
            std::vector<std::string> used_skills;

            if (has_skills) {
                std::string raw_response = Utils::call_mistral_ai(prompt, system_prompt);

                Utils::LLMResponse llm_response;
                llm_response.content = raw_response;

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
                    llm_response.content = raw_response.substr(0, metadata_start);
                }

                result = llm_response.content;
                used_skills = llm_response.used_skills;

                if (!llm_response.parse_success) {
                    used_skills = agent_opt->skills;
                    std::cout << "[BILLING] Metadata parsing failed, using all agent skills as fallback" << std::endl;
                }

                std::vector<std::string> valid_used_skills;
                for (const auto& skill_id : used_skills) {
                    if (std::find(agent_opt->skills.begin(), agent_opt->skills.end(), skill_id) != agent_opt->skills.end()) {
                        valid_used_skills.push_back(skill_id);
                    }
                }
                used_skills = valid_used_skills;

                double total_cost = used_skills.size() * BillingConfig::SKILL_USAGE_COST;
                if (total_cost > 0) {
                    auto [card_token, card_mask] = store.get_payment_info(*caller_email);
                    if (!card_token.empty()) {
                        bool payment_success = Payment::charge_card(card_token, total_cost,
                            "Skills usage: " + std::to_string(used_skills.size()) + " skills");

                        if (payment_success) {
                            if (!store.update_balance(*caller_email, -total_cost)) {
                                std::cerr << "[BILLING] Failed to persist balance deduction for user " << *caller_email << std::endl;
                                return json_error(res, 500, "Failed to update balance after payment");
                            }
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
                result = Utils::call_mistral_ai(prompt, system_prompt);
            }
            Utils::log_audit(*caller_email, agent_id, prompt, result);

            json_ok(res, { {"result", result} });
            });

        // =============================================================================
        // POST /orchestrate 
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

            std::string task_id = Utils::generate_uuid();
            Task task;
            task.task_id = task_id;
            task.orchestrator_email = *caller_email;
            task.status = "pending";
            task.agent_chain = chain;
            task.input_payload = initial_prompt;
            task.created_at = std::chrono::system_clock::now();
            task_store.create(task);

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

                    std::string step_result = Utils::call_mistral_ai(current_payload, system_prompt);
                    Utils::log_audit(caller_email.value(), agent_id, current_payload, step_result);
                    current_payload = step_result;
                }
                if (!failed) {
                    task_store.update_status(task_id, "completed", "", current_payload);
                }
                }).detach();

            json_ok(res, { {"taskId", task_id}, {"status", "pending"} });
            });

        // =============================================================================
        // GET /task/{task_id} 
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