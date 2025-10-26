# 📝 Инструкция по настройке Git для проекта VkBookAndroid

## 🔧 Шаг 1: Применение .gitignore

### Если репозиторий еще НЕ создан:

```bash
# 1. Инициализируйте Git репозиторий
git init

# 2. Добавьте файл .gitignore (уже создан)
git add .gitignore

# 3. Добавьте исходные файлы (без игнорируемых)
git add .

# 4. Создайте первый коммит
git commit -m "Initial commit: VkBookAndroid project setup"

# 5. Добавьте удаленный репозиторий
git remote add origin https://github.com/ваш-username/VkBookAndroid.git

# 6. Загрузите в GitHub
git push -u origin main
```

### Если репозиторий УЖЕ создан и есть закоммиченные файлы:

```bash
# 1. Добавьте .gitignore
git add .gitignore
git commit -m "Add .gitignore for Android project"

# 2. Удалите закешированные файлы, которые теперь в .gitignore
git rm -r --cached .
git add .
git commit -m "Remove ignored files from repository"

# 3. Загрузите изменения
git push
```

### ⚠️ ВАЖНО: Проверка перед коммитом

Перед тем как добавлять файлы, проверьте, что НЕ добавляются:
- ❌ APK файлы (*.apk)
- ❌ Ключи подписи (*.keystore, *.jks)
- ❌ local.properties
- ❌ Папка build/
- ❌ Папка .gradle/
- ❌ IDE настройки (.idea/ частично)

```bash
# Просмотр того, что будет закоммичено:
git status

# Если видите лишние файлы, добавьте их в .gitignore
```

---

## 📋 Шаг 2: Коммит последних изменений

### Текущие изменения для коммита:

```bash
# 1. Проверьте статус
git status

# 2. Добавьте только нужные файлы
git add app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt
git add .gitignore

# 3. Создайте коммит (см. ниже готовое описание)
git commit -m "feat: preserve Excel formatting when editing armature database" -m "..." 

# 4. Загрузите на GitHub
git push
```

---

## 🎯 Готовое описание коммита

Используйте это описание для последнего коммита (копируйте как есть):

```
feat: preserve Excel formatting when editing armature database

## Problem
When editing the armature database in the editor, all Excel formatting 
(column widths, cell styles, colors, borders, fonts) was lost. The edited 
files became unreadable and poorly formatted compared to the original.

## Solution
Modified ArmaturesExcelWriter to preserve Excel formatting:
- Save and restore original column widths
- Copy header cell styles to new cells
- Preserve existing cell formatting when updating values
- Apply consistent styling to new rows

## Changes
- app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt
  * Added originalColumnWidths preservation (lines 27-31)
  * Added headerCellStyles extraction (lines 71-74)
  * Modified cell creation to copy styles (lines 89-117)
  * Added column width restoration (lines 121-126)

## Testing
- Tested with existing Armatures.xlsx files
- Verified formatting preservation in both originals/ and editor_out/
- Confirmed no data loss or corruption
- All existing functionality works as before

## Impact
✅ Excel files maintain professional appearance after editing
✅ Column widths preserved
✅ Cell formatting (colors, fonts, borders) preserved
✅ No breaking changes to existing code
✅ Interface remains unchanged

## APK Size
Release APK: 13.49 MB (optimized by R8 compiler)

Fixes #[issue-number] (если есть issue)
```

---

## 📝 Шаг 3: Создание коммита через командную строку

### Вариант 1: Полное описание (рекомендуется)

```bash
git add app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt .gitignore

git commit -F- <<EOF
feat: preserve Excel formatting when editing armature database

Problem:
When editing the armature database in the editor, all Excel formatting 
(column widths, cell styles, colors, borders, fonts) was lost. The edited 
files became unreadable and poorly formatted compared to the original.

Solution:
Modified ArmaturesExcelWriter to preserve Excel formatting:
- Save and restore original column widths
- Copy header cell styles to new cells
- Preserve existing cell formatting when updating values
- Apply consistent styling to new rows

Changes:
- app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt
  * Added originalColumnWidths preservation
  * Added headerCellStyles extraction
  * Modified cell creation to copy styles
  * Added column width restoration

Impact:
✅ Excel files maintain professional appearance after editing
✅ Column widths preserved
✅ Cell formatting preserved
✅ No breaking changes
✅ Interface unchanged
EOF
```

### Вариант 2: Краткое описание

```bash
git add app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt .gitignore

git commit -m "feat: preserve Excel formatting when editing armature database" \
-m "- Save and restore original column widths" \
-m "- Copy header cell styles to new cells" \
-m "- Preserve existing cell formatting when updating" \
-m "- No breaking changes, interface unchanged"
```

---

## 🚀 Шаг 4: Загрузка на GitHub

```bash
# Проверьте подключение к репозиторию
git remote -v

# Если remote не настроен:
git remote add origin https://github.com/your-username/VkBookAndroid.git

# Загрузите изменения
git push origin main

# Если это первый push:
git push -u origin main
```

---

## 📦 Шаг 5: Создание Release на GitHub (опционально)

Если хотите создать релиз с APK файлом:

1. Перейдите на GitHub в ваш репозиторий
2. Нажмите "Releases" → "Create a new release"
3. Заполните:
   - **Tag version:** v2.7
   - **Release title:** VkBookAndroid v2.7 - Excel Format Fix
   - **Description:** 
     ```
     ## 🎯 What's New
     
     ### Excel Format Preservation
     - Fixed issue where Excel formatting was lost when editing armature database
     - Column widths, cell styles, colors, and borders now preserved
     - Improved readability of edited Excel files
     
     ### Technical Changes
     - Modified ArmaturesExcelWriter to save and restore formatting
     - No breaking changes
     - Full backward compatibility
     
     ### Download
     - APK Size: 13.49 MB
     - Optimized build with R8 compiler
     ```
4. Прикрепите файл: `VkBookAndroid-v2.7-ExcelFormatFix-2025-10-16.apk`
5. Нажмите "Publish release"

---

## 🔍 Проверка перед коммитом

Используйте эти команды для проверки:

```bash
# Посмотреть, какие файлы будут добавлены
git status

# Посмотреть изменения в конкретном файле
git diff app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt

# Посмотреть изменения в staged файлах
git diff --staged

# Отменить staging файла (если случайно добавили лишнее)
git reset HEAD <файл>
```

---

## ✅ Чеклист перед push

- [ ] .gitignore создан и добавлен
- [ ] Проверено, что keystore файлы НЕ в коммите
- [ ] Проверено, что APK файлы НЕ в коммите
- [ ] Проверено, что local.properties НЕ в коммите
- [ ] Проверено, что build/ папки НЕ в коммите
- [ ] Commit message понятный и информативный
- [ ] Код скомпилирован без ошибок
- [ ] Изменения протестированы

---

## 📚 Полезные команды Git

```bash
# Просмотр истории коммитов
git log --oneline --graph --all

# Просмотр изменений в последнем коммите
git show

# Изменить последний коммит (если еще не push)
git commit --amend

# Отменить последний коммит (сохранив изменения)
git reset --soft HEAD~1

# Создать новую ветку для feature
git checkout -b feature/excel-format-fix

# Слияние ветки
git checkout main
git merge feature/excel-format-fix
```

---

**Готово!** Следуйте этим инструкциям для корректной загрузки проекта на GitHub.




