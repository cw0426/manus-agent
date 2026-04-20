package com.yupi.yuaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片搜索工具
 */
public class ImageSearchTool {

    // SearchAPI 的图片搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public ImageSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for images from Baidu Image Search Engine, returns image URLs")
    public String searchImages(
            @ToolParam(description = "Search query keyword for images, e.g., '景点图片', '美食图片'") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu_images");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);

            // 提取图片结果
            List<Map<String, String>> images = new ArrayList<>();

            // 尝试从 images 数组中提取
            JSONArray imagesArray = jsonObject.getJSONArray("images");
            if (imagesArray != null && !imagesArray.isEmpty()) {
                int limit = Math.min(5, imagesArray.size());
                for (int i = 0; i < limit; i++) {
                    JSONObject imgObj = imagesArray.getJSONObject(i);
                    Map<String, String> imgInfo = new HashMap<>();

                    // 提取原图URL
                    String originalUrl = imgObj.getStr("original");
                    if (originalUrl == null) {
                        originalUrl = imgObj.getStr("link");
                    }
                    if (originalUrl == null) {
                        originalUrl = imgObj.getStr("thumbnail");
                    }

                    if (originalUrl != null && originalUrl.startsWith("http")) {
                        imgInfo.put("imageUrl", originalUrl);
                        imgInfo.put("title", imgObj.getStr("title", query));
                        imgInfo.put("source", imgObj.getStr("source", ""));
                        images.add(imgInfo);
                    }
                }
            }

            // 如果没有找到图片，返回提示信息
            if (images.isEmpty()) {
                return "{\"message\": \"未找到相关图片\", \"query\": \"" + query + "\"}";
            }

            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("count", images.size());
            result.put("images", images);

            return JSONUtil.toJsonStr(result);
        } catch (Exception e) {
            return "{\"error\": \"搜索图片失败: " + e.getMessage() + "\", \"query\": \"" + query + "\"}";
        }
    }
}
