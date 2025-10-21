package com.EcoChartPro.plugins.community;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.Parameter;
import com.EcoChartPro.api.indicator.ParameterType;
import com.EcoChartPro.api.indicator.drawing.DrawableCandle;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.core.indicator.IndicatorContext;
import com.EcoChartPro.api.indicator.ApiKLine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Overlays candles from a higher timeframe (HTF) on top of the current chart.
 * This helps visualize larger market structure while viewing smaller timeframe price action.
 */
public class HTFOverlayIndicator implements CustomIndicator {

    @Override
    public String getName() {
        return "HTF Overlay";
    }

    @Override
    public IndicatorType getType() {
        return IndicatorType.OVERLAY; // Renders on the main price chart
    }

    @Override
    public List<Parameter> getParameters() {
        // Dropdown of common higher timeframes
        String[] timeframeChoices = {"M15", "M30", "H1", "H4", "D1"};
        return List.of(
            new Parameter("Timeframe", ParameterType.CHOICE, "H1", timeframeChoices),
            new Parameter("Bull Color", ParameterType.COLOR, new Color(0, 150, 136, 80)),
            new Parameter("Bear Color", ParameterType.COLOR, new Color(244, 67, 54, 80)),
            new Parameter("Wick Color", ParameterType.COLOR, new Color(150, 150, 150, 120))
        );
    }

    @Override
    public List<DrawableObject> calculate(IndicatorContext context) {
        Map<String, Object> settings = context.settings();
        String htf = (String) settings.get("Timeframe");
        Color bullColor = (Color) settings.get("Bull Color");
        Color bearColor = (Color) settings.get("Bear Color");
        Color wickColor = (Color) settings.get("Wick Color");

        // Request the resampled data for the chosen higher timeframe
        List<ApiKLine> htfData = context.resampledKlineData(htf);

        if (htfData == null || htfData.isEmpty()) {
            return new ArrayList<>(); // No data available for the requested timeframe
        }

        List<DrawableObject> drawables = new ArrayList<>();
        for (ApiKLine candle : htfData) {
            // Determine color based on open vs close
            Color bodyColor = candle.close().compareTo(candle.open()) >= 0 ? bullColor : bearColor;
            
            // Create a DrawableCandle object for the API to render
            drawables.add(new DrawableCandle(
                candle.timestamp(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                bodyColor,
                wickColor
            ));
        }
        
        return drawables;
    }
}
