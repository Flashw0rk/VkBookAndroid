# 📊 **Структура баз данных на VkBook Server**

## 🎯 **Рекомендуемая структура файлов на сервере:**

```
/var/www/vkbook-server/
├── data/
│   ├── databases/
│   │   ├── armature/                    # База данных арматуры
│   │   │   ├── armature_coords.json    # Координаты арматуры
│   │   │   ├── armature_markers.json   # Маркеры арматуры
│   │   │   └── armature_versions.json  # Версии файлов
│   │   ├── schemes/                     # База данных схем
│   │   │   ├── schemes_list.json       # Список схем
│   │   │   ├── schemes_metadata.json   # Метаданные схем
│   │   │   └── schemes_versions.json   # Версии схем
│   │   └── signals/                     # База данных сигналов
│   │       ├── signals_data.json       # Данные сигналов
│   │       ├── signals_metadata.json   # Метаданные сигналов
│   │       └── signals_versions.json   # Версии сигналов
│   ├── files/
│   │   ├── excel/                      # Excel файлы
│   │   │   ├── armature/
│   │   │   ├── schemes/
│   │   │   └── signals/
│   │   ├── pdf/                        # PDF файлы
│   │   │   ├── armature/
│   │   │   ├── schemes/
│   │   │   └── signals/
│   │   └── images/                     # Изображения
│   │       ├── armature/
│   │       ├── schemes/
│   │       └── signals/
│   └── metadata/
│       ├── file_versions.json          # Версии всех файлов
│       ├── update_log.json             # Лог обновлений
│       └── server_info.json            # Информация о сервере
```

## 🔄 **API Endpoints для обновлений:**

### **1. Проверка обновлений:**
```
GET /api/updates/check
Response: {
  "hasUpdates": true,
  "lastUpdate": "2025-09-16T10:30:00Z",
  "files": [
    {
      "type": "armature",
      "filename": "armature_coords.json",
      "version": "1.2.3",
      "size": 1024,
      "hash": "sha256:abc123...",
      "lastModified": "2025-09-16T10:30:00Z"
    }
  ]
}
```

### **2. Получение списка файлов:**
```
GET /api/files/list?type=armature
Response: {
  "success": true,
  "data": [
    {
      "filename": "armature_coords.json",
      "version": "1.2.3",
      "size": 1024,
      "hash": "sha256:abc123...",
      "lastModified": "2025-09-16T10:30:00Z",
      "downloadUrl": "/api/files/download?filename=armature_coords.json"
    }
  ]
}
```

### **3. Скачивание файла:**
```
GET /api/files/download?filename=armature_coords.json
Response: File content
```

### **4. Получение метаданных:**
```
GET /api/metadata/versions
Response: {
  "armature": {
    "version": "1.2.3",
    "lastUpdate": "2025-09-16T10:30:00Z",
    "files": ["armature_coords.json", "armature_markers.json"]
  },
  "schemes": {
    "version": "2.1.0",
    "lastUpdate": "2025-09-15T15:20:00Z",
    "files": ["schemes_list.json"]
  },
  "signals": {
    "version": "1.0.5",
    "lastUpdate": "2025-09-14T09:15:00Z",
    "files": ["signals_data.json"]
  }
}
```

## 📝 **Формат файлов метаданных:**

### **file_versions.json:**
```json
{
  "armature": {
    "armature_coords.json": {
      "version": "1.2.3",
      "size": 1024,
      "hash": "sha256:abc123...",
      "lastModified": "2025-09-16T10:30:00Z",
      "description": "Обновленные координаты арматуры"
    }
  },
  "schemes": {
    "schemes_list.json": {
      "version": "2.1.0",
      "size": 2048,
      "hash": "sha256:def456...",
      "lastModified": "2025-09-15T15:20:00Z",
      "description": "Новые схемы"
    }
  },
  "signals": {
    "signals_data.json": {
      "version": "1.0.5",
      "size": 512,
      "hash": "sha256:ghi789...",
      "lastModified": "2025-09-14T09:15:00Z",
      "description": "Обновленные сигналы"
    }
  }
}
```

### **update_log.json:**
```json
{
  "updates": [
    {
      "timestamp": "2025-09-16T10:30:00Z",
      "type": "armature",
      "filename": "armature_coords.json",
      "version": "1.2.3",
      "action": "update",
      "description": "Добавлены новые координаты арматуры"
    },
    {
      "timestamp": "2025-09-15T15:20:00Z",
      "type": "schemes",
      "filename": "schemes_list.json",
      "version": "2.1.0",
      "action": "update",
      "description": "Обновлены схемы"
    }
  ]
}
```

## 🔧 **Настройка сервера:**

### **1. Создание директорий:**
```bash
sudo mkdir -p /var/www/vkbook-server/data/{databases,files,metadata}
sudo mkdir -p /var/www/vkbook-server/data/databases/{armature,schemes,signals}
sudo mkdir -p /var/www/vkbook-server/data/files/{excel,pdf,images}
sudo mkdir -p /var/www/vkbook-server/data/files/excel/{armature,schemes,signals}
sudo mkdir -p /var/www/vkbook-server/data/files/pdf/{armature,schemes,signals}
sudo mkdir -p /var/www/vkbook-server/data/files/images/{armature,schemes,signals}
```

### **2. Права доступа:**
```bash
sudo chown -R www-data:www-data /var/www/vkbook-server/data
sudo chmod -R 755 /var/www/vkbook-server/data
```

### **3. Настройка CORS (если нужно):**
```java
@CrossOrigin(origins = "*")
@RestController
public class FileController {
    // Ваши контроллеры
}
```

## 📱 **Интеграция в Android приложение:**

### **1. Обновление VkBookApiClient:**
```java
// Проверка обновлений
public void checkForUpdates(Callback callback) {
    Request request = new Request.Builder()
            .url(ServerConfig.API_BASE_URL + "/updates/check")
            .build();
    client.newCall(request).enqueue(callback);
}

// Получение метаданных
public void getMetadata(Callback callback) {
    Request request = new Request.Builder()
            .url(ServerConfig.API_BASE_URL + "/metadata/versions")
            .build();
    client.newCall(request).enqueue(callback);
}
```

### **2. Логика обновления:**
```kotlin
class UpdateManager {
    fun checkAndUpdate() {
        // 1. Проверить версии на сервере
        // 2. Сравнить с локальными версиями
        // 3. Скачать обновленные файлы
        // 4. Обновить локальную базу данных
    }
}
```

## 🎯 **Рекомендации по развертыванию:**

1. **Создайте базовые файлы** с начальными данными
2. **Настройте автоматическое резервное копирование**
3. **Добавьте логирование всех операций**
4. **Настройте мониторинг доступности сервера**
5. **Создайте систему уведомлений об обновлениях**

## 📊 **Мониторинг и логирование:**

```json
{
  "server_info": {
    "version": "1.0.0",
    "lastRestart": "2025-09-16T08:00:00Z",
    "uptime": "2h 30m",
    "totalRequests": 1250,
    "successfulRequests": 1200,
    "failedRequests": 50
  }
}
```

Эта структура обеспечит:
- ✅ Организованное хранение данных
- ✅ Простую систему версионирования
- ✅ Эффективные обновления
- ✅ Отслеживание изменений
- ✅ Масштабируемость


