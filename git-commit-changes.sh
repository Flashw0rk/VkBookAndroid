#!/bin/bash
# Bash скрипт для коммита изменений Excel форматирования
# Использование: chmod +x git-commit-changes.sh && ./git-commit-changes.sh

echo "=== Git Commit Script: Excel Format Fix ==="
echo ""

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Проверка, что мы в Git репозитории
if [ ! -d ".git" ]; then
    echo -e "${YELLOW}⚠️  Git репозиторий не найден. Инициализировать? (yes/no)${NC}"
    read response
    if [ "$response" = "yes" ]; then
        git init
        echo -e "${GREEN}✅ Git репозиторий инициализирован${NC}"
    else
        echo -e "${RED}❌ Отменено${NC}"
        exit 1
    fi
fi

echo -e "${YELLOW}📁 Текущий статус Git:${NC}"
git status --short
echo ""

# Проверка наличия изменений
echo -e "${YELLOW}📝 Добавление файлов...${NC}"
files=(
    "app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt"
    ".gitignore"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        git add "$file"
        echo -e "${GREEN}  ✅ Добавлен: $file${NC}"
    else
        echo -e "${YELLOW}  ⚠️  Файл не найден: $file${NC}"
    fi
done

echo ""
echo -e "${YELLOW}📋 Создание коммита...${NC}"

# Читаем commit message из файла
if [ -f "COMMIT_MESSAGE.txt" ]; then
    git commit -F COMMIT_MESSAGE.txt
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Коммит создан успешно!${NC}"
        echo ""
        echo -e "${YELLOW}📊 Последний коммит:${NC}"
        git log -1 --oneline
        echo ""
        
        # Спрашиваем о push
        echo -e "${YELLOW}🚀 Push изменения на GitHub? (yes/no)${NC}"
        read response
        if [ "$response" = "yes" ]; then
            echo -e "${YELLOW}Pushing to remote...${NC}"
            git push
            if [ $? -eq 0 ]; then
                echo -e "${GREEN}✅ Изменения загружены на GitHub!${NC}"
            else
                echo -e "${RED}⚠️  Ошибка при push. Проверьте настройки remote:${NC}"
                git remote -v
            fi
        fi
    else
        echo -e "${RED}❌ Ошибка при создании коммита${NC}"
        echo -e "${YELLOW}Возможные причины:${NC}"
        echo "  - Нет изменений для коммита"
        echo "  - Файлы уже закоммичены"
        echo "  - Нужно настроить git config (user.name, user.email)"
    fi
else
    echo -e "${RED}❌ Файл COMMIT_MESSAGE.txt не найден${NC}"
    exit 1
fi

echo ""
echo -e "${CYAN}✅ Скрипт завершен${NC}"




