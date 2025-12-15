# ПОЛНАЯ СПЕЦИФИКАЦИЯ ТЕМ VKBOOKANDROID

## ТЕМА 1: КЛАССИЧЕСКАЯ (THEME_CLASSIC = 0)

### Шкала часов (HoursAdapter):
```kotlin
// Выделенный час:
selected: bg=#64B5F6, text=#0D47A1

// Активный диапазон (текущая смена):
08-13 (утро):  bg=#388E3C, text=#FFFFFF
14-19 (день):  bg=lighten(#2196F3, 0.3f), text=#2196F3
20-07 (ночь):  bg=lighten(#78909C, 0.7f), text=#78909C

// Неактивный диапазон:
08-13 (утро):  bg=lighten(#388E3C, 0.5f), text=#2E7D32
14-19 (день):  bg=lighten(#2196F3, 0.6f), text=#1976D2
20-07 (ночь):  bg=lighten(#78909C, 0.8f), text=#546E7A

// По умолчанию: bg=#CCCCCC, text=#212121
```

### Календарь (MonthAdapter):
```kotlin
// Сегодняшний день + выделенный:
bg=#90CAF9, text=#1976D2

// Сегодняшний день (не выделенный):
bg=#FFFFFF, text=#212121
РАМКА: цвет=BLACK, ширина=2dp, радиус=4dp

// Выделенный день:
bg=#90CAF9, text=#1976D2

// Обычный день:
bg=lighten(#FFFFFF, 0.3f), text=#212121
```

### Таблица задач (TasksAdapter):
```kotlin
// Заголовок:
textColor=#212121

// Активная задача:
bg=#FFEB3B (желтый), text=#212121

// Неактивная задача:
bg=#FFFFFF (белый), text=#212121
```

### Кнопки:
```kotlin
// Toggle кнопки (btnEditMode, btnPersonalMode):
drawable: R.drawable.bg_zoom_button
  - shape: rounded rectangle
  - cornerRadius: 20dp
  - solidColor: #673AB7 (Deep Purple)
  - ripple: #803F51B5
textColor: WHITE

// Кнопка календаря (btnToggleCalendar):
drawable: R.drawable.bg_circle_button
shape: OVAL
color: #673AB7
textColor: WHITE
```

### Фон:
```kotlin
background: #FAFAFA (светло-серый)
tvNow textColor: #000000
```

---

## ТЕМА 2: NUCLEAR (THEME_NUCLEAR = 1)

### Шкала часов:
```kotlin
// Выделенный:
selected: bg=#FF6B35 (оранжевый), text=#FFFFFF

// Активный диапазон:
08-13: bg=#2A68A9, text=#E4F4FF
14-19: bg=#3183D2, text=#E4F4FF
20-07: bg=#184472, text=#E4F4FF

// Неактивный:
08-13: bg=#1B4F82, text=#E4F4FF
14-19: bg=#256DB3, text=#E4F4FF
20-07: bg=#10355F, text=#E4F4FF
```

### Календарь:
```kotlin
// Будний день: bg=#49C9D4, text=#013349
// Суббота: bg=#34B2F2, text=#02243C
// Воскресенье: bg=#2C91E8, text=#011B33

// Сегодняшний день (не выделенный):
bg=(как у будни/выходные), text=(соответствующий)
РАМКА: цвет=#FF6B35 (оранжевый), ширина=2dp, радиус=4dp

// Выделенный: bg=#2FD2FF, text=#001E36
```

### Таблица задач:
```kotlin
// Заголовок: text=#003144

// Активная: bg=#0D2B51, text=из AppTheme
// Неактивная: bg=#1D5EA9, text=из AppTheme
```

### Фон:
```kotlin
background: градиент + атом (R.drawable.bg_atom_3d_realistic)
  или R.drawable.bg_nuclear
tvNow: textColor=AppTheme.getTextPrimaryColor()
```

---

## ТЕМА 3: РОСАТОМ (THEME_ROSATOM = 5)

### Шкала часов:
```kotlin
textDark = #003D5C

// Выделенный:
selected: bg=#FF6B35 (оранжевый), text=#FFFFFF

// Активный диапазон:
08-13: bg=#4FC3F7, text=#003D5C
14-19: bg=#29B6F6, text=#003D5C
20-07: bg=#0398D4 (темнее!), text=#003D5C

// Неактивный:
08-13: bg=#B3E5FC (светло-голубой), text=#003D5C
14-19: bg=#81D4FA (голубой), text=#003D5C
20-07: bg=#4DB6D5 (более темный - ночная смена), text=#003D5C
```

### Календарь:
```kotlin
// Будний день: bg=#B3E5FC, text=#003D5C
// Суббота: bg=#81D4FA, text=#003D5C
// Воскресенье: bg=#4FC3F7, text=#003D5C

// Сегодняшний день (не выделенный):
bg=(как у будни/выходные по дню недели), text=#003D5C
РАМКА: цвет=#FF6B35 (оранжевый), ширина=2dp, радиус=4dp

// Выделенный: bg=#0091D5, text=#FFFFFF
```

### Фон:
```kotlin
background: градиент + логотип Росатом
  градиент: TL_BR [#E3F2FD, #BBDEFB, #90CAF9]
  логотип: R.drawable.bg_rosatom_photo
tvNow: textColor=AppTheme.getTextPrimaryColor()
```

---

## ТЕМА 4: ЭРГОНОМИЧНАЯ (THEME_ERGONOMIC_LIGHT = 2)

### Шкала часов:
```kotlin
// Используется AppTheme.getCardBackgroundColor() и другие методы
// Нет хардкода цветов - все через AppTheme

primary = AppTheme.getPrimaryColor()
accent = AppTheme.getAccentColor()
textDefault = AppTheme.getTextPrimaryColor()

// Выделенный:
selectedBg = AppTheme.darken(AppTheme.getSelectedColor(), 0.3f)

// Активный:
morningBg = ColorUtils.blendARGB(primary, #4CAF50, 0.4f)
dayBg = ColorUtils.blendARGB(primary, accent, 0.5f)
nightBg = ColorUtils.blendARGB(primary, #607D8B, 0.3f)
```

### Календарь:
```kotlin
cardColor = AppTheme.getCardBackgroundColor()
textDefault = AppTheme.getTextPrimaryColor()
selectedBg = AppTheme.getSelectedColor()
normalBg = lighten(cardColor, 0.4f)

// Сегодняшний день:
РАМКА: цвет=#689F38 (зеленый), ширина=2dp, радиус=4dp
```

### Фон:
```kotlin
background: R.drawable.bg_ergonomic_light
tvNow: textColor=AppTheme.getTextPrimaryColor()
```

---

## ТЕМА 5: СТЕКЛЯННАЯ (THEME_MODERN_GLASS = 3)

### Шкала часов:
```kotlin
// Аналогично Эргономичной - через AppTheme методы
```

### Календарь:
```kotlin
// Аналогично Эргономичной - через AppTheme

// Сегодняшний день:
РАМКА: цвет=BLACK, ширина=2dp, радиус=4dp
```

### Фон:
```kotlin
background: R.drawable.bg_modern_glass
```

---

## ТЕМА 6: БРУТАЛЬНАЯ (THEME_MODERN_GRADIENT = 4)

### Шкала часов:
```kotlin
// Аналогично Эргономичной - через AppTheme
```

### Календарь:
```kotlin
// Аналогично Эргономичной - через AppTheme

// Сегодняшний день:
РАМКА: цвет=AppTheme.getAccentColor(), ширина=2dp, радиус=4dp
```

### Фон:
```kotlin
background: R.drawable.bg_modern_gradient
```

---

## ОБЩИЕ ПАРАМЕТРЫ (AppTheme.kt)

### Акцентные цвета (getAccentColor):
```kotlin
THEME_CLASSIC:          #2196F3 (синий)
THEME_NUCLEAR:          #00D8FF (циан)
THEME_ERGONOMIC_LIGHT:  #8BC34A (зеленый)
THEME_MODERN_GLASS:     #80DEEA (светлый циан)
THEME_MODERN_GRADIENT:  #FF4081 (розовый)
THEME_ROSATOM:          #FF6B35 (оранжевый)
```

### Радиус скругления (getCardCornerRadius):
```kotlin
THEME_CLASSIC:          0f
THEME_NUCLEAR:          8f
THEME_ERGONOMIC_LIGHT:  12f
THEME_MODERN_GLASS:     16f
THEME_MODERN_GRADIENT:  20f
THEME_ROSATOM:          4f
```

### Толщина обводки (getBorderWidth):
```kotlin
THEME_CLASSIC:          1f px
THEME_NUCLEAR:          3f px
THEME_ERGONOMIC_LIGHT:  1f px
THEME_MODERN_GLASS:     1f px
THEME_MODERN_GRADIENT:  0f px (без обводки)
THEME_ROSATOM:          2f px
```

---

## ДОКУМЕНТАЦИЯ СОХРАНЕНА
Все настройки тем зафиксированы для рефакторинга!


















