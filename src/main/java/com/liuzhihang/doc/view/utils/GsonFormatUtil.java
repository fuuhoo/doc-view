package com.liuzhihang.doc.view.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.liuzhihang.doc.view.config.JacksonAnnotationExclusionStrategy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;

/**
 * 对 Gson 进行修改主要是修改 newJsonWriter中的缩进
 *
 * @author liuzhihang
 * @date 2019/5/9 11:07
 */
public class GsonFormatUtil {

    @NotNull
    public static String gsonFormat(Gson gson, JsonElement jsonElement) throws IOException {
        StringWriter writer = new StringWriter();
        JsonWriter jsonWriter = newJsonWriter(Streams.writerForAppendable(Streams.writerForAppendable(writer)));
        gson.toJson(jsonElement, jsonWriter);
        return writer.toString();
    }

    public static String gsonFormat(Object src) {

//        Gson gson = new GsonBuilder().serializeNulls().create();

        Gson gson = new GsonBuilder()
                .setExclusionStrategies(new JacksonAnnotationExclusionStrategy())
                .create();

        return gsonFormat(gson, src);
    }

    @NotNull
    public static String gsonFormat(Gson gson, Object src) {
        if (src == null) {
            return gson.toJson(JsonNull.INSTANCE);
        }
        try {
            StringWriter writer = new StringWriter();
            JsonWriter jsonWriter = newJsonWriter(Streams.writerForAppendable(Streams.writerForAppendable(writer)));
            gson.toJson(src, src.getClass(), jsonWriter);
            return writer.toString();
        } catch (IOException e) {
            if (src instanceof Collection) {
                return "[]";
            }
            return "{}";
        }
    }

    /**
     * 重新JsonWriter的缩进
     * 具体请查看相关源码
     *
     * @param writer
     * @return
     * @throws IOException
     * @see Gson#newJsonWriter(Writer)
     */
    private static JsonWriter newJsonWriter(Writer writer) throws IOException {
        JsonWriter jsonWriter = new JsonWriter(writer);
        // 修改此处缩进为四个空格
        jsonWriter.setIndent("    ");
        jsonWriter.setSerializeNulls(false);
        return jsonWriter;
    }


}
