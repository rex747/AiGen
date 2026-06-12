#pragma once

#include <string>
#include <unordered_map>
#include <fstream>
#include <cstdint>
#include <mutex>
#include <iostream>
#include <cstring>
#include <vector>
#include <atomic>
#include <thread>

// ============================================================================
// Write-Ahead Logging (WAL) для гарантии консистентности баланса
// ============================================================================

enum class WalEntryType : uint8_t {
    BEGIN_TX = 1,           // Начало транзакции
    UPDATE_BALANCE = 2,     // Обновление баланса
    COMMIT_TX = 3,          // Фиксация транзакции
    ABORT_TX = 4            // Откат транзакции
};

// Структура записи в WAL (фиксированный размер для быстрого чтения)
#pragma pack(push, 1)
struct WalEntry {
    uint32_t magic;         // Магический маркер для валидации (0xDEADBEEF)
    uint64_t tx_id;         // ID транзакции
    WalEntryType entry_type; // Тип записи
    uint8_t email_len;      // Длина email (макс 255)
    char email[255];        // Email пользователя (NULL-терминирован)
    double new_balance;     // Новый баланс
    uint64_t new_version;   // Версия для контроля консистентности
    uint32_t checksum;      // CRC32 для целостности
};
#pragma pack(pop)

// ============================================================================
// CRC32 хеш для контроля целостности данных
// ============================================================================

inline uint32_t crc32_checksum(const void* data, size_t len) {
    uint32_t crc = 0xFFFFFFFF;
    const uint8_t* bytes = static_cast<const uint8_t*>(data);

    for (size_t i = 0; i < len; ++i) {
        crc ^= bytes[i];
        for (int j = 0; j < 8; ++j) {
            if (crc & 1) {
                crc = (crc >> 1) ^ 0xEDB88320;  // Стандартный полином CRC32
            }
            else {
                crc >>= 1;
            }
        }
    }
    return crc ^ 0xFFFFFFFF;
}

// ============================================================================
// WAL Manager — управление журналом транзакций
// ============================================================================

class WalManager {
public:
    explicit WalManager(const std::string& wal_path)
        : wal_path_(wal_path), next_tx_id_(1) {

        // Открываем или создаём WAL файл в режиме append + binary
        wal_file_.open(wal_path, std::ios::app | std::ios::binary);

        if (!wal_file_.is_open()) {
            throw std::runtime_error("[WAL] Failed to open WAL file: " + wal_path);
        }

        std::cout << "[WAL] Initialized. Path: " << wal_path << std::endl;
    }

    ~WalManager() {
        if (wal_file_.is_open()) {
            wal_file_.flush();
            wal_file_.close();
        }
    }

    // ========================================================================
    // Логирование BEGIN + UPDATE атомарно
    // Возвращает ID транзакции для последующего COMMIT/ABORT
    // ========================================================================
    uint64_t log_begin_update(const std::string& email,
        double new_balance,
        uint64_t new_version) {
        std::lock_guard<std::mutex> lock(wal_mtx_);

        uint64_t tx_id = next_tx_id_++;

        // Запись BEGIN
        WalEntry begin_entry;
        std::memset(&begin_entry, 0, sizeof(WalEntry));
        begin_entry.magic = 0xDEADBEEF;
        begin_entry.tx_id = tx_id;
        begin_entry.entry_type = WalEntryType::BEGIN_TX;
        begin_entry.email_len = 0;

        if (!write_entry_unlocked(begin_entry)) {
            std::cerr << "[WAL] Failed to write BEGIN entry" << std::endl;
            return 0;
        }

        // Запись UPDATE
        WalEntry update_entry;
        std::memset(&update_entry, 0, sizeof(WalEntry));
        update_entry.magic = 0xDEADBEEF;
        update_entry.tx_id = tx_id;
        update_entry.entry_type = WalEntryType::UPDATE_BALANCE;

        // Копируем email (максимум 254 символа, 255-й это null-терминатор)
        size_t email_len = email.length();
        if (email_len > 254) email_len = 254;
        update_entry.email_len = static_cast<uint8_t>(email_len);
        std::memcpy(update_entry.email, email.c_str(), email_len);
        update_entry.email[email_len] = '\0';

        update_entry.new_balance = new_balance;
        update_entry.new_version = new_version;

        if (!write_entry_unlocked(update_entry)) {
            std::cerr << "[WAL] Failed to write UPDATE entry" << std::endl;
            return 0;
        }

        // Синхронизируем на диск
        if (!fsync_internal()) {
            std::cerr << "[WAL] fsync failed after BEGIN+UPDATE" << std::endl;
            return 0;
        }

        std::cout << "[WAL] BEGIN+UPDATE logged: tx=" << tx_id
            << " email=" << email
            << " balance=" << new_balance << std::endl;

        return tx_id;
    }

    // ========================================================================
    // Логирование COMMIT
    // ========================================================================
    bool log_commit(uint64_t tx_id) {
        std::lock_guard<std::mutex> lock(wal_mtx_);

        WalEntry commit_entry;
        std::memset(&commit_entry, 0, sizeof(WalEntry));
        commit_entry.magic = 0xDEADBEEF;
        commit_entry.tx_id = tx_id;
        commit_entry.entry_type = WalEntryType::COMMIT_TX;

        if (!write_entry_unlocked(commit_entry)) {
            std::cerr << "[WAL] Failed to write COMMIT entry" << std::endl;
            return false;
        }

        if (!fsync_internal()) {
            std::cerr << "[WAL] fsync failed after COMMIT" << std::endl;
            return false;
        }

        std::cout << "[WAL] COMMIT logged: tx=" << tx_id << std::endl;
        return true;
    }

    // ========================================================================
    // Логирование ABORT
    // ========================================================================
    bool log_abort(uint64_t tx_id) {
        std::lock_guard<std::mutex> lock(wal_mtx_);

        WalEntry abort_entry;
        std::memset(&abort_entry, 0, sizeof(WalEntry));
        abort_entry.magic = 0xDEADBEEF;
        abort_entry.tx_id = tx_id;
        abort_entry.entry_type = WalEntryType::ABORT_TX;

        if (!write_entry_unlocked(abort_entry)) {
            std::cerr << "[WAL] Failed to write ABORT entry" << std::endl;
            return false;
        }

        if (!fsync_internal()) {
            std::cerr << "[WAL] fsync failed after ABORT" << std::endl;
            return false;
        }

        std::cout << "[WAL] ABORT logged: tx=" << tx_id << std::endl;
        return true;
    }

    // ========================================================================
    // Структура для возврата восстановленных данных
    // ========================================================================
    struct RecoveryData {
        std::string email;
        double balance;
        uint64_t version;
    };

    // ========================================================================
    // Восстановление консистентного состояния после падения
    // ========================================================================
    std::unordered_map<std::string, RecoveryData> recover() {
        std::unordered_map<std::string, RecoveryData> recovered;
        std::ifstream wal(wal_path_, std::ios::binary);

        if (!wal.is_open()) {
            std::cout << "[WAL] No WAL file found or recovery not needed" << std::endl;
            return recovered;
        }

        std::cout << "[WAL] === Starting recovery ===" << std::endl;

        std::unordered_map<uint64_t, RecoveryData> pending_tx;  // Незавершённые транзакции
        WalEntry entry;
        int entries_read = 0;
        int valid_entries = 0;
        int invalid_entries = 0;

        while (wal.read(reinterpret_cast<char*>(&entry), sizeof(WalEntry))) {
            if (!wal.good() && !wal.eof()) break;

            entries_read++;

            // Валидация magic маркера
            if (entry.magic != 0xDEADBEEF) {
                std::cerr << "[WAL] Invalid magic at entry " << entries_read << std::endl;
                invalid_entries++;
                continue;
            }

            // Валидация checksum
            uint32_t stored_checksum = entry.checksum;
            entry.checksum = 0;
            uint32_t computed_checksum = crc32_checksum(&entry, sizeof(WalEntry));

            if (stored_checksum != computed_checksum) {
                std::cerr << "[WAL] Checksum mismatch at entry " << entries_read
                    << " tx=" << entry.tx_id << std::endl;
                invalid_entries++;
                continue;
            }

            valid_entries++;

            // Обработка в зависимости от типа записи
            switch (entry.entry_type) {
            case WalEntryType::UPDATE_BALANCE: {
                std::string email(entry.email, entry.email_len);
                pending_tx[entry.tx_id] = {
                    email,
                    entry.new_balance,
                    entry.new_version
                };
                std::cout << "[WAL] Recovered UPDATE: tx=" << entry.tx_id
                    << " email=" << email
                    << " balance=" << entry.new_balance
                    << " version=" << entry.new_version << std::endl;
                break;
            }

            case WalEntryType::COMMIT_TX: {
                auto it = pending_tx.find(entry.tx_id);
                if (it != pending_tx.end()) {
                    recovered[it->second.email] = it->second;
                    std::cout << "[WAL] Committed: tx=" << entry.tx_id
                        << " email=" << it->second.email
                        << " balance=" << it->second.balance << std::endl;
                    pending_tx.erase(it);
                }
                break;
            }

            case WalEntryType::ABORT_TX: {
                auto it = pending_tx.find(entry.tx_id);
                if (it != pending_tx.end()) {
                    std::cout << "[WAL] Aborted: tx=" << entry.tx_id << std::endl;
                    pending_tx.erase(it);
                }
                break;
            }

            case WalEntryType::BEGIN_TX: {
                // Просто отмечаем начало транзакции
                std::cout << "[WAL] BEGIN: tx=" << entry.tx_id << std::endl;
                break;
            }
            }
        }

        // Оставшиеся незавершённые транзакции отбрасываем
        for (const auto& [tx_id, _] : pending_tx) {
            std::cout << "[WAL] Discarding incomplete tx=" << tx_id << std::endl;
        }

        std::cout << "[WAL] === Recovery complete ===" << std::endl;
        std::cout << "[WAL] Entries read: " << entries_read << std::endl;
        std::cout << "[WAL] Valid entries: " << valid_entries << std::endl;
        std::cout << "[WAL] Invalid entries: " << invalid_entries << std::endl;
        std::cout << "[WAL] Recovered users: " << recovered.size() << std::endl;

        return recovered;
    }

    // ========================================================================
    // Очистка WAL после успешного checkpoint
    // ========================================================================
    void truncate() {
        std::lock_guard<std::mutex> lock(wal_mtx_);

        wal_file_.close();

        // 1. Открываем с флагом trunc, чтобы гарантированно очистить файл
        wal_file_.open(wal_path_, std::ios::out | std::ios::trunc | std::ios::binary);
        if (wal_file_.is_open()) {
            wal_file_.close();
        }

        // 2. Переоткрываем в режиме append для последующих записей
        wal_file_.open(wal_path_, std::ios::app | std::ios::binary);

        if (!wal_file_.is_open()) {
            std::cerr << "[WAL] CRITICAL: Failed to reopen WAL file after truncation!" << std::endl;
        }

        next_tx_id_ = 1;
        std::cout << "[WAL] Truncated (reset)" << std::endl;
    }

    // ========================================================================
    // Получение текущего ID транзакции (для отладки)
    // ========================================================================
    uint64_t get_next_tx_id() const {
        return next_tx_id_.load();
    }

private:
    std::string wal_path_;
    std::ofstream wal_file_;
    std::mutex wal_mtx_;
    std::atomic<uint64_t> next_tx_id_;

    // Вспомогательный метод: запись записи БЕЗ блокировки (вызывается под wal_mtx_)
    bool write_entry_unlocked(const WalEntry& entry) {
        WalEntry entry_to_write = entry;

        // Вычисляем checksum (исключаем сам checksum из вычисления)
        entry_to_write.checksum = 0;
        entry_to_write.checksum = crc32_checksum(&entry_to_write, sizeof(WalEntry));

        // Записываем в бинарном виде
        wal_file_.write(reinterpret_cast<const char*>(&entry_to_write), sizeof(WalEntry));

        return wal_file_.good();
    }

    // Синхронизация на диск (вызывается под wal_mtx_)
    bool fsync_internal() {
        wal_file_.flush();

#ifdef _WIN32
        // Windows
        HANDLE h = reinterpret_cast<HANDLE>(_get_osfhandle(_fileno(const_cast<FILE*>(reinterpret_cast<const FILE*>(wal_file_.rdbuf())))));
        if (h == INVALID_HANDLE_VALUE) {
            std::cerr << "[WAL] Failed to get file handle for fsync" << std::endl;
            return false;
        }
        if (!FlushFileBuffers(h)) {
            std::cerr << "[WAL] FlushFileBuffers failed: " << GetLastError() << std::endl;
            return false;
        }
        return true;
#elif defined(__linux__) || defined(__APPLE__)
        // Linux/macOS
        return ::fsync(fileno(const_cast<FILE*>(reinterpret_cast<const FILE*>(wal_file_.rdbuf())))) == 0;
#else
        // Fallback
        return true;
#endif
    }
};
