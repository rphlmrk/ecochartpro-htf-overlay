package com.EcoChartPro.plugins.community;

import com.EcoChartPro.api.indicator.ApiKLine;
import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.Parameter;
import com.EcoChartPro.api.indicator.ParameterType;
import com.EcoChartPro.api.indicator.drawing.DataPoint;
import com.EcoChartPro.api.indicator.drawing.DrawableBox;
import com.EcoChartPro.api.indicator.drawing.DrawableCandle;
import com.EcoChartPro.api.indicator.drawing.DrawableLine;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.api.indicator.drawing.DrawableText;
import com.EcoChartPro.api.indicator.drawing.TextAnchor;
import com.EcoChartPro.core.indicator.IndicatorContext;
import com.EcoChartPro.model.Timeframe;

import java.awt.Color;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An indicator that overlays candlestick data from a higher timeframe (HTF) onto the current chart.
 * Provides extensive customization for colors, outlines, and an optional real-time countdown timer.
 * Version: 2.1 (API Compatibility Fix)
 */
public class HTFOverlayIndicator implements CustomIndicator {

    @Override
    public String getName() {
        return "HTF Overlay";
    }

    @Override
    public IndicatorType getType() {
        return IndicatorType.OVERLAY;
    }

    @Override
    public List<Parameter> getParameters() {
        return List.of(
            new Parameter("Enable HTF Overlay", ParameterType.BOOLEAN, true),
            new Parameter("Higher Timeframe", ParameterType.CHOICE, "4H", "1m", "5m", "15m", "30m", "1H", "4H", "1D"),
            new Parameter("Candles to Show", ParameterType.INTEGER, 20, "Set to 0 to show all available HTF candles"),
            new Parameter("Hide When Chart Is Above", ParameterType.CHOICE, "1D", "1m", "5m", "15m", "30m", "1H", "4H", "1D"),
            new Parameter("Display Style", ParameterType.CHOICE, "Box", "Box", "Candle"),
            new Parameter("Body Shading (Box Style)", ParameterType.BOOLEAN, false),
            new Parameter("Show Open/Close Lines", ParameterType.BOOLEAN, false),
            new Parameter("Wicks On/Off", ParameterType.BOOLEAN, true),
            new Parameter("Outline Width (Box Style)", ParameterType.INTEGER, 2),
            new Parameter("Wick Line Width", ParameterType.INTEGER, 1),
            new Parameter("Open/Close Line Width", ParameterType.INTEGER, 1),
            new Parameter("Bull Candle Outline", ParameterType.COLOR, pineColor(38, 166, 153, 58)),
            new Parameter("Bear Candle Outline", ParameterType.COLOR, new Color(125, 129, 126, 100)),
            new Parameter("Bull Candle Fill", ParameterType.COLOR, new Color(38, 166, 153, 90)),
            new Parameter("Bear Candle Fill", ParameterType.COLOR, new Color(239, 83, 80, 90)),
            new Parameter("Bull Candle Wick", ParameterType.COLOR, pineColor(38, 166, 153, 58)),
            new Parameter("Bear Candle Wick", ParameterType.COLOR, new Color(239, 83, 80, 100)),
            new Parameter("Open Line Color", ParameterType.COLOR, Color.GRAY),
            new Parameter("Close Line Color", ParameterType.COLOR, Color.GRAY),
            new Parameter("Show Countdown Timer", ParameterType.BOOLEAN, true),
            new Parameter("Timer Position", ParameterType.CHOICE, "Bottom Center", "Top Left", "Top Center", "Top Right", "Bottom Left", "Bottom Center", "Bottom Right"),
            new Parameter("Timer Text Size", ParameterType.CHOICE, "Small", "Tiny", "Small", "Medium", "Large"),
            new Parameter("Timer Text Color", ParameterType.COLOR, pineColor(21, 255, 0, 56))
        );
    }

    @Override
    public List<DrawableObject> calculate(IndicatorContext context) {
        Map<String, Object> settings = context.settings();

        if (!(boolean) settings.getOrDefault("Enable HTF Overlay", true)) {
            return Collections.emptyList();
        }

        Timeframe htf = Timeframe.fromString((String) settings.getOrDefault("Higher Timeframe", "4H"));
        Timeframe hideAboveHTF = Timeframe.fromString((String) settings.getOrDefault("Hide When Chart Is Above", "1D"));
        if (htf == null || hideAboveHTF == null) return Collections.emptyList();

        int numCandles = (int) settings.getOrDefault("Candles to Show", 20);
        List<ApiKLine> klineData = context.klineData();

        if (klineData.size() > 1) {
            Duration currentTfDuration = Duration.between(klineData.get(0).timestamp(), klineData.get(1).timestamp());
            if (currentTfDuration.compareTo(hideAboveHTF.duration()) > 0 || currentTfDuration.compareTo(htf.duration()) >= 0) {
                return Collections.emptyList();
            }
        }

        // [FIX] Use the new, correct method to get HTF data
        List<ApiKLine> htfData = context.resampledKlineData(htf.displayName());
        if (htfData == null || htfData.isEmpty()) return Collections.emptyList();

        List<DrawableObject> drawables = new ArrayList<>();

        String displayStyle = (String) settings.getOrDefault("Display Style", "Box");
        boolean bodyShadingOnOff = (boolean) settings.getOrDefault("Body Shading (Box Style)", false);
        boolean showOpenCloseLines = (boolean) settings.getOrDefault("Show Open/Close Lines", false);
        boolean wickOnOff = (boolean) settings.getOrDefault("Wicks On/Off", true);
        float outlineWidth = ((Integer) settings.getOrDefault("Outline Width (Box Style)", 2)).floatValue();
        float wickLineWidth = ((Integer) settings.getOrDefault("Wick Line Width", 1)).floatValue();
        float openCloseLineWidth = ((Integer) settings.getOrDefault("Open/Close Line Width", 1)).floatValue();
        
        Color bullOutline = (Color) settings.get("Bull Candle Outline");
        Color bearOutline = (Color) settings.get("Bear Candle Outline");
        Color bullFill = (Color) settings.get("Bull Candle Fill");
        Color bearFill = (Color) settings.get("Bear Candle Fill");
        Color bullWick = (Color) settings.get("Bull Candle Wick");
        Color bearWick = (Color) settings.get("Bear Candle Wick");
        Color openLineColor = (Color) settings.get("Open Line Color");
        Color closeLineColor = (Color) settings.get("Close Line Color");

        int maxCandles = (numCandles == 0) ? 5000 : numCandles;
        int startIndex = Math.max(0, htfData.size() - maxCandles);
        Duration htfDuration = htf.duration();

        for (int i = startIndex; i < htfData.size(); i++) {
            ApiKLine candle = htfData.get(i);
            BigDecimal o = candle.open();
            BigDecimal h = candle.high();
            BigDecimal l = candle.low();
            BigDecimal c = candle.close();
            Instant openTime = candle.timestamp();
            Instant closeTime = openTime.plus(htfDuration);
            Instant midTime = openTime.plus(htfDuration.dividedBy(2));

            boolean isBullish = c.compareTo(o) >= 0;
            BigDecimal bodyTop = isBullish ? c : o;
            BigDecimal bodyBottom = isBullish ? o : c;
            
            Color wickColor = isBullish ? bullWick : bearWick;

            if ("Candle".equals(displayStyle)) {
                Color bodyColor = isBullish ? bullFill : bearFill; 
                // [FIX] Use the new DrawableCandle class
                drawables.add(new DrawableCandle(openTime, o, h, l, c, bodyColor, wickColor));
            
            } else {
                Color finalFillColor = bodyShadingOnOff ? (isBullish ? bullFill : bearFill) : null;
                Color finalBorderColor = isBullish ? bullOutline : bearOutline;
                
                drawables.add(new DrawableBox(new DataPoint(openTime, bodyTop), new DataPoint(closeTime, bodyBottom), finalFillColor, finalBorderColor, outlineWidth));

                if (wickOnOff) {
                    if (h.compareTo(bodyTop) > 0) {
                        drawables.add(new DrawableLine(new DataPoint(midTime, h), new DataPoint(midTime, bodyTop), wickColor, wickLineWidth));
                    }
                    if (l.compareTo(bodyBottom) < 0) {
                        drawables.add(new DrawableLine(new DataPoint(midTime, bodyBottom), new DataPoint(midTime, l), wickColor, wickLineWidth));
                    }
                }
            }

            if (showOpenCloseLines) {
                drawables.add(new DrawableLine(new DataPoint(openTime, o), new DataPoint(midTime, o), openLineColor, openCloseLineWidth));
                drawables.add(new DrawableLine(new DataPoint(midTime, c), new DataPoint(closeTime, c), closeLineColor, openCloseLineWidth));
            }
        }

        if ((boolean) settings.getOrDefault("Show Countdown Timer", true) && !htfData.isEmpty()) {
            ApiKLine lastCandle = htfData.get(htfData.size() - 1);
            Instant closeTime = lastCandle.timestamp().plus(htfDuration);
            long timeLeftMs = closeTime.toEpochMilli() - Instant.now().toEpochMilli();
            if (timeLeftMs > 0) {
                drawCountdownTimer(drawables, settings, lastCandle, timeLeftMs, htfDuration);
            }
        }
        
        return drawables;
    }
    
    private void drawCountdownTimer(List<DrawableObject> drawables, Map<String, Object> settings, ApiKLine lastCandle, long timeLeftMs, Duration htfDuration) {
        String position = (String) settings.get("Timer Position");
        String sizeStr = (String) settings.get("Timer Text Size");
        Color textColor = (Color) settings.get("Timer Text Color");
        
        long hours = TimeUnit.MILLISECONDS.toHours(timeLeftMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftMs) % 60;
        String m_str = String.format("%02d", minutes);
        String s_str = String.format("%02d", seconds);
        String countdownText = (hours > 0) ? String.format("%d:%s:%s", hours, m_str, s_str) : String.format("%s:%s", m_str, s_str);
        
        Instant openTime = lastCandle.timestamp();
        Instant closeTime = openTime.plus(htfDuration);
        BigDecimal bodyTop = lastCandle.open().max(lastCandle.close());
        BigDecimal bodyBottom = lastCandle.open().min(lastCandle.close());

        Instant positionXTime;
        if (position.contains("Left")) positionXTime = openTime;
        else if (position.contains("Right")) positionXTime = closeTime;
        else positionXTime = openTime.plus(htfDuration.dividedBy(2));

        BigDecimal positionYPrice = position.contains("Top") ? bodyTop : bodyBottom;

        DataPoint anchorPoint = new DataPoint(positionXTime, positionYPrice);
        TextAnchor textAnchor = getTextAnchor(position);
        Font font = getCountdownFont(sizeStr);
        
        drawables.add(new DrawableText(anchorPoint, countdownText, font, textColor, textAnchor));
    }
    
    private TextAnchor getTextAnchor(String position) {
        return switch (position) {
            case "Top Left" -> TextAnchor.BOTTOM_LEFT;
            case "Top Center" -> TextAnchor.BOTTOM_CENTER;
            case "Top Right" -> TextAnchor.BOTTOM_RIGHT;
            case "Bottom Left" -> TextAnchor.TOP_LEFT;
            case "Bottom Right" -> TextAnchor.TOP_RIGHT;
            default -> TextAnchor.TOP_CENTER;
        };
    }

    private Font getCountdownFont(String sizeStr) {
        int size = switch (sizeStr) {
            case "Tiny" -> 10;
            case "Small" -> 12;
            case "Medium" -> 14;
            case "Large" -> 16;
            default -> 12;
        };
        return new Font("SansSerif", Font.BOLD, size);
    }
    
    private Color pineColor(int r, int g, int b, int transparency) {
        int alpha = (int) (255 * (100 - transparency) / 100.0);
        return new Color(r, g, b, Math.max(0, Math.min(255, alpha)));
    }
}
