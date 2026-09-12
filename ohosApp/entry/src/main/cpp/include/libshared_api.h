#ifndef KONAN_LIBSHARED_H
#define KONAN_LIBSHARED_H
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
typedef bool            libshared_KBoolean;
#else
typedef _Bool           libshared_KBoolean;
#endif
typedef unsigned short     libshared_KChar;
typedef signed char        libshared_KByte;
typedef short              libshared_KShort;
typedef int                libshared_KInt;
typedef long long          libshared_KLong;
typedef unsigned char      libshared_KUByte;
typedef unsigned short     libshared_KUShort;
typedef unsigned int       libshared_KUInt;
typedef unsigned long long libshared_KULong;
typedef float              libshared_KFloat;
typedef double             libshared_KDouble;
typedef float __attribute__ ((__vector_size__ (16))) libshared_KVector128;
typedef void*              libshared_KNativePtr;
struct libshared_KType;
typedef struct libshared_KType libshared_KType;

typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Byte;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Short;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Int;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Long;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Float;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Double;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Char;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Boolean;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Unit;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_UByte;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_UShort;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_UInt;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_ULong;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_collections_List;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_AiConfig;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Any;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_AiPreset;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel_Companion;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult_Companion;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_tencent_kuikly_core_module_NetworkModule;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_StockQuote;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Function1;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Function2;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage_Companion;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_Conversation;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_LevelKind;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_LevelKind_SUPPORT;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_LevelKind_RESISTANCE;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_LevelKind_COST;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_LevelKind_Companion;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult_ADDED;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult_DUPLICATE;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult_FULL;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_LevelStore;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_StockCache;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_StockMeta;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_MinutePoint;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_KLineBar;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_Watchlist;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_collections_Map;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_data_StockRepository;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Function0;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_tencent_kuiklybase_config_MarkdownConfig;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ui_StockFormat;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_tencent_kuikly_core_base_Color;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ui_ThemePalettes;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_example_aistock_prices_stock_ui_ThemeMode;


typedef struct {
  /* Service functions. */
  void (*DisposeStablePointer)(libshared_KNativePtr ptr);
  void (*DisposeString)(const char* string);
  libshared_KBoolean (*IsInstance)(libshared_KNativePtr ref, const libshared_KType* type);
  libshared_kref_kotlin_Byte (*createNullableByte)(libshared_KByte);
  libshared_KByte (*getNonNullValueOfByte)(libshared_kref_kotlin_Byte);
  libshared_kref_kotlin_Short (*createNullableShort)(libshared_KShort);
  libshared_KShort (*getNonNullValueOfShort)(libshared_kref_kotlin_Short);
  libshared_kref_kotlin_Int (*createNullableInt)(libshared_KInt);
  libshared_KInt (*getNonNullValueOfInt)(libshared_kref_kotlin_Int);
  libshared_kref_kotlin_Long (*createNullableLong)(libshared_KLong);
  libshared_KLong (*getNonNullValueOfLong)(libshared_kref_kotlin_Long);
  libshared_kref_kotlin_Float (*createNullableFloat)(libshared_KFloat);
  libshared_KFloat (*getNonNullValueOfFloat)(libshared_kref_kotlin_Float);
  libshared_kref_kotlin_Double (*createNullableDouble)(libshared_KDouble);
  libshared_KDouble (*getNonNullValueOfDouble)(libshared_kref_kotlin_Double);
  libshared_kref_kotlin_Char (*createNullableChar)(libshared_KChar);
  libshared_KChar (*getNonNullValueOfChar)(libshared_kref_kotlin_Char);
  libshared_kref_kotlin_Boolean (*createNullableBoolean)(libshared_KBoolean);
  libshared_KBoolean (*getNonNullValueOfBoolean)(libshared_kref_kotlin_Boolean);
  libshared_kref_kotlin_Unit (*createNullableUnit)(void);
  libshared_kref_kotlin_UByte (*createNullableUByte)(libshared_KUByte);
  libshared_KUByte (*getNonNullValueOfUByte)(libshared_kref_kotlin_UByte);
  libshared_kref_kotlin_UShort (*createNullableUShort)(libshared_KUShort);
  libshared_KUShort (*getNonNullValueOfUShort)(libshared_kref_kotlin_UShort);
  libshared_kref_kotlin_UInt (*createNullableUInt)(libshared_KUInt);
  libshared_KUInt (*getNonNullValueOfUInt)(libshared_kref_kotlin_UInt);
  libshared_kref_kotlin_ULong (*createNullableULong)(libshared_KULong);
  libshared_KULong (*getNonNullValueOfULong)(libshared_kref_kotlin_ULong);

  /* User functions. */
  struct {
    struct {
      struct {
        struct {
          struct {
            struct {
              struct {
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiConfig (*AiConfig)(const char* baseUrl, const char* apiKey, const char* model);
                  const char* (*get_apiKey)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  const char* (*get_baseUrl)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  libshared_KBoolean (*get_isConfigured)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  const char* (*get_model)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiConfig (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz, const char* baseUrl, const char* apiKey, const char* model);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_AiConfig thiz);
                } AiConfig;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiPreset (*AiPreset)(const char* name, const char* baseUrl, const char* apiKey, const char* model, libshared_KBoolean enabled, libshared_KBoolean failed);
                  const char* (*get_apiKey)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*get_baseUrl)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  libshared_KBoolean (*get_enabled)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  libshared_KBoolean (*get_failed)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*get_model)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*get_name)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*component4)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  libshared_KBoolean (*component5)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  libshared_KBoolean (*component6)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiPreset (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz, const char* name, const char* baseUrl, const char* apiKey, const char* model, libshared_KBoolean enabled, libshared_KBoolean failed);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_AiPreset thiz);
                } AiPreset;
                struct {
                  struct {
                    libshared_KType* (*_type)(void);
                    libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel_Companion (*_instance)();
                    const char* (*get_TYPE_RESISTANCE)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel_Companion thiz);
                    const char* (*get_TYPE_SUPPORT)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel_Companion thiz);
                  } Companion;
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel (*KeyLevel)(libshared_KDouble price, const char* type, const char* label, const char* desc);
                  const char* (*get_desc)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  const char* (*get_label)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  libshared_KDouble (*get_price)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  const char* (*get_type)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  libshared_KDouble (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  const char* (*component4)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz, libshared_KDouble price, const char* type, const char* label, const char* desc);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_KeyLevel thiz);
                } KeyLevel;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight (*MetricInsight)(const char* key, const char* name, const char* comment);
                  const char* (*get_comment)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                  const char* (*get_key)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                  const char* (*get_name)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz, const char* key, const char* name, const char* comment);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_MetricInsight thiz);
                } MetricInsight;
                struct {
                  struct {
                    libshared_KType* (*_type)(void);
                    libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult_Companion (*_instance)();
                    const char* (*get_RISK_HIGH)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult_Companion thiz);
                    const char* (*get_RISK_LOW)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult_Companion thiz);
                    const char* (*get_RISK_MID)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult_Companion thiz);
                    const char* (*get_SOURCE_AI)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult_Companion thiz);
                    const char* (*get_SOURCE_RULE)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult_Companion thiz);
                  } Companion;
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult (*AiAnalysisResult)(const char* trend, const char* trendDesc, const char* suggestion, libshared_kref_kotlin_collections_List buyPoints, libshared_kref_kotlin_collections_List sellPoints, libshared_kref_kotlin_collections_List risks, const char* summary, const char* riskLevel, const char* source, const char* markdown, libshared_kref_kotlin_collections_List keyLevels, libshared_kref_kotlin_collections_List metrics);
                  libshared_kref_kotlin_collections_List (*get_buyPoints)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*get_keyLevels)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*get_markdown)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*get_metrics)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*get_riskLevel)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*get_risks)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*get_sellPoints)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*get_source)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*get_suggestion)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*get_summary)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*get_trend)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*get_trendDesc)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*component10)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*component11)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*component12)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*component4)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*component5)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_kotlin_collections_List (*component6)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*component7)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*component8)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*component9)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz, const char* trend, const char* trendDesc, const char* suggestion, libshared_kref_kotlin_collections_List buyPoints, libshared_kref_kotlin_collections_List sellPoints, libshared_kref_kotlin_collections_List risks, const char* summary, const char* riskLevel, const char* source, const char* markdown, libshared_kref_kotlin_collections_List keyLevels, libshared_kref_kotlin_collections_List metrics);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult thiz);
                } AiAnalysisResult;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService (*_instance)();
                  libshared_kref_com_example_aistock_prices_stock_ai_AiPreset (*activePreset)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  const char* (*activePresetName)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  void (*analyze)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_NetworkModule network, libshared_kref_com_example_aistock_prices_stock_ai_AiConfig config, libshared_kref_com_example_aistock_prices_stock_data_StockQuote quote, libshared_kref_kotlin_collections_List minute, libshared_kref_kotlin_collections_List kline, libshared_kref_kotlin_Function1 callback);
                  void (*chat)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_NetworkModule network, libshared_kref_com_example_aistock_prices_stock_ai_AiConfig config, libshared_kref_kotlin_collections_List history, libshared_kref_kotlin_Function2 callback);
                  const char* (*chatUrl)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, const char* base);
                  void (*fetchModels)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_NetworkModule network, const char* baseUrl, const char* apiKey, libshared_kref_kotlin_Function2 callback);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiConfig (*loadConfig)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  libshared_kref_kotlin_collections_List (*loadPresets)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisResult (*localFallback)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_example_aistock_prices_stock_data_StockQuote quote, libshared_kref_kotlin_collections_List kline);
                  const char* (*modelsUrl)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, const char* base);
                  void (*removePreset)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, libshared_kref_com_example_aistock_prices_stock_ai_AiPreset preset);
                  void (*saveConfig)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, libshared_kref_com_example_aistock_prices_stock_ai_AiConfig config);
                  void (*setActivePreset)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* name);
                  void (*setPresetEnabled)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* name, libshared_KBoolean enabled);
                  void (*setPresetFailed)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* name, libshared_KBoolean failed);
                  void (*testConnection)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_NetworkModule network, const char* baseUrl, const char* apiKey, libshared_kref_kotlin_Function2 callback);
                  void (*updatePreset)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* oldName, libshared_kref_com_example_aistock_prices_stock_ai_AiPreset newPreset);
                  void (*upsertPreset)(libshared_kref_com_example_aistock_prices_stock_ai_AiAnalysisService thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, libshared_kref_com_example_aistock_prices_stock_ai_AiConfig config, const char* name, libshared_KBoolean enabled);
                } AiAnalysisService;
                struct {
                  struct {
                    libshared_KType* (*_type)(void);
                    libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage_Companion (*_instance)();
                    const char* (*get_ROLE_ASSISTANT)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage_Companion thiz);
                    const char* (*get_ROLE_CONTEXT)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage_Companion thiz);
                    const char* (*get_ROLE_USER)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage_Companion thiz);
                  } Companion;
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage (*ChatMessage)(const char* role, const char* content, libshared_KLong ts, libshared_KBoolean error);
                  const char* (*get_content)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  libshared_KBoolean (*get_error)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  const char* (*get_role)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  libshared_KLong (*get_ts)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  libshared_KLong (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  libshared_KBoolean (*component4)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz, const char* role, const char* content, libshared_KLong ts, libshared_KBoolean error);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_ChatMessage thiz);
                } ChatMessage;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_Conversation (*Conversation)(const char* id, const char* title, const char* stockCode, const char* stockName, libshared_kref_kotlin_collections_List messages, libshared_KLong createdAt);
                  libshared_KLong (*get_createdAt)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*get_displayName)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*get_id)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  libshared_kref_kotlin_collections_List (*get_messages)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*get_stockCode)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*get_stockName)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*get_title)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*component4)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  libshared_kref_kotlin_collections_List (*component5)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  libshared_KLong (*component6)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_Conversation (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz, const char* id, const char* title, const char* stockCode, const char* stockName, libshared_kref_kotlin_collections_List messages, libshared_KLong createdAt);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_Conversation thiz);
                } Conversation;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment (*ChatSegment)(const char* type, const char* text, const char* code, const char* name);
                  const char* (*get_code)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*get_name)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*get_text)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*get_type)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*component4)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment (*copy)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz, const char* type, const char* text, const char* code, const char* name);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_ai_ChatSegment thiz);
                } ChatSegment;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore (*_instance)();
                  void (*delete_)(libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* id);
                  libshared_kref_com_example_aistock_prices_stock_ai_Conversation (*findOrCreateForStock)(libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code, const char* name);
                  libshared_kref_kotlin_collections_List (*load)(libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  libshared_kref_com_example_aistock_prices_stock_ai_Conversation (*newConversation)(libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  const char* (*newId)(libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore thiz);
                  void (*save)(libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, libshared_kref_kotlin_collections_List list);
                  void (*update)(libshared_kref_com_example_aistock_prices_stock_ai_ConversationStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, libshared_kref_com_example_aistock_prices_stock_ai_Conversation conv);
                } ConversationStore;
                libshared_kref_kotlin_collections_List (*parseChatSegments)(const char* content);
                const char* (*stripContextSuffix)(const char* content);
              } ai;
              struct {
                struct {
                  struct {
                    libshared_kref_com_example_aistock_prices_stock_data_LevelKind (*get)(); /* enum entry for SUPPORT. */
                  } SUPPORT;
                  struct {
                    libshared_kref_com_example_aistock_prices_stock_data_LevelKind (*get)(); /* enum entry for RESISTANCE. */
                  } RESISTANCE;
                  struct {
                    libshared_kref_com_example_aistock_prices_stock_data_LevelKind (*get)(); /* enum entry for COST. */
                  } COST;
                  struct {
                    libshared_KType* (*_type)(void);
                    libshared_kref_com_example_aistock_prices_stock_data_LevelKind_Companion (*_instance)();
                    libshared_kref_com_example_aistock_prices_stock_data_LevelKind (*of)(libshared_kref_com_example_aistock_prices_stock_data_LevelKind_Companion thiz, const char* name);
                  } Companion;
                  libshared_KType* (*_type)(void);
                  const char* (*get_label)(libshared_kref_com_example_aistock_prices_stock_data_LevelKind thiz);
                } LevelKind;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark (*KeyLevelMark)(libshared_KDouble price, libshared_kref_com_example_aistock_prices_stock_data_LevelKind kind, const char* note);
                  libshared_kref_com_example_aistock_prices_stock_data_LevelKind (*get_kind)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                  const char* (*get_note)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                  libshared_KDouble (*get_price)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                  libshared_KDouble (*component1)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                  libshared_kref_com_example_aistock_prices_stock_data_LevelKind (*component2)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                  const char* (*component3)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                  libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark (*copy)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz, libshared_KDouble price, libshared_kref_com_example_aistock_prices_stock_data_LevelKind kind, const char* note);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark thiz);
                } KeyLevelMark;
                struct {
                  struct {
                    libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult (*get)(); /* enum entry for ADDED. */
                  } ADDED;
                  struct {
                    libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult (*get)(); /* enum entry for DUPLICATE. */
                  } DUPLICATE;
                  struct {
                    libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult (*get)(); /* enum entry for FULL. */
                  } FULL;
                  libshared_KType* (*_type)(void);
                } AddLevelResult;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_LevelStore (*_instance)();
                  libshared_kref_com_example_aistock_prices_stock_data_AddLevelResult (*add)(libshared_kref_com_example_aistock_prices_stock_data_LevelStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code, libshared_kref_com_example_aistock_prices_stock_data_KeyLevelMark mark);
                  void (*clear)(libshared_kref_com_example_aistock_prices_stock_data_LevelStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code);
                  libshared_KInt (*count)(libshared_kref_com_example_aistock_prices_stock_data_LevelStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code);
                  libshared_kref_kotlin_collections_List (*levels)(libshared_kref_com_example_aistock_prices_stock_data_LevelStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code);
                  libshared_KBoolean (*remove)(libshared_kref_com_example_aistock_prices_stock_data_LevelStore thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code, libshared_KDouble price, libshared_kref_com_example_aistock_prices_stock_data_LevelKind kind);
                } LevelStore;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_StockCache (*_instance)();
                  libshared_kref_kotlin_collections_List (*loadKLine)(libshared_kref_com_example_aistock_prices_stock_data_StockCache thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code);
                  libshared_kref_kotlin_collections_List (*loadMinute)(libshared_kref_com_example_aistock_prices_stock_data_StockCache thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code);
                  libshared_kref_kotlin_collections_List (*loadQuotes)(libshared_kref_com_example_aistock_prices_stock_data_StockCache thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  void (*saveKLine)(libshared_kref_com_example_aistock_prices_stock_data_StockCache thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code, libshared_kref_kotlin_collections_List bars);
                  void (*saveMinute)(libshared_kref_com_example_aistock_prices_stock_data_StockCache thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code, libshared_kref_kotlin_collections_List points);
                  void (*saveQuotes)(libshared_kref_com_example_aistock_prices_stock_data_StockCache thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, libshared_kref_kotlin_collections_List list);
                } StockCache;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_StockMeta (*StockMeta)(const char* code, const char* name, libshared_KBoolean pinned, const char* group);
                  const char* (*get_code)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  const char* (*get_group)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  const char* (*get_name)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  libshared_KBoolean (*get_pinned)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  const char* (*get_symbol)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  libshared_KBoolean (*component3)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  const char* (*component4)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  libshared_kref_com_example_aistock_prices_stock_data_StockMeta (*copy)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz, const char* code, const char* name, libshared_KBoolean pinned, const char* group);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_data_StockMeta thiz);
                } StockMeta;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_StockQuote (*StockQuote)(const char* code, const char* name, libshared_KDouble price, libshared_KDouble prevClose, libshared_KDouble open, libshared_KDouble high, libshared_KDouble low, libshared_KDouble change, libshared_KDouble changePercent, libshared_KLong volume, libshared_KDouble amount, libshared_KDouble turnover, libshared_KDouble amplitude, libshared_KDouble avgPrice, const char* time);
                  libshared_KDouble (*get_amount)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_amplitude)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_avgPrice)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_change)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_changePercent)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*get_code)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_high)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_low)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*get_name)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_open)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_prevClose)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_price)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*get_symbol)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*get_time)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*get_turnover)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KLong (*get_volume)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KLong (*component10)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component11)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component12)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component13)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component14)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*component15)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*component2)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component3)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component4)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component5)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component6)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component7)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component8)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_KDouble (*component9)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  libshared_kref_com_example_aistock_prices_stock_data_StockQuote (*copy)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz, const char* code, const char* name, libshared_KDouble price, libshared_KDouble prevClose, libshared_KDouble open, libshared_KDouble high, libshared_KDouble low, libshared_KDouble change, libshared_KDouble changePercent, libshared_KLong volume, libshared_KDouble amount, libshared_KDouble turnover, libshared_KDouble amplitude, libshared_KDouble avgPrice, const char* time);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_data_StockQuote thiz);
                } StockQuote;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_MinutePoint (*MinutePoint)(const char* time, libshared_KDouble price, libshared_KLong volume, libshared_KDouble amount);
                  libshared_KDouble (*get_amount)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  libshared_KDouble (*get_price)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  const char* (*get_time)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  libshared_KLong (*get_volume)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  libshared_KDouble (*component2)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  libshared_KLong (*component3)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  libshared_KDouble (*component4)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  libshared_kref_com_example_aistock_prices_stock_data_MinutePoint (*copy)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz, const char* time, libshared_KDouble price, libshared_KLong volume, libshared_KDouble amount);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_data_MinutePoint thiz);
                } MinutePoint;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_KLineBar (*KLineBar)(const char* date, libshared_KDouble open, libshared_KDouble close, libshared_KDouble high, libshared_KDouble low, libshared_KLong volume);
                  libshared_KDouble (*get_close)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  const char* (*get_date)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KDouble (*get_high)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KDouble (*get_low)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KDouble (*get_open)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KLong (*get_volume)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  const char* (*component1)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KDouble (*component2)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KDouble (*component3)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KDouble (*component4)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KDouble (*component5)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_KLong (*component6)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  libshared_kref_com_example_aistock_prices_stock_data_KLineBar (*copy)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz, const char* date, libshared_KDouble open, libshared_KDouble close, libshared_KDouble high, libshared_KDouble low, libshared_KLong volume);
                  libshared_KBoolean (*equals)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz, libshared_kref_kotlin_Any other);
                  libshared_KInt (*hashCode)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                  const char* (*toString)(libshared_kref_com_example_aistock_prices_stock_data_KLineBar thiz);
                } KLineBar;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_Watchlist (*_instance)();
                  const char* (*get_GROUP_ALL)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz);
                  const char* (*get_GROUP_NONE_LABEL)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz);
                  libshared_KInt (*get_MAX_GROUPS)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz);
                  libshared_KInt (*get_MAX_GROUP_NAME)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz);
                  libshared_KBoolean (*add)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, libshared_kref_com_example_aistock_prices_stock_data_StockMeta meta);
                  libshared_KBoolean (*addGroup)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* name);
                  libshared_kref_kotlin_collections_Map (*groupCounts)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  libshared_kref_kotlin_collections_List (*groups)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                  libshared_KBoolean (*isPinned)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code);
                  libshared_KBoolean (*moveGroup)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* name, libshared_KInt delta);
                  libshared_KBoolean (*moveToGroup)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code, const char* group);
                  const char* (*nameOf)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, const char* code);
                  const char* (*normalizeCode)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, const char* input);
                  const char* (*normalizeGroupName)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, const char* raw);
                  libshared_KBoolean (*pin)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code, libshared_KBoolean pinned);
                  libshared_KBoolean (*remove)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* code);
                  libshared_KBoolean (*removeGroup)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* name);
                  libshared_KBoolean (*renameGroup)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* from, const char* to);
                  libshared_kref_kotlin_collections_List (*stocks)(libshared_kref_com_example_aistock_prices_stock_data_Watchlist thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                } Watchlist;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_data_StockRepository (*_instance)();
                  void (*fetchKLine)(libshared_kref_com_example_aistock_prices_stock_data_StockRepository thiz, libshared_kref_com_tencent_kuikly_core_module_NetworkModule network, const char* code, libshared_KInt count, libshared_kref_kotlin_Function1 callback);
                  void (*fetchMinute)(libshared_kref_com_example_aistock_prices_stock_data_StockRepository thiz, libshared_kref_com_tencent_kuikly_core_module_NetworkModule network, const char* code, libshared_kref_kotlin_Function1 callback);
                  void (*fetchQuotes)(libshared_kref_com_example_aistock_prices_stock_data_StockRepository thiz, libshared_kref_com_tencent_kuikly_core_module_NetworkModule network, libshared_kref_kotlin_collections_List codes, libshared_kref_kotlin_Function1 callback);
                } StockRepository;
              } data;
              struct {
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips (*_instance)();
                  const char* (*get_AI_MSG)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz);
                  libshared_kref_kotlin_collections_List (*get_ALL)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz);
                  const char* (*get_CHART)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz);
                  const char* (*get_GROUP)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz);
                  const char* (*get_SORT)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz);
                  libshared_KBoolean (*isSeen)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* key);
                  void (*markSeen)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp, const char* key);
                  void (*resetAll)(libshared_kref_com_example_aistock_prices_stock_ui_FeatureTips thiz, libshared_kref_com_tencent_kuikly_core_module_SharedPreferencesModule sp);
                } FeatureTips;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ui_StockFormat (*_instance)();
                  const char* (*amount)(libshared_kref_com_example_aistock_prices_stock_ui_StockFormat thiz, libshared_KDouble wan);
                  const char* (*change)(libshared_kref_com_example_aistock_prices_stock_ui_StockFormat thiz, libshared_KDouble v);
                  const char* (*percent)(libshared_kref_com_example_aistock_prices_stock_ui_StockFormat thiz, libshared_KDouble v);
                  const char* (*price)(libshared_kref_com_example_aistock_prices_stock_ui_StockFormat thiz, libshared_KDouble v);
                  const char* (*volume)(libshared_kref_com_example_aistock_prices_stock_ui_StockFormat thiz, libshared_KLong hands);
                } StockFormat;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette (*ThemePalette)(libshared_kref_com_tencent_kuikly_core_base_Color up, libshared_kref_com_tencent_kuikly_core_base_Color down, libshared_kref_com_tencent_kuikly_core_base_Color flat, libshared_kref_com_tencent_kuikly_core_base_Color accent, libshared_kref_com_tencent_kuikly_core_base_Color onAccent, libshared_kref_com_tencent_kuikly_core_base_Color bgPage, libshared_kref_com_tencent_kuikly_core_base_Color card, libshared_kref_com_tencent_kuikly_core_base_Color chipBg, libshared_kref_com_tencent_kuikly_core_base_Color chip2Bg, libshared_kref_com_tencent_kuikly_core_base_Color accentChipBg, libshared_kref_com_tencent_kuikly_core_base_Color textMain, libshared_kref_com_tencent_kuikly_core_base_Color textSub, libshared_kref_com_tencent_kuikly_core_base_Color divider, libshared_kref_com_tencent_kuikly_core_base_Color stopRed, libshared_kref_com_tencent_kuikly_core_base_Color errRed, libshared_kref_com_tencent_kuikly_core_base_Color warnBg, libshared_kref_com_tencent_kuikly_core_base_Color warnBorder, libshared_kref_com_tencent_kuikly_core_base_Color warnText, libshared_kref_com_tencent_kuikly_core_base_Color errBg, libshared_kref_com_tencent_kuikly_core_base_Color maskDim, libshared_kref_com_tencent_kuikly_core_base_Color maskFull);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_accent)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_accentChipBg)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_bgPage)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_card)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_chip2Bg)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_chipBg)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_divider)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_down)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_errBg)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_errRed)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_flat)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_maskDim)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_maskFull)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_onAccent)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_stopRed)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_textMain)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_textSub)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_up)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_warnBg)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_warnBorder)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*get_warnText)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz);
                  libshared_kref_com_tencent_kuikly_core_base_Color (*ofChange)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette thiz, libshared_KDouble v);
                } ThemePalette;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ui_ThemePalettes (*_instance)();
                  libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette (*get_dark)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalettes thiz);
                  libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette (*get_light)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalettes thiz);
                  libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette (*of)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalettes thiz, libshared_KBoolean night);
                } ThemePalettes;
                struct {
                  libshared_KType* (*_type)(void);
                  libshared_kref_com_example_aistock_prices_stock_ui_ThemeMode (*_instance)();
                  libshared_KInt (*get_AUTO)(libshared_kref_com_example_aistock_prices_stock_ui_ThemeMode thiz);
                  libshared_KInt (*get_DARK)(libshared_kref_com_example_aistock_prices_stock_ui_ThemeMode thiz);
                  libshared_KInt (*get_LIGHT)(libshared_kref_com_example_aistock_prices_stock_ui_ThemeMode thiz);
                  const char* (*label)(libshared_kref_com_example_aistock_prices_stock_ui_ThemeMode thiz, libshared_KInt mode);
                  libshared_KInt (*next)(libshared_kref_com_example_aistock_prices_stock_ui_ThemeMode thiz, libshared_KInt mode);
                } ThemeMode;
                libshared_kref_kotlin_Function1 (*featureTipBar)(libshared_kref_com_example_aistock_prices_stock_ui_ThemePalette pal, const char* text, libshared_kref_kotlin_Function0 onClose);
                libshared_kref_com_tencent_kuiklybase_config_MarkdownConfig (*markdownConfig)(libshared_KBoolean night);
              } ui;
            } stock;
          } aistock_prices;
        } example;
      } com;
      libshared_KInt (*initKuikly)();
    } root;
  } kotlin;
} libshared_ExportedSymbols;
extern libshared_ExportedSymbols* libshared_symbols(void);
#ifdef __cplusplus
}  /* extern "C" */
#endif
#endif  /* KONAN_LIBSHARED_H */
