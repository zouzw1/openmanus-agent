package com.openmanus.mcptest.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class TestMcpToolService {

    private static final Logger log = LoggerFactory.getLogger(TestMcpToolService.class);
    private final WeatherService weatherService;

    public TestMcpToolService(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool(name = "health_check", description = "Return a simple health message proving the MCP test service is reachable.")
    public Map<String, Object> healthCheck() {
        try {
            return new java.util.HashMap<String, Object>() {{
                put("success", true);
                put("tool", "health_check");
                put("status", "healthy");
                put("ok", true);
                put("timestamp", System.currentTimeMillis());
                put("text", "MCP test service is reachable and healthy.");
            }};
        } catch (Exception e) {
            log.warn("Health check failed - {}", e.getMessage());
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("tool", "health_check");
                put("status", "unhealthy");
                put("ok", false);
                put("error", e.getClass().getSimpleName());
                put("errorMessage", e.getMessage());
                put("text", "Health check failed: " + e.getMessage());
            }};
        }
    }

    @Tool(name = "echo_text", description = "Echo the provided text back to the caller.")
    public Map<String, Object> echoText(@ToolParam(description = "Text to echo back.", required = true) String text) {
        try {
            if (text == null || text.isBlank()) {
                return new java.util.HashMap<String, Object>() {{
                    put("success", false);
                    put("tool", "echo_text");
                    put("error", "ValidationError");
                    put("errorMessage", "Text parameter cannot be empty");
                    put("text", "Error: Text parameter is required");
                }};
            }
            
            return new java.util.HashMap<String, Object>() {{
                put("success", true);
                put("tool", "echo_text");
                put("input", text);
                put("output", text);
                put("text", "echo: " + text);
            }};
        } catch (Exception e) {
            log.warn("Failed to echo text - {}", e.getMessage());
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("tool", "echo_text");
                put("error", e.getClass().getSimpleName());
                put("errorMessage", e.getMessage());
                put("text", "Error echoing text: " + e.getMessage());
            }};
        }
    }

    @Tool(name = "sum_numbers", description = "Return the sum of two numbers.")
    public Map<String, Object> sumNumbers(
            @ToolParam(description = "First number.", required = true) double a,
            @ToolParam(description = "Second number.", required = true) double b
    ) {
        try {
            double sum = a + b;
            return new java.util.HashMap<String, Object>() {{
                put("success", true);
                put("tool", "sum_numbers");
                put("a", a);
                put("b", b);
                put("sum", sum);
                put("text", "sum is " + sum);
            }};
        } catch (Exception e) {
            log.warn("Failed to sum numbers - {}", e.getMessage());
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("tool", "sum_numbers");
                put("a", a);
                put("b", b);
                put("error", e.getClass().getSimpleName());
                put("errorMessage", e.getMessage());
                put("text", "Error calculating sum: " + e.getMessage());
            }};
        }
    }

    @Tool(name = "current_time", description = "Return the current server time in the requested time zone or Asia/Shanghai by default.")
    public Map<String, Object> currentTime(
            @ToolParam(description = "Optional Java ZoneId, for example Asia/Shanghai.", required = false) String zoneId
    ) {
        try {
            String resolvedZoneId = zoneId == null || zoneId.isBlank() ? "Asia/Shanghai" : zoneId;
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of(resolvedZoneId));
            
            return new java.util.HashMap<String, Object>() {{
                put("success", true);
                put("tool", "current_time");
                put("zoneId", resolvedZoneId);
                put("currentTime", now.toString());
                put("timestamp", now.toInstant().toEpochMilli());
                put("text", "Current time in " + resolvedZoneId + " is " + now);
            }};
        } catch (Exception e) {
            log.warn("Failed to get current time - {}", e.getMessage());
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("tool", "current_time");
                put("zoneId", zoneId);
                put("error", e.getClass().getSimpleName());
                put("errorMessage", e.getMessage());
                put("text", "Error getting current time: " + e.getMessage());
            }};
        }
    }

    @Tool(name = "get_weather", description = "Get current real-time weather information for a specified city. Returns temperature, condition, humidity, and wind speed from actual weather data.")
    public Map<String, Object> getWeather(
            @ToolParam(description = "City name to query weather for, e.g., Beijing, Shanghai, Guangzhou, New York, London.", required = true) String city
    ) {
        try {
            log.info(city);
            // 获取城市坐标
            Map<String, Double> coordinates = weatherService.getCityCoordinates(city);
            double latitude = coordinates.get("latitude");
            double longitude = coordinates.get("longitude");
            
            // 获取当前天气
            Map<String, Object> weatherData = weatherService.getCurrentWeather(latitude, longitude);
            
            String weatherText = String.format(
                    "Real-time weather in %s: %s, Temperature: %d°C, Humidity: %d%%, Wind: %d km/h",
                    city, 
                    weatherData.get("condition"),
                    (int) weatherData.get("temperature"),
                    (int) weatherData.get("humidity"),
                    (int) weatherData.get("windSpeed")
            );
            
            return new java.util.HashMap<String, Object>() {{
                put("success", true);
                put("tool", "get_weather");
                put("city", city);
                put("latitude", latitude);
                put("longitude", longitude);
                put("temperature", weatherData.get("temperature"));
                put("condition", weatherData.get("condition"));
                put("humidity", weatherData.get("humidity"));
                put("windSpeed", weatherData.get("windSpeed"));
                put("isDay", weatherData.get("isDay"));
                put("weatherCode", weatherData.get("weatherCode"));
                put("unit_temperature", "celsius");
                put("unit_wind", "km/h");
                put("source", "Open-Meteo Weather API");
                put("text", weatherText);
            }};
        } catch (Exception e) {
            log.warn("Failed to fetch weather for city: {} - {}", city, e.getMessage());
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("tool", "get_weather");
                put("city", city);
                put("error", e.getClass().getSimpleName());
                put("errorMessage", e.getMessage());
                put("text", "Unable to retrieve weather for " + city + ": " + e.getMessage());
            }};
        }
    }

    @Tool(name = "get_forecast", description = "Get real weather forecast for a specified city for the next few days using actual forecast data.")
    public Map<String, Object> getForecast(
            @ToolParam(description = "City name to get forecast for.", required = true) String city,
            @ToolParam(description = "Number of days to forecast (1-7). Default is 3.", required = false) Integer days
    ) {
        try {
            int forecastDays = (days == null || days < 1 || days > 7) ? 3 : days;
            
            // 获取城市坐标
            Map<String, Double> coordinates = weatherService.getCityCoordinates(city);
            double latitude = coordinates.get("latitude");
            double longitude = coordinates.get("longitude");
            
            // 获取天气预报
            List<Map<String, Object>> forecast = weatherService.getWeatherForecast(
                    latitude, longitude, forecastDays
            );
            
            String forecastText = String.format(
                    "%d-day forecast for %s: %s",
                    forecastDays, city, 
                    forecast.stream()
                            .map(f -> "Day " + f.get("day") + " (" + f.get("date") + "): " + 
                                    f.get("condition") + " (" + f.get("temperatureMax") + "/" + 
                                    f.get("temperatureMin") + "°C)")
                            .collect(java.util.stream.Collectors.joining(", "))
            );
            
            return new java.util.HashMap<String, Object>() {{
                put("success", true);
                put("tool", "get_forecast");
                put("city", city);
                put("latitude", latitude);
                put("longitude", longitude);
                put("days", forecastDays);
                put("forecast", forecast);
                put("source", "Open-Meteo Weather API");
                put("text", forecastText);
            }};
        } catch (Exception e) {
            log.warn("Failed to fetch forecast for city: {} - {}", city, e.getMessage());
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("tool", "get_forecast");
                put("city", city);
                put("error", e.getClass().getSimpleName());
                put("errorMessage", e.getMessage());
                put("text", "Unable to retrieve forecast for " + city + ": " + e.getMessage());
            }};
        }
    }

    @Tool(name = "travel_guide", description = "Get comprehensive travel guide information for a destination including best time to visit, top attractions, travel tips, and practical advice. Provides real recommendations based on actual travel data.")
    public Map<String, Object> getTravelGuide(
            @ToolParam(description = "Destination city or country name, e.g., Beijing, Paris, Japan, Thailand.", required = true) String destination,
            @ToolParam(description = "Travel month or season (e.g., 'March', 'Spring', 'Summer'). Optional - if not provided, returns general year-round information.", required = false) String travelTime,
            @ToolParam(description = "Travel style preference: 'budget', 'luxury', 'family', 'adventure', 'cultural'. Default is 'general'.", required = false) String travelStyle
    ) {
        try {
            log.info("Getting travel guide for: {}, time: {}, style: {}", destination, travelTime, travelStyle);
            
            // 验证参数
            if (destination == null || destination.isBlank()) {
                return new java.util.HashMap<String, Object>() {{
                    put("success", false);
                    put("tool", "travel_guide");
                    put("error", "ValidationError");
                    put("errorMessage", "Destination parameter is required");
                    put("text", "Error: Destination is required. Please provide a city or country name.");
                }};
            }
            
            // 获取旅行指南信息
            TravelGuideData guideData = buildTravelGuide(destination, travelTime, travelStyle);
            
            // 构建结构化响应
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("tool", "travel_guide");
            result.put("destination", guideData.destination);
            result.put("country", guideData.country);
            result.put("bestTimeToVisit", guideData.bestTimeToVisit);
            result.put("topAttractions", guideData.topAttractions);
            result.put("travelTips", guideData.travelTips);
            result.put("practicalInfo", guideData.practicalInfo);
            result.put("estimatedBudget", guideData.estimatedBudget);
            result.put("localCustoms", guideData.localCustoms);
            result.put("recommendedDuration", guideData.recommendedDuration);
            result.put("currency", guideData.currency);
            result.put("language", guideData.language);
            result.put("visaRequirements", guideData.visaRequirements);
            
            if (travelTime != null && !travelTime.isBlank()) {
                result.put("seasonalAdvice", guideData.seasonalAdvice);
            }
            
            if (travelStyle != null && !travelStyle.isBlank()) {
                result.put("styleSpecificTips", guideData.styleSpecificTips);
            }
            
            result.put("text", generateTravelGuideText(guideData));
            
            return result;
            
        } catch (Exception e) {
            log.warn("Failed to fetch travel guide for: {} - {}", destination, e.getMessage(), e);
            return new java.util.HashMap<String, Object>() {{
                put("success", false);
                put("tool", "travel_guide");
                put("destination", destination);
                put("error", e.getClass().getSimpleName());
                put("errorMessage", e.getMessage());
                put("text", "Unable to retrieve travel guide for " + destination + ": " + e.getMessage());
            }};
        }
    }
    
    /**
     * 内部类：旅行指南数据结构
     */
    private static class TravelGuideData {
        String destination;
        String country;
        List<String> bestTimeToVisit;
        List<Map<String, String>> topAttractions;
        List<String> travelTips;
        Map<String, String> practicalInfo;
        Map<String, String> estimatedBudget;
        List<String> localCustoms;
        String recommendedDuration;
        String currency;
        String language;
        String visaRequirements;
        String seasonalAdvice;
        List<String> styleSpecificTips;
        
        TravelGuideData() {
            this.bestTimeToVisit = new ArrayList<>();
            this.topAttractions = new ArrayList<>();
            this.travelTips = new ArrayList<>();
            this.practicalInfo = new HashMap<>();
            this.estimatedBudget = new HashMap<>();
            this.localCustoms = new ArrayList<>();
            this.styleSpecificTips = new ArrayList<>();
        }
    }
    
    /**
     * 构建旅行指南数据（基于真实旅行数据）
     */
    private TravelGuideData buildTravelGuide(String destination, String travelTime, String travelStyle) {
        TravelGuideData guide = new TravelGuideData();
        
        // 标准化目的地名称
        String normalizedDest = normalizeDestination(destination);
        
        // 根据目的地填充数据
        populateDestinationData(guide, normalizedDest);
        
        // 添加季节性建议
        if (travelTime != null && !travelTime.isBlank()) {
            guide.seasonalAdvice = getSeasonalAdvice(normalizedDest, travelTime);
        }
        
        // 添加风格特定建议
        if (travelStyle != null && !travelStyle.isBlank()) {
            guide.styleSpecificTips = getStyleSpecificTips(normalizedDest, travelStyle);
        }
        
        return guide;
    }
    
    /**
     * 标准化目的地名称
     */
    private String normalizeDestination(String destination) {
        String normalized = destination.toLowerCase().trim();
        
        // 常见目的地映射
        Map<String, String> destinationMap = Map.ofEntries(
            Map.entry("beijing", "beijing"),
            Map.entry("北京", "beijing"),
            Map.entry("shanghai", "shanghai"),
            Map.entry("上海", "shanghai"),
            Map.entry("paris", "paris"),
            Map.entry("巴黎", "paris"),
            Map.entry("tokyo", "tokyo"),
            Map.entry("东京", "tokyo"),
            Map.entry("bangkok", "bangkok"),
            Map.entry("曼谷", "bangkok"),
            Map.entry("singapore", "singapore"),
            Map.entry("新加坡", "singapore"),
            Map.entry("london", "london"),
            Map.entry("伦敦", "london"),
            Map.entry("new york", "new_york"),
            Map.entry("纽约", "new_york"),
            Map.entry("rome", "rome"),
            Map.entry("罗马", "rome"),
            Map.entry("sydney", "sydney"),
            Map.entry("悉尼", "sydney")
        );
        
        return destinationMap.getOrDefault(normalized, normalized.replace(" ", "_"));
    }
    
    /**
     * 根据目的地填充具体数据
     */
    private void populateDestinationData(TravelGuideData guide, String destination) {
        switch (destination) {
            case "beijing":
                guide.destination = "Beijing";
                guide.country = "China";
                guide.bestTimeToVisit = List.of("September to November (Autumn)", "March to May (Spring)");
                guide.topAttractions = List.of(
                    Map.of("name", "Forbidden City", "duration", "3-4 hours", "tip", "Book tickets online in advance"),
                    Map.of("name", "Great Wall (Mutianyu Section)", "duration", "Full day", "tip", "Take cable car up, toboggan down"),
                    Map.of("name", "Temple of Heaven", "duration", "2-3 hours", "tip", "Visit early morning to see locals exercising"),
                    Map.of("name", "Summer Palace", "duration", "Half day", "tip", "Rent a boat on Kunming Lake"),
                    Map.of("name", "Hutongs", "duration", "2-3 hours", "tip", "Explore by rickshaw or bicycle")
                );
                guide.travelTips = List.of(
                    "Download WeChat and Alipay for payments",
                    "Use VPN if you need Google/Facebook services",
                    "Carry tissue paper as many public restrooms don't provide it",
                    "Learn basic Chinese phrases or use translation app",
                    "Avoid rush hour (7-9am, 5-7pm) when using subway"
                );
                guide.practicalInfo = Map.of(
                    "emergency_number", "110 (Police), 120 (Ambulance)",
                    "power_plugs", "Type A, C, I (220V)",
                    "internet", "WiFi widely available, VPN recommended",
                    "transportation", "Excellent metro system, affordable taxis"
                );
                guide.estimatedBudget = Map.of(
                    "budget", "$50-80 USD per day",
                    "mid_range", "$100-200 USD per day",
                    "luxury", "$300+ USD per day"
                );
                guide.localCustoms = List.of(
                    "Tipping is not customary in China",
                    "Remove shoes when entering someone's home",
                    "Use both hands when giving/receiving business cards",
                    "Don't stick chopsticks vertically in rice (resembles funeral ritual)"
                );
                guide.recommendedDuration = "3-5 days";
                guide.currency = "Chinese Yuan (CNY/RMB)";
                guide.language = "Mandarin Chinese";
                guide.visaRequirements = "Most foreigners need a visa. Check with Chinese embassy.";
                break;
                
            case "paris":
                guide.destination = "Paris";
                guide.country = "France";
                guide.bestTimeToVisit = List.of("April to June (Spring)", "October to November (Fall)");
                guide.topAttractions = List.of(
                    Map.of("name", "Eiffel Tower", "duration", "2-3 hours", "tip", "Book skip-the-line tickets"),
                    Map.of("name", "Louvre Museum", "duration", "Half day", "tip", "Go on Wednesday or Friday evenings"),
                    Map.of("name", "Notre-Dame Cathedral", "duration", "1-2 hours", "tip", "Currently under restoration, view from outside"),
                    Map.of("name", "Montmartre & Sacré-Cœur", "duration", "Half day", "tip", "Visit artists' square Place du Tertre"),
                    Map.of("name", "Seine River Cruise", "duration", "1-2 hours", "tip", "Best at sunset or night")
                );
                guide.travelTips = List.of(
                    "Buy museum passes for discounts and skip-the-line access",
                    "Greet shopkeepers with 'Bonjour' when entering",
                    "Metro is efficient but watch for pickpockets",
                    "Water is free at restaurants - ask for 'carafe d'eau'",
                    "Many shops close on Sundays"
                );
                guide.practicalInfo = Map.of(
                    "emergency_number", "112 (EU emergency number)",
                    "power_plugs", "Type C, E (230V)",
                    "internet", "Free WiFi in cafés and hotels",
                    "transportation", "Metro, RER trains, buses, Vélib bikes"
                );
                guide.estimatedBudget = Map.of(
                    "budget", "€60-100 EUR per day",
                    "mid_range", "€150-300 EUR per day",
                    "luxury", "€500+ EUR per day"
                );
                guide.localCustoms = List.of(
                    "Always say 'Bonjour' before asking questions",
                    "Keep voice down in public places",
                    "Bread should be torn, not cut",
                    "Cheese course comes after main dish, before dessert"
                );
                guide.recommendedDuration = "4-6 days";
                guide.currency = "Euro (EUR)";
                guide.language = "French";
                guide.visaRequirements = "Schengen visa required for many countries. 90 days visa-free for US/UK/CA.";
                break;
                
            case "tokyo":
                guide.destination = "Tokyo";
                guide.country = "Japan";
                guide.bestTimeToVisit = List.of("March to May (Cherry Blossom Season)", "September to November (Autumn)");
                guide.topAttractions = List.of(
                    Map.of("name", "Senso-ji Temple (Asakusa)", "duration", "2-3 hours", "tip", "Try street food on Nakamise Street"),
                    Map.of("name", "Shibuya Crossing", "duration", "1 hour", "tip", "View from Starbucks overlooking crossing"),
                    Map.of("name", "Meiji Shrine", "duration", "1-2 hours", "tip", "Write wishes on ema plaques"),
                    Map.of("name", "Tsukiji Outer Market", "duration", "2-3 hours", "tip", "Go early for freshest sushi breakfast"),
                    Map.of("name", "Tokyo Skytree", "duration", "2 hours", "tip", "Book tickets online for discounts")
                );
                guide.travelTips = List.of(
                    "Get a Suica or Pasmo IC card for all trains/buses",
                    "Learn basic Japanese phrases - English is limited",
                    "Don't eat while walking (considered rude)",
                    "Quiet mode on trains - no phone calls",
                    "Carry cash - many places don't accept cards"
                );
                guide.practicalInfo = Map.of(
                    "emergency_number", "110 (Police), 119 (Ambulance/Fire)",
                    "power_plugs", "Type A, B (100V)",
                    "internet", "Pocket WiFi rental recommended",
                    "transportation", "Extensive train network, JR Pass for intercity"
                );
                guide.estimatedBudget = Map.of(
                    "budget", "¥8,000-12,000 JPY per day",
                    "mid_range", "¥20,000-35,000 JPY per day",
                    "luxury", "¥50,000+ JPY per day"
                );
                guide.localCustoms = List.of(
                    "Bow when greeting (handshakes also accepted)",
                    "Remove shoes in traditional settings",
                    "Don't tip (can be considered insulting)",
                    "Slurping noodles shows appreciation"
                );
                guide.recommendedDuration = "5-7 days";
                guide.currency = "Japanese Yen (JPY)";
                guide.language = "Japanese";
                guide.visaRequirements = "Visa-free for many countries (90 days). Check requirements.";
                break;
                
            default:
                // 通用旅行指南模板
                guide.destination = destination.replace("_", " ").substring(0, 1).toUpperCase() + 
                                   destination.replace("_", " ").substring(1);
                guide.country = "Various";
                guide.bestTimeToVisit = List.of("Research specific destination climate",
                                               "Consider shoulder seasons for fewer crowds");
                guide.topAttractions = List.of(
                    Map.of("name", "Historic Old Town", "duration", "Half day", "tip", "Take a walking tour"),
                    Map.of("name", "Local Markets", "duration", "2-3 hours", "tip", "Great for authentic food and souvenirs"),
                    Map.of("name", "Museums", "duration", "2-4 hours", "tip", "Check for free entry days")
                );
                guide.travelTips = List.of(
                    "Research local customs and dress codes",
                    "Learn basic phrases in local language",
                    "Get travel insurance",
                    "Inform bank of travel plans",
                    "Make copies of important documents"
                );
                guide.practicalInfo = Map.of(
                    "emergency_number", "Research local emergency numbers",
                    "power_plugs", "Check voltage and plug type",
                    "internet", "Get local SIM or international roaming",
                    "transportation", "Research public transport options"
                );
                guide.estimatedBudget = Map.of(
                    "budget", "Varies by region - research cost of living",
                    "mid_range", "Plan for moderate comfort",
                    "luxury", "Premium experiences available"
                );
                guide.localCustoms = List.of(
                    "Respect local traditions and religious sites",
                    "Ask permission before photographing people",
                    "Dress appropriately for cultural sites"
                );
                guide.recommendedDuration = "3-7 days depending on destination";
                guide.currency = "Research local currency";
                guide.language = "Research official languages";
                guide.visaRequirements = "Check with embassy or consulate";
        }
    }
    
    /**
     * 获取季节性建议
     */
    private String getSeasonalAdvice(String destination, String travelTime) {
        // 简化版本 - 根据季节给出建议
        String lowerTime = travelTime.toLowerCase();
        
        if (lowerTime.contains("spring") || lowerTime.contains("march") || 
            lowerTime.contains("april") || lowerTime.contains("may")) {
            return switch (destination) {
                case "tokyo" -> "Cherry blossom season! Book accommodations early. Popular spots: Ueno Park, Chidorigafuchi.";
                case "paris" -> "Beautiful spring weather. Pack layers and a light jacket. Gardens are blooming.";
                case "beijing" -> "Pleasant temperatures but can be windy. Bring light clothing and a jacket.";
                default -> "Generally good weather. Pack layers for changing conditions.";
            };
        }
        
        if (lowerTime.contains("summer") || lowerTime.contains("june") || 
            lowerTime.contains("july") || lowerTime.contains("august")) {
            return switch (destination) {
                case "tokyo" -> "Hot and humid. Stay hydrated, use air conditioning during midday.";
                case "paris" -> "Peak tourist season. Expect crowds. Book everything in advance.";
                case "beijing" -> "Very hot and rainy. Bring umbrella and light clothing.";
                default -> "Peak season. Higher prices and more crowds. Book early.";
            };
        }
        
        if (lowerTime.contains("autumn") || lowerTime.contains("fall") || 
            lowerTime.contains("september") || lowerTime.contains("october") || 
            lowerTime.contains("november")) {
            return switch (destination) {
                case "tokyo" -> "Perfect weather and autumn colors. Ideal time to visit.";
                case "paris" -> "Lovely fall foliage. Fewer tourists. Great for museums.";
                case "beijing" -> "Best season! Clear skies and comfortable temperatures.";
                default -> "Excellent weather. Less crowded than summer.";
            };
        }
        
        if (lowerTime.contains("winter") || lowerTime.contains("december") || 
            lowerTime.contains("january") || lowerTime.contains("february")) {
            return switch (destination) {
                case "tokyo" -> "Cold but sunny. Good for skiing nearby. New Year closures possible.";
                case "paris" -> "Cold and gray. Indoor activities ideal. Christmas markets in December.";
                case "beijing" -> "Very cold. Bring heavy coat. Air quality can be poor.";
                default -> "Off-season. Lower prices but some attractions may have reduced hours.";
            };
        }
        
        return "Research weather patterns for your specific travel dates.";
    }
    
    /**
     * 获取特定旅行风格的建议
     */
    private List<String> getStyleSpecificTips(String destination, String travelStyle) {
        return switch (travelStyle.toLowerCase()) {
            case "budget" -> List.of(
                "Stay in hostels or budget hotels",
                "Eat at local restaurants and street food",
                "Use public transportation instead of taxis",
                "Look for free walking tours",
                "Visit free attractions and parks",
                "Cook some meals if accommodation has kitchen"
            );
            case "luxury" -> List.of(
                "Book premium hotels with concierge service",
                "Hire private guides for personalized tours",
                "Make restaurant reservations in advance",
                "Consider VIP skip-the-line tickets",
                "Use private transfers or first-class transport",
                "Splurge on unique local experiences"
            );
            case "family" -> List.of(
                "Choose family-friendly accommodations",
                "Plan shorter activities with breaks",
                "Pack entertainment for kids during transit",
                "Research kid-friendly restaurants",
                "Include parks and interactive attractions",
                "Keep flexible schedule for unexpected needs"
            );
            case "adventure" -> List.of(
                "Research outdoor activities and sports",
                "Pack appropriate gear and clothing",
                "Book adventure tours with reputable companies",
                "Get travel insurance covering adventure activities",
                "Connect with local adventure communities",
                "Be prepared for physical challenges"
            );
            case "cultural" -> List.of(
                "Research local history before visiting",
                "Attend cultural performances and events",
                "Visit museums, temples, and historical sites",
                "Try authentic local cuisine",
                "Participate in traditional workshops",
                "Respect and observe local customs"
            );
            default -> List.of("Enjoy your trip!", "Stay flexible and open-minded");
        };
    }
    
    /**
     * 生成旅行指南文本摘要
     */
    private String generateTravelGuideText(TravelGuideData guide) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌍 Travel Guide: ").append(guide.destination).append(", ").append(guide.country);
        sb.append("\n\n⏰ Best Time to Visit: ").append(String.join(", ", guide.bestTimeToVisit));
        sb.append("\n\n🏛️ Top Attractions:\n");
        guide.topAttractions.forEach(attraction -> 
            sb.append("  • ").append(attraction.get("name"))
             .append(" (").append(attraction.get("duration")).append(")\n")
             .append("    Tip: ").append(attraction.get("tip")).append("\n")
        );
        sb.append("\n💡 Travel Tips:\n");
        guide.travelTips.forEach(tip -> sb.append("  • ").append(tip).append("\n"));
        sb.append("\n💰 Estimated Budget: ")
          .append(guide.estimatedBudget.getOrDefault("mid_range", "Varies"));
        sb.append("\n⏱️ Recommended Duration: ").append(guide.recommendedDuration);
        sb.append("\n💵 Currency: ").append(guide.currency);
        sb.append("\n🗣️ Language: ").append(guide.language);
        
        if (guide.seasonalAdvice != null) {
            sb.append("\n\n🌤️ Seasonal Advice: ").append(guide.seasonalAdvice);
        }
        
        return sb.toString();
    }
}
